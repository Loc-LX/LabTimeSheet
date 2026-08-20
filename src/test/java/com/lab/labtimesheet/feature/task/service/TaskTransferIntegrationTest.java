package com.lab.labtimesheet.feature.task.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
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
class TaskTransferIntegrationTest {

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TaskService taskService;

    @Autowired
    private TaskTransferService taskTransfers;

    @Autowired
    private TaskQueryService taskQueries;

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
    void transferMovesOnlyUnfinishedTasksAndLeavesDoneAndCreatorAttributionUntouched() {
        TaskView open = taskService.create(
                "member@example.test",
                new CreateTaskCommand(projectId, memberMembershipId, "Open", null, null));
        TaskView blocked = taskService.create(
                "member@example.test",
                new CreateTaskCommand(projectId, memberMembershipId, "Blocked", null, null));
        taskService.changeStatus("member@example.test", projectId, blocked.id(), TaskStatus.BLOCKED);
        TaskView done = taskService.create(
                "member@example.test",
                new CreateTaskCommand(projectId, memberMembershipId, "Done", null, null));
        taskService.changeStatus("member@example.test", projectId, done.id(), TaskStatus.IN_PROGRESS);
        taskService.changeStatus("member@example.test", projectId, done.id(), TaskStatus.DONE);
        addComment(open.id(), "member@example.test", "Keep me");
        logWork(open.id(), memberMembershipId, LocalDate.of(2026, 8, 14), 90);

        int transferred = taskTransfers.transferUnfinishedTasks(
                projectId, memberMembershipId, leaderMembershipId);

        assertThat(transferred).isEqualTo(2);
        assertThat(assigneeOf(open.id())).isEqualTo(leaderMembershipId);
        assertThat(assigneeOf(blocked.id())).isEqualTo(leaderMembershipId);
        assertThat(assigneeOf(done.id())).isEqualTo(memberMembershipId);
        assertThat(creatorOf(open.id())).isEqualTo(memberMembershipId);
        assertThat(creatorOf(done.id())).isEqualTo(memberMembershipId);
        assertThat(assignerOf(open.id())).isEqualTo(leaderMembershipId);
        assertThat(commentCount(open.id())).isEqualTo(1);
        assertThat(minutesFor(open.id())).isEqualTo(90);
    }

    @Test
    void completionGateCountsExcludeSoftDeletedAndTransferReturnsZeroForNone() {
        TaskView open = taskService.create(
                "member@example.test",
                new CreateTaskCommand(projectId, memberMembershipId, "Open", null, null));
        taskService.softDelete("leader@example.test", projectId, open.id());

        assertThat(taskQueries.countCurrentTasks(projectId)).isZero();
        assertThat(taskQueries.countDoneTasks(projectId)).isZero();

        int transferred = taskTransfers.transferUnfinishedTasks(
                projectId, memberMembershipId, leaderMembershipId);
        assertThat(transferred).isZero();
    }

    @Test
    void transferToSameMembershipSkipsEveryTask() {
        taskService.create(
                "member@example.test",
                new CreateTaskCommand(projectId, memberMembershipId, "Open", null, null));

        int transferred = taskTransfers.transferUnfinishedTasks(
                projectId, memberMembershipId, memberMembershipId);

        assertThat(transferred).isEqualTo(1);
        assertThat(assigneeOfAll(memberMembershipId)).isEqualTo(1);
    }

    private long assigneeOf(long taskId) {
        return jdbc.sql("select assignee_membership_id from tasks where id = :id")
                .param("id", taskId)
                .query(Long.class)
                .single();
    }

    private long assigneeOfAll(long membershipId) {
        return jdbc.sql("""
                        select count(*) from tasks
                        where project_id = :projectId and assignee_membership_id = :membershipId
                        """)
                .param("projectId", projectId)
                .param("membershipId", membershipId)
                .query(Long.class)
                .single();
    }

    private long creatorOf(long taskId) {
        return jdbc.sql("select created_by_membership_id from tasks where id = :id")
                .param("id", taskId)
                .query(Long.class)
                .single();
    }

    private long assignerOf(long taskId) {
        return jdbc.sql("select assigned_by_membership_id from tasks where id = :id")
                .param("id", taskId)
                .query(Long.class)
                .single();
    }

    private long commentCount(long taskId) {
        return jdbc.sql("select count(*) from task_comments where task_id = :id")
                .param("id", taskId)
                .query(Long.class)
                .single();
    }

    private long minutesFor(long taskId) {
        return jdbc.sql("select coalesce(sum(minutes), 0) from task_work_logs where task_id = :id")
                .param("id", taskId)
                .query(Long.class)
                .single();
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
