package com.lab.labtimesheet.feature.task.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.task.exception.TaskNotFoundException;
import com.lab.labtimesheet.feature.task.exception.TaskValidationException;
import com.lab.labtimesheet.feature.task.model.TaskStatus;
import com.lab.labtimesheet.feature.task.model.dto.CreateTaskCommand;
import com.lab.labtimesheet.feature.task.model.dto.TaskView;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TaskReassignmentIntegrationTest {

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TaskService taskService;

    private long projectId;
    private long leaderMembershipId;
    private long memberMembershipId;
    private long secondMemberMembershipId;

    @BeforeEach
    void setUpProject() {
        long mentorId = insertUser("mentor@example.test", "MENTOR");
        long leaderId = insertIntern("leader@example.test");
        long memberId = insertIntern("member@example.test");
        long secondMemberId = insertIntern("second@example.test");
        projectId = insertProject(mentorId, "PLANNED");
        leaderMembershipId = insertMembership(projectId, leaderId, mentorId);
        memberMembershipId = insertMembership(projectId, memberId, mentorId);
        secondMemberMembershipId = insertMembership(projectId, secondMemberId, mentorId);
        jdbc.sql("""
                        insert into project_leadership_terms
                            (project_id, membership_id, appointed_by_mentor_user_id)
                        values (:projectId, :membershipId, :mentorId)
                        """)
                .param("projectId", projectId)
                .param("membershipId", leaderMembershipId)
                .param("mentorId", mentorId)
                .update();
    }

    @Test
    void doneTaskRejectsReassignmentUntilAssigneeReopensThenPreservesStateAndLogs() {
        long taskId = createMemberTask("Draft results").id();
        activateProject();
        setStatus(taskId, TaskStatus.IN_PROGRESS);
        addComment(taskId, "member@example.test", "First draft done");
        logWork(taskId, memberMembershipId, LocalDate.of(2026, 8, 14), 90);
        setStatus(taskId, TaskStatus.DONE);

        assertThat(taskService.details("leader@example.test", projectId, taskId).canReassign())
                .isFalse();
        assertThatThrownBy(() -> taskService.reassign(
                        "leader@example.test", projectId, taskId, secondMemberMembershipId))
                .isInstanceOf(TaskValidationException.class)
                .hasMessage("A DONE Task must be reopened before reassignment");
        assertThat(assigneeOf(taskId)).isEqualTo(memberMembershipId);

        taskService.changeStatus("member@example.test", projectId, taskId, TaskStatus.IN_PROGRESS);
        TaskView reassigned = taskService.reassign(
                "leader@example.test", projectId, taskId, secondMemberMembershipId);

        assertThat(reassigned.status()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(reassigned.assigneeMembershipId()).isEqualTo(secondMemberMembershipId);
        assertThat(reassigned.assignerMembershipId()).isEqualTo(leaderMembershipId);
        assertThat(reassigned.creatorMembershipId()).isEqualTo(memberMembershipId);
        assertThat(commentCountFor(taskId)).isEqualTo(1);
        assertThat(workLogCountFor(taskId)).isEqualTo(1);
        assertThat(workLogMembershipOf(taskId)).isEqualTo(memberMembershipId);
    }

    @Test
    void nonLeaderCannotReassignAndReassignmentAwayRevokesCreatorControlUntilReturn() {
        TaskView created = createMemberTask("Draft results");
        long taskId = created.id();
        activateProject();
        setStatus(taskId, TaskStatus.IN_PROGRESS);

        assertThatThrownBy(() -> taskService.reassign(
                        "member@example.test", projectId, taskId, secondMemberMembershipId))
                .isInstanceOf(TaskNotFoundException.class);
        assertThat(assigneeOf(taskId)).isEqualTo(memberMembershipId);

        TaskView away = taskService.reassign(
                "leader@example.test", projectId, taskId, secondMemberMembershipId);
        assertThat(away.assigneeMembershipId()).isEqualTo(secondMemberMembershipId);
        assertThat(away.creatorMembershipId()).isEqualTo(memberMembershipId);

        assertThatThrownBy(() -> taskService.changeStatus(
                        "member@example.test", projectId, taskId, TaskStatus.DONE))
                .isInstanceOf(TaskNotFoundException.class);
        assertThat(statusOf(taskId)).isEqualTo(TaskStatus.IN_PROGRESS.name());

        TaskView back = taskService.reassign(
                "leader@example.test", projectId, taskId, memberMembershipId);
        assertThat(back.assigneeMembershipId()).isEqualTo(memberMembershipId);
        assertThat(back.creatorMembershipId()).isEqualTo(memberMembershipId);

        taskService.changeStatus("member@example.test", projectId, taskId, TaskStatus.DONE);
        assertThat(statusOf(taskId)).isEqualTo(TaskStatus.DONE.name());
    }

    private TaskView createMemberTask(String title) {
        return taskService.create(
                "member@example.test",
                new CreateTaskCommand(projectId, memberMembershipId, title, null, null));
    }

    private void setStatus(long taskId, TaskStatus status) {
        jdbc.sql("update tasks set status = :status where id = :id")
                .param("status", status.name())
                .param("id", taskId)
                .update();
        entityManager.clear();
    }

    private void activateProject() {
        jdbc.sql("update projects set status = 'ACTIVE', activated_at = current_timestamp where id = :id")
                .param("id", projectId)
                .update();
        entityManager.clear();
    }

    private void addComment(long taskId, String email, String body) {
        jdbc.sql("""
                        insert into task_comments (task_id, author_user_id, body)
                        select :taskId, id, :body from app_users where email = :email
                        """)
                .param("taskId", taskId)
                .param("body", body)
                .param("email", email)
                .update();
        entityManager.clear();
    }

    private void logWork(long taskId, long membershipId, LocalDate date, int minutes) {
        jdbc.sql("""
                        insert into task_work_logs (project_id, task_id, membership_id, work_date, minutes)
                        values (:projectId, :taskId, :membershipId, :date, :minutes)
                        """)
                .param("projectId", projectId)
                .param("taskId", taskId)
                .param("membershipId", membershipId)
                .param("date", date)
                .param("minutes", minutes)
                .update();
        entityManager.clear();
    }

    private long assigneeOf(long taskId) {
        return jdbc.sql("select assignee_membership_id from tasks where id = :id")
                .param("id", taskId)
                .query(Long.class)
                .single();
    }

    private long workLogMembershipOf(long taskId) {
        return jdbc.sql("select membership_id from task_work_logs where task_id = :id")
                .param("id", taskId)
                .query(Long.class)
                .single();
    }

    private String statusOf(long taskId) {
        return jdbc.sql("select status from tasks where id = :id")
                .param("id", taskId)
                .query(String.class)
                .single();
    }

    private long commentCountFor(long taskId) {
        return jdbc.sql("select count(*) from task_comments where task_id = :id")
                .param("id", taskId)
                .query(Long.class)
                .single();
    }

    private long workLogCountFor(long taskId) {
        return jdbc.sql("select count(*) from task_work_logs where task_id = :id")
                .param("id", taskId)
                .query(Long.class)
                .single();
    }

    private long insertUser(String email, String role) {
        return jdbc.sql("""
                        insert into app_users
                            (email, display_name, password_hash, global_role, account_status, activated_at)
                        values (:email, :email, 'hash', :role, 'ACTIVE', current_timestamp)
                        returning id
                        """)
                .param("email", email)
                .param("role", role)
                .query(Long.class)
                .single();
    }

    private long insertIntern(String email) {
        long userId = insertUser(email, "INTERN");
        jdbc.sql("""
                        insert into intern_profiles
                            (user_id, student_code, internship_start_date, internship_end_date,
                             internship_status, activated_at)
                        values (:userId, :studentCode, date '2026-01-01', date '2026-12-31',
                                'ACTIVE', current_timestamp)
                        """)
                .param("userId", userId)
                .param("studentCode", "S" + userId)
                .update();
        return userId;
    }

    private long insertProject(long mentorId, String status) {
        return jdbc.sql("""
                        insert into projects
                            (mentor_user_id, name, status, start_date, end_date, activated_at)
                        values (:mentorId, 'Project', :status, :startDate, :endDate,
                                case when :status = 'ACTIVE' then current_timestamp else null end)
                        returning id
                        """)
                .param("mentorId", mentorId)
                .param("status", status)
                .param("startDate", LocalDate.of(2026, 8, 1))
                .param("endDate", LocalDate.of(2026, 8, 31))
                .query(Long.class)
                .single();
    }

    private long insertMembership(long projectId, long userId, long mentorId) {
        return jdbc.sql("""
                        insert into project_memberships (project_id, intern_user_id, added_by_user_id)
                        values (:projectId, :internId, :mentorId)
                        returning id
                        """)
                .param("projectId", projectId)
                .param("internId", userId)
                .param("mentorId", mentorId)
                .query(Long.class)
                .single();
    }
}
