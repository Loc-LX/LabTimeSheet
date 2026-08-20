package com.lab.labtimesheet.feature.task.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.task.exception.TaskNotFoundException;
import com.lab.labtimesheet.feature.task.exception.TaskValidationException;
import com.lab.labtimesheet.feature.task.model.TaskStatus;
import com.lab.labtimesheet.feature.task.model.dto.CreateTaskCommand;
import com.lab.labtimesheet.feature.task.model.dto.EditTaskCommand;
import com.lab.labtimesheet.feature.task.model.dto.TaskDetails;
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
class TaskDefinitionIntegrationTest {

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
        projectId = insertProject(mentorId, "ACTIVE");
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
    void leaderEditsUnfinishedTaskAndSelfCreatorEditsOwnWhileAssigned() {
        TaskView task = taskService.create(
                "member@example.test",
                new CreateTaskCommand(projectId, memberMembershipId, "Draft", "Notes", LocalDate.of(2026, 8, 20)));

        TaskView edited = taskService.edit(
                "member@example.test",
                new EditTaskCommand(projectId, task.id(), "  Final draft  ", "  New notes  ", LocalDate.of(2026, 8, 21)));

        assertThat(edited.title()).isEqualTo("Final draft");
        assertThat(edited.description()).isEqualTo("New notes");
        assertThat(edited.dueDate()).isEqualTo(LocalDate.of(2026, 8, 21));
        assertThat(edited.creatorMembershipId()).isEqualTo(memberMembershipId);
        assertThat(edited.assignerMembershipId()).isEqualTo(memberMembershipId);
        assertThat(edited.assigneeMembershipId()).isEqualTo(memberMembershipId);
        assertThat(edited.status()).isEqualTo(TaskStatus.TODO);
    }

    @Test
    void leaderEditsTaskCreatedForAnotherMember() {
        TaskView task = taskService.create(
                "leader@example.test",
                new CreateTaskCommand(projectId, memberMembershipId, "Draft", null, null));

        TaskView edited = taskService.edit(
                "leader@example.test",
                new EditTaskCommand(projectId, task.id(), "Leader retitle", null, LocalDate.of(2026, 8, 22)));

        assertThat(edited.title()).isEqualTo("Leader retitle");
        assertThat(edited.creatorMembershipId()).isEqualTo(leaderMembershipId);
        assertThat(edited.assigneeMembershipId()).isEqualTo(memberMembershipId);
    }

    @Test
    void selfCreatorLosesDefinitionControlAfterReassignmentAway() {
        TaskView created = taskService.create(
                "member@example.test",
                new CreateTaskCommand(projectId, memberMembershipId, "Draft", null, null));
        taskService.reassign("leader@example.test", projectId, created.id(), secondMemberMembershipId);

        assertThatThrownBy(() -> taskService.edit(
                        "member@example.test",
                        new EditTaskCommand(projectId, created.id(), "Retitle", null, null)))
                .isInstanceOf(TaskNotFoundException.class);
        assertThatThrownBy(() -> taskService.softDelete("member@example.test", projectId, created.id()))
                .isInstanceOf(TaskNotFoundException.class);
        assertThat(titleOf(created.id())).isEqualTo("Draft");
    }

    @Test
    void doneTaskRejectsEditAndDeleteUntilReopened() {
        TaskView task = taskService.create(
                "member@example.test",
                new CreateTaskCommand(projectId, memberMembershipId, "Draft", null, null));
        setStatus(task.id(), TaskStatus.DONE);

        assertThatThrownBy(() -> taskService.edit(
                        "member@example.test",
                        new EditTaskCommand(projectId, task.id(), "Retitle", null, null)))
                .isInstanceOf(TaskNotFoundException.class);
        assertThatThrownBy(() -> taskService.softDelete("leader@example.test", projectId, task.id()))
                .isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    void leaderSoftDeleteLeavesNormalProgressButRemainsHistoricallyQueryable() {
        TaskView task = taskService.create(
                "member@example.test",
                new CreateTaskCommand(projectId, memberMembershipId, "Draft", null, null));
        addComment(task.id(), "member@example.test", "First comment");
        logWork(task.id(), memberMembershipId, LocalDate.of(2026, 8, 14), 90);

        TaskView deleted = taskService.softDelete("leader@example.test", projectId, task.id());

        assertThat(deleted.deletedAt()).isNotNull();
        assertThat(taskService.list("leader@example.test", projectId).tasks())
                .extracting(TaskView::id)
                .doesNotContain(task.id());

        TaskDetails historical = taskService.historicalDetails("leader@example.test", projectId, task.id());
        assertThat(historical.deleted()).isTrue();
        assertThat(historical.task().title()).isEqualTo("Draft");
        assertThat(historical.comments()).hasSize(1);
        assertThat(historical.workLogs()).hasSize(1);
        assertThat(historical.canEdit()).isFalse();
        assertThat(historical.canChangeStatus()).isFalse();

        assertThatThrownBy(() -> taskService.historicalDetails("second@example.test", projectId, task.id()))
                .isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    void selfCreatorMayInspectOwnSoftDeletedTaskHistorically() {
        TaskView task = taskService.create(
                "member@example.test",
                new CreateTaskCommand(projectId, memberMembershipId, "Draft", null, null));
        taskService.softDelete("leader@example.test", projectId, task.id());

        TaskDetails historical = taskService.historicalDetails("member@example.test", projectId, task.id());

        assertThat(historical.deleted()).isTrue();
        assertThat(historical.task().creatorMembershipId()).isEqualTo(memberMembershipId);
    }

    @Test
    void editRejectsDueDateOutsideProjectOrOnGlobalDayOff() {
        TaskView task = taskService.create(
                "member@example.test",
                new CreateTaskCommand(projectId, memberMembershipId, "Draft", null, null));

        assertThatThrownBy(() -> taskService.edit(
                        "member@example.test",
                        new EditTaskCommand(projectId, task.id(), "Retitle", null, LocalDate.of(2026, 9, 15))))
                .isInstanceOf(TaskValidationException.class)
                .hasMessage("Due date must be within Project dates");

        insertDayOff(LocalDate.of(2026, 8, 20));
        assertThatThrownBy(() -> taskService.edit(
                        "member@example.test",
                        new EditTaskCommand(projectId, task.id(), "Retitle", null, LocalDate.of(2026, 8, 20))))
                .isInstanceOf(TaskValidationException.class)
                .hasMessage("Due date cannot be a current global day off");
    }

    private void setStatus(long taskId, TaskStatus status) {
        jdbc.sql("update tasks set status = :status where id = :id")
                .param("status", status.name())
                .param("id", taskId)
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

    private String titleOf(long taskId) {
        return jdbc.sql("select title from tasks where id = :id")
                .param("id", taskId)
                .query(String.class)
                .single();
    }

    private void insertDayOff(LocalDate date) {
        long mentorId = userId("mentor@example.test");
        jdbc.sql("""
                        insert into global_calendar_events
                            (calendar_date, name, source, is_day_off, created_by_user_id, updated_by_user_id)
                        values (:date, 'Day off', 'CUSTOM', true, :userId, :userId)
                        """)
                .param("date", date)
                .param("userId", mentorId)
                .update();
    }

    private long userId(String email) {
        return jdbc.sql("select id from app_users where email = :email")
                .param("email", email)
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
