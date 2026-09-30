package com.lab.labtimesheet.feature.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.project.exception.TaskNotFoundException;
import com.lab.labtimesheet.feature.project.exception.TaskValidationException;
import com.lab.labtimesheet.feature.project.model.TaskStatus;
import com.lab.labtimesheet.feature.project.model.dto.CreateTaskCommand;
import com.lab.labtimesheet.feature.project.model.dto.TaskStatusChangeCommand;
import com.lab.labtimesheet.feature.project.model.dto.TaskView;
import jakarta.persistence.EntityManager;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verifies the grant half of TSK-023 via authorization policy AUTH-012.
 * Covers Leader and Mentor task blocking, unblocking, and reopening capabilities,
 * as well as refusal boundaries for unauthorized actors and operations.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TaskStatusGrantIntegrationTest {

    private static final LocalDate PROJECT_START = LocalDate.of(2026, 8, 1);
    private static final LocalDate PROJECT_END = LocalDate.of(2026, 8, 31);

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private Clock clock;

    @Autowired
    private TaskService taskService;

    private long projectId;
    private long leaderMembershipId;
    private long memberMembershipId;
    private long otherMembershipId;

    @BeforeEach
    void setUp() {
        long adminId = insertUser("bootstrap@example.test", "ADMIN");
        jdbc.sql("""
                update system_state
                set initialized = true, initialized_at = current_timestamp, bootstrap_admin_id = :adminId
                where singleton_id = 1
                """)
                .param("adminId", adminId)
                .update();

        long mentorId = insertUser("mentor@example.test", "MENTOR");
        long leaderId = insertIntern("leader@example.test");
        long memberId = insertIntern("member@example.test");
        long otherId = insertIntern("other@example.test");

        projectId = insertProject(mentorId, "PLANNED");
        leaderMembershipId = insertMembership(projectId, leaderId, mentorId);
        memberMembershipId = insertMembership(projectId, memberId, mentorId);
        otherMembershipId = insertMembership(projectId, otherId, mentorId);

        jdbc.sql("update project_memberships set joined_at = :joinedAt where id in (:leader, :member, :other)")
                .param("joinedAt", Timestamp.from(Instant.parse("2026-08-14T00:00:00Z")))
                .param("leader", leaderMembershipId)
                .param("member", memberMembershipId)
                .param("other", otherMembershipId)
                .update();

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

    /**
     * TSK-007, TSK-023, TSK-025, AC-TSK-016, AC-TSK-018, NOT-003:
     * When the project is ACTIVE, the current Leader is permitted by policy BLOCK_UNBLOCK_REOPEN_TASK
     * to block another member's TODO task, unblock it back to TODO, and reopen a DONE task with a
     * non-blank reason. Each action appends a task_status_transitions ledger record and sends a
     * notification to the assignee under NOT-003 while excluding the acting Leader.
     * Observable break before fix: Leader is denied with TaskNotFoundException because forOwningMentorTask
     * only attached OWNING_MENTOR; expected transitions: 3, expected notifications: 3 to assignee.
     */
    @Test
    void leaderBlocksUnblocksAndReopensAnotherMembersTaskWithTransitionsAndAssigneeNotification() {
        TaskView task = createMemberTask("Feature development");
        activateProject();

        long notificationsBeforeBlock = notificationCount();

        // 1. Leader blocks member's TODO task
        TaskView blocked = taskService.changeStatus("leader@example.test", projectId, task.id(), TaskStatus.BLOCKED);
        assertThat(blocked.status()).isEqualTo(TaskStatus.BLOCKED);
        assertThat(transitionCount(task.id())).isEqualTo(1);
        assertThat(lastTransition(task.id())).isEqualTo(new TransitionRow("TODO", "BLOCKED", userId("leader@example.test"), null));

        // NOT-003: notification sent to assignee (member), not to leader
        assertThat(notificationCount()).isEqualTo(notificationsBeforeBlock + 1);
        assertThat(lastNotificationRecipientId()).isEqualTo(userId("member@example.test"));

        // 2. Leader unblocks member's task back to TODO
        TaskView unblocked = taskService.changeStatus("leader@example.test", projectId, task.id(), TaskStatus.TODO);
        assertThat(unblocked.status()).isEqualTo(TaskStatus.TODO);
        assertThat(transitionCount(task.id())).isEqualTo(2);
        assertThat(lastTransition(task.id())).isEqualTo(new TransitionRow("BLOCKED", "TODO", userId("leader@example.test"), null));
        assertThat(lastNotificationRecipientId()).isEqualTo(userId("member@example.test"));

        // Assignee moves task to IN_PROGRESS then to DONE
        taskService.changeStatus("member@example.test", projectId, task.id(), TaskStatus.IN_PROGRESS);
        taskService.changeStatus("member@example.test", projectId, task.id(), TaskStatus.DONE);
        assertThat(snapshot(task.id()).status()).isEqualTo("DONE");

        long notificationsBeforeReopen = notificationCount();

        // 3. Leader reopens DONE task to IN_PROGRESS with reason
        TaskView reopened = taskService.changeStatus("leader@example.test", projectId, task.id(), null,
                new TaskStatusChangeCommand(TaskStatus.IN_PROGRESS, "Require additional integration verification"));
        assertThat(reopened.status()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(transitionCount(task.id())).isEqualTo(3);
        assertThat(lastTransition(task.id())).isEqualTo(new TransitionRow("DONE", "IN_PROGRESS",
                userId("leader@example.test"), "Require additional integration verification"));

        // NOT-003: notification sent to assignee (member), not to leader
        assertThat(notificationCount()).isEqualTo(notificationsBeforeReopen + 1);
        assertThat(lastNotificationRecipientId()).isEqualTo(userId("member@example.test"));
    }

    /**
     * TSK-007, TSK-023, AC-TSK-003, AC-AUTH-004:
     * Current Leader cannot start another member's task (TODO -> IN_PROGRESS) or mark it DONE
     * (IN_PROGRESS -> DONE). Both attempts must be refused with TaskNotFoundException, leaving
     * the task status and ledger unchanged.
     * Observable break: if granted, ledger would contain start/done rows or wrong status;
     * hand-derived expected status: unchanged, expected transition count: 0.
     */
    @Test
    void leaderCannotStartAnotherMembersTaskOrMarkItDone() {
        TaskView task = createMemberTask("Restricted workflow task");
        activateProject();

        // Leader tries to start member's TODO task
        TaskSnapshot beforeStart = snapshot(task.id());
        assertThatThrownBy(() -> taskService.changeStatus("leader@example.test", projectId, task.id(), TaskStatus.IN_PROGRESS))
                .isInstanceOf(TaskNotFoundException.class);
        assertThat(snapshot(task.id())).isEqualTo(beforeStart);
        assertThat(transitionCount(task.id())).isZero();

        // Assignee moves task to IN_PROGRESS (normal in-progress transition is not recorded in statusTransitions)
        taskService.changeStatus("member@example.test", projectId, task.id(), TaskStatus.IN_PROGRESS);
        assertThat(snapshot(task.id()).status()).isEqualTo("IN_PROGRESS");
        assertThat(transitionCount(task.id())).isZero();

        // Leader tries to mark member's IN_PROGRESS task as DONE
        TaskSnapshot beforeDone = snapshot(task.id());
        assertThatThrownBy(() -> taskService.changeStatus("leader@example.test", projectId, task.id(), TaskStatus.DONE))
                .isInstanceOf(TaskNotFoundException.class);
        assertThat(snapshot(task.id())).isEqualTo(beforeDone);
        assertThat(transitionCount(task.id())).isZero();
    }

    /**
     * TSK-025, AC-TSK-020, D46:
     * Leader attempting to unblock to a status different from the latest block origin
     * must be rejected with TaskValidationException, leaving the task BLOCKED and history unchanged.
     * Observable break before fix: throws TaskNotFoundException instead of TaskValidationException
     * because Leader was completely unauthorized.
     */
    @Test
    void leaderCannotUnblockToWrongStatus() {
        TaskView task = createMemberTask("Unblock validation task");
        activateProject();

        // Leader blocks TODO task
        taskService.changeStatus("leader@example.test", projectId, task.id(), TaskStatus.BLOCKED);
        assertThat(snapshot(task.id()).status()).isEqualTo("BLOCKED");
        assertThat(transitionCount(task.id())).isEqualTo(1);

        // Leader tries to unblock to IN_PROGRESS instead of original TODO
        TaskSnapshot beforeWrong = snapshot(task.id());
        long notificationsBeforeWrong = notificationCount();

        assertThatThrownBy(() -> taskService.changeStatus("leader@example.test", projectId, task.id(), TaskStatus.IN_PROGRESS))
                .isInstanceOf(TaskValidationException.class)
                .hasMessage("Task must return to the status recorded by its latest block.");

        assertThat(snapshot(task.id())).isEqualTo(beforeWrong);
        assertThat(transitionCount(task.id())).isEqualTo(1);
        assertThat(notificationCount()).isEqualTo(notificationsBeforeWrong);
    }

    /**
     * TSK-023, AUTH-012, AC-AUTH-004:
     * Refuses status change when the project is not ACTIVE (e.g. PLANNED), when the leader's term
     * has ended, or when the actor is a regular member attempting to block another member's task.
     * All attempts must throw TaskNotFoundException without recording transitions.
     */
    @Test
    void refusesBlockWhenProjectNotActiveOrLeaderTermEndedOrRegularMember() {
        TaskView task = createMemberTask("Boundary task");

        // 1. Project is PLANNED (not ACTIVE)
        assertThatThrownBy(() -> taskService.changeStatus("leader@example.test", projectId, task.id(), TaskStatus.BLOCKED))
                .isInstanceOf(TaskNotFoundException.class);
        assertThat(snapshot(task.id()).status()).isEqualTo("TODO");
        assertThat(transitionCount(task.id())).isZero();

        activateProject();

        // 2. Regular member (other@example.test) attempts to block member's task
        assertThatThrownBy(() -> taskService.changeStatus("other@example.test", projectId, task.id(), TaskStatus.BLOCKED))
                .isInstanceOf(TaskNotFoundException.class);
        assertThat(snapshot(task.id()).status()).isEqualTo("TODO");
        assertThat(transitionCount(task.id())).isZero();

        // 3. Former leader whose term has ended attempts to block member's task
        jdbc.sql("""
                update project_leadership_terms
                set ended_at = started_at + interval '1 second', ended_by_mentor_user_id = :mentorId
                where project_id = :projectId and membership_id = :leaderId
                """)
                .param("mentorId", userId("mentor@example.test"))
                .param("projectId", projectId)
                .param("leaderId", leaderMembershipId)
                .update();
        entityManager.clear();

        assertThatThrownBy(() -> taskService.changeStatus("leader@example.test", projectId, task.id(), TaskStatus.BLOCKED))
                .isInstanceOf(TaskNotFoundException.class);
        assertThat(snapshot(task.id()).status()).isEqualTo("TODO");
        assertThat(transitionCount(task.id())).isZero();
    }

    /**
     * AUTH-012, AC-AUTH-005, TSK-023:
     * Admin has no task status change capability under authorization matrix. Any attempt by Admin
     * to block, unblock, reopen, or change a task must be refused with TaskNotFoundException
     * without altering task rows or ledger.
     */
    @Test
    void refusesAdminChangingAnyTaskStatus() {
        TaskView task = createMemberTask("Admin refusal task");
        activateProject();

        TaskSnapshot before = snapshot(task.id());
        long notificationsBefore = notificationCount();

        assertThatThrownBy(() -> taskService.changeStatus("bootstrap@example.test", projectId, task.id(), TaskStatus.BLOCKED))
                .isInstanceOf(TaskNotFoundException.class);

        assertThatThrownBy(() -> taskService.changeStatus("bootstrap@example.test", projectId, task.id(), TaskStatus.IN_PROGRESS))
                .isInstanceOf(TaskNotFoundException.class);

        assertThat(snapshot(task.id())).isEqualTo(before);
        assertThat(transitionCount(task.id())).isZero();
        assertThat(notificationCount()).isEqualTo(notificationsBefore);
    }

    /**
     * TSK-007, AC-TSK-021:
     * Soft-deleted tasks cannot have their status changed by any actor, including the Current Leader.
     * Attempt must throw TaskNotFoundException with no ledger or notification side effects.
     */
    @Test
    void refusesChangingStatusOfSoftDeletedTask() {
        TaskView task = createMemberTask("Soft-deleted task");
        activateProject();

        // Soft delete the task
        jdbc.sql("""
                update tasks
                set deleted_at = current_timestamp, deleted_by_membership_id = :membershipId
                where id = :id
                """)
                .param("membershipId", memberMembershipId)
                .param("id", task.id())
                .update();
        entityManager.clear();

        TaskSnapshot before = snapshot(task.id());
        long notificationsBefore = notificationCount();

        assertThatThrownBy(() -> taskService.changeStatus("leader@example.test", projectId, task.id(), TaskStatus.BLOCKED))
                .isInstanceOf(TaskNotFoundException.class);

        assertThat(snapshot(task.id())).isEqualTo(before);
        assertThat(transitionCount(task.id())).isZero();
        assertThat(notificationCount()).isEqualTo(notificationsBefore);
    }

    /**
     * TSK-023, NOT-003, AC-TSK-018:
     * Owning mentor can still block, unblock, and reopen tasks. Under NOT-003, when the owning mentor
     * blocks another member's task, notifications are dispatched to BOTH the assignee and the current leader.
     */
    @Test
    void owningMentorCanStillBlockTaskAndNotifiesBothAssigneeAndLeader() {
        TaskView task = createMemberTask("Mentor block task");
        activateProject();

        long notificationsBefore = notificationCount();

        TaskView blocked = taskService.changeStatus("mentor@example.test", projectId, task.id(), TaskStatus.BLOCKED);
        assertThat(blocked.status()).isEqualTo(TaskStatus.BLOCKED);
        assertThat(transitionCount(task.id())).isEqualTo(1);
        assertThat(lastTransition(task.id())).isEqualTo(new TransitionRow("TODO", "BLOCKED", userId("mentor@example.test"), null));

        // NOT-003: Owning mentor block dispatches to both assignee and current leader
        assertThat(notificationCount()).isEqualTo(notificationsBefore + 2);
        List<Long> recipients = recentNotificationRecipientIds(2);
        assertThat(recipients).containsExactlyInAnyOrder(
                userId("member@example.test"),
                userId("leader@example.test"));
    }

    // Helper methods

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
                .param("startDate", PROJECT_START)
                .param("endDate", PROJECT_END)
                .query(Long.class)
                .single();
    }

    private long insertMembership(long targetProjectId, long internId, long mentorId) {
        return jdbc.sql("""
                insert into project_memberships (project_id, intern_user_id, added_by_user_id)
                values (:projectId, :internId, :mentorId)
                returning id
                """)
                .param("projectId", targetProjectId)
                .param("internId", internId)
                .param("mentorId", mentorId)
                .query(Long.class)
                .single();
    }

    private long userId(String email) {
        return jdbc.sql("select id from app_users where email = :email")
                .param("email", email)
                .query(Long.class)
                .single();
    }

    private void activateProject() {
        jdbc.sql("update projects set status = 'ACTIVE', activated_at = current_timestamp where id = :id")
                .param("id", projectId)
                .update();
        entityManager.clear();
    }

    private TaskView createMemberTask(String title) {
        return taskService.create(
                "member@example.test",
                new CreateTaskCommand(projectId, memberMembershipId, title, null, null));
    }

    private long transitionCount(long taskId) {
        return jdbc.sql("select count(*) from task_status_transitions where task_id = :taskId")
                .param("taskId", taskId)
                .query(Long.class)
                .single();
    }

    private TransitionRow lastTransition(long taskId) {
        return jdbc.sql("""
                select from_status, to_status, actor_user_id, reason
                from task_status_transitions
                where task_id = :taskId
                order by id desc
                limit 1
                """)
                .param("taskId", taskId)
                .query((result, row) -> new TransitionRow(
                        result.getString("from_status"),
                        result.getString("to_status"),
                        result.getLong("actor_user_id"),
                        result.getString("reason")))
                .single();
    }

    private long notificationCount() {
        return jdbc.sql("select count(*) from notifications").query(Long.class).single();
    }

    private long lastNotificationRecipientId() {
        return jdbc.sql("select recipient_user_id from notifications order by id desc limit 1")
                .query(Long.class)
                .single();
    }

    private List<Long> recentNotificationRecipientIds(int limit) {
        return jdbc.sql("select recipient_user_id from notifications order by id desc limit :limit")
                .param("limit", limit)
                .query(Long.class)
                .list();
    }

    private TaskSnapshot snapshot(long taskId) {
        return jdbc.sql("""
                select status, title, description, assignee_membership_id, version, deleted_at
                from tasks where id = :id
                """)
                .param("id", taskId)
                .query((result, row) -> new TaskSnapshot(
                        result.getString("status"),
                        result.getString("title"),
                        result.getString("description"),
                        result.getLong("assignee_membership_id"),
                        result.getLong("version"),
                        result.getTimestamp("deleted_at") == null ? null : result.getTimestamp("deleted_at").toInstant()))
                .single();
    }

    private record TransitionRow(String fromStatus, String toStatus, long actorUserId, String reason) {}

    private record TaskSnapshot(String status, String title, String description, long assigneeMembershipId, long version, Instant deletedAt) {}
}
