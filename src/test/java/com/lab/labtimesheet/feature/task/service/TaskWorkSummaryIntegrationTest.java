package com.lab.labtimesheet.feature.task.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.task.model.TaskStatus;
import com.lab.labtimesheet.feature.task.model.dto.CreateTaskCommand;
import com.lab.labtimesheet.feature.task.model.dto.TaskMemberHours;
import com.lab.labtimesheet.feature.task.model.dto.TaskProjectWorkSummary;
import com.lab.labtimesheet.feature.task.model.dto.TaskView;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.Comparator;
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
class TaskWorkSummaryIntegrationTest {

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TaskService taskService;

    @Autowired
    private TaskQueryService taskQueries;

    private long projectId;
    private long leaderMembershipId;
    private long memberMembershipId;
    private long otherMemberMembershipId;
    private long mentorId;
    private long leaderUserId;
    private long memberUserId;
    private long otherMemberUserId;
    private long bystanderUserId;

    @BeforeEach
    void setUpProject() {
        mentorId = insertUser("mentor@example.test", "MENTOR");
        leaderUserId = insertIntern("leader@example.test");
        memberUserId = insertIntern("member@example.test");
        otherMemberUserId = insertIntern("other@example.test");
        bystanderUserId = insertIntern("bystander@example.test");
        projectId = insertProject(mentorId, "ACTIVE");
        leaderMembershipId = insertMembership(projectId, leaderUserId, mentorId);
        memberMembershipId = insertMembership(projectId, memberUserId, mentorId);
        otherMemberMembershipId = insertMembership(projectId, otherMemberUserId, mentorId);
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
    void aggregateTotalsAndPerMemberBreakdownAreHandCheckable() {
        TaskView a = taskService.create(
                "member@example.test",
                new CreateTaskCommand(projectId, memberMembershipId, "A", null, null));
        TaskView b = taskService.create(
                "member@example.test",
                new CreateTaskCommand(projectId, memberMembershipId, "B", null, null));
        TaskView c = taskService.create(
                "member@example.test",
                new CreateTaskCommand(projectId, memberMembershipId, "C", null, null));
        taskService.changeStatus("member@example.test", projectId, a.id(), TaskStatus.IN_PROGRESS);
        taskService.changeStatus("member@example.test", projectId, c.id(), TaskStatus.IN_PROGRESS);
        taskService.changeStatus("member@example.test", projectId, c.id(), TaskStatus.DONE);
        logWork(a.id(), memberMembershipId, LocalDate.of(2026, 8, 14), 60);
        logWork(a.id(), memberMembershipId, LocalDate.of(2026, 8, 15), 30);
        logWork(b.id(), otherMemberMembershipId, LocalDate.of(2026, 8, 16), 45);

        TaskProjectWorkSummary summary = taskQueries.projectWorkSummary("leader@example.test", projectId);

        assertThat(summary.progress().todo()).isEqualTo(1);
        assertThat(summary.progress().inProgress()).isEqualTo(1);
        assertThat(summary.progress().blocked()).isZero();
        assertThat(summary.progress().done()).isEqualTo(1);
        assertThat(summary.progress().total()).isEqualTo(3);
        assertThat(summary.completionPercentage()).hasValue(100.0 / 3);
        assertThat(summary.totalMinutes()).isEqualTo(135);
        assertThat(summary.perMemberVisible()).isTrue();
        assertThat(summary.perMemberHours()).containsExactlyInAnyOrder(
                new TaskMemberHours(memberMembershipId, 90L),
                new TaskMemberHours(otherMemberMembershipId, 45L));
    }

    @Test
    void ordinaryMemberSeesAggregateOnlyWhileMinutesRetainSoftDeletedHistory() {
        TaskView a = taskService.create(
                "member@example.test",
                new CreateTaskCommand(projectId, memberMembershipId, "A", null, null));
        TaskView b = taskService.create(
                "member@example.test",
                new CreateTaskCommand(projectId, memberMembershipId, "B", null, null));
        taskService.changeStatus("member@example.test", projectId, b.id(), TaskStatus.IN_PROGRESS);
        logWork(a.id(), memberMembershipId, LocalDate.of(2026, 8, 14), 60);
        logWork(b.id(), memberMembershipId, LocalDate.of(2026, 8, 15), 30);
        taskService.softDelete("member@example.test", projectId, b.id());

        TaskProjectWorkSummary summary = taskQueries.projectWorkSummary("member@example.test", projectId);

        assertThat(summary.progress().todo()).isEqualTo(1);
        assertThat(summary.progress().inProgress()).isZero();
        assertThat(summary.progress().done()).isZero();
        assertThat(summary.completionPercentage()).hasValue(0.0);
        assertThat(summary.totalMinutes()).isEqualTo(90);
        assertThat(summary.perMemberVisible()).isFalse();
        assertThat(summary.perMemberHours()).isEmpty();
    }

    @Test
    void emptyProjectProducesNACompletenessAndZeroTotals() {
        TaskProjectWorkSummary summary = taskQueries.projectWorkSummary("mentor@example.test", projectId);

        assertThat(summary.completionPercentage()).isEmpty();
        assertThat(summary.totalMinutes()).isZero();
        assertThat(summary.progress().total()).isZero();
        assertThat(summary.perMemberVisible()).isTrue();
        assertThat(summary.perMemberHours()).isEmpty();
    }

    @Test
    void bystanderInternCannotViewTheProjectAtAll() {
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> taskQueries.projectWorkSummary("bystander@example.test", projectId))
                .isInstanceOf(com.lab.labtimesheet.feature.project.exception.ProjectAccessDeniedException.class);
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
