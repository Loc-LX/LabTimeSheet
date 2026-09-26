package com.lab.labtimesheet.feature.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.calendar.service.CalendarApplicationService;
import com.lab.labtimesheet.feature.project.model.dto.ProjectTaskContext;
import com.lab.labtimesheet.feature.project.service.ProjectService;
import com.lab.labtimesheet.feature.reporting.service.DailyProjectWorkReportService;
import com.lab.labtimesheet.feature.project.exception.TaskConflictException;
import com.lab.labtimesheet.feature.project.exception.TaskNotFoundException;
import com.lab.labtimesheet.feature.project.exception.TaskValidationException;
import com.lab.labtimesheet.feature.project.model.TaskStatus;
import com.lab.labtimesheet.feature.project.model.TaskVarianceState;
import com.lab.labtimesheet.feature.project.model.dto.CreateTaskCommand;
import com.lab.labtimesheet.feature.project.model.dto.RemainingEffortForecastInput;
import com.lab.labtimesheet.feature.project.model.dto.TaskAssigneeChoice;
import com.lab.labtimesheet.feature.project.model.dto.TaskCommentView;
import com.lab.labtimesheet.feature.project.model.dto.TaskDailyReportView;
import com.lab.labtimesheet.feature.project.model.dto.TaskDetails;
import com.lab.labtimesheet.feature.project.model.dto.TaskDueDateImpactView;
import com.lab.labtimesheet.feature.project.model.dto.TaskEffortPlanningView;
import com.lab.labtimesheet.feature.project.model.dto.TaskHistoryView;
import com.lab.labtimesheet.feature.project.model.dto.TaskListView;
import com.lab.labtimesheet.feature.project.model.dto.TaskProjectProgress;
import com.lab.labtimesheet.feature.project.model.dto.TaskRemainingEffortForecastView;
import com.lab.labtimesheet.feature.project.model.dto.TaskStatusChangeCommand;
import com.lab.labtimesheet.feature.project.model.dto.TaskView;
import com.lab.labtimesheet.feature.project.model.entity.TaskRemainingEffortForecast;
import com.lab.labtimesheet.feature.project.model.entity.TaskWorkLog;
import com.lab.labtimesheet.feature.project.repository.TaskRemainingEffortForecastRepository;
import com.lab.labtimesheet.feature.project.repository.TaskWorkLogRepository;
import jakarta.persistence.EntityManager;
import org.postgresql.util.PSQLException;
import java.time.Instant;
import java.time.Clock;
import java.time.LocalDate;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.assertj.core.api.SoftAssertions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;

@Import(TestcontainersConfiguration.class)
@AutoConfigureMockMvc
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TaskCreationIntegrationTest {

    private static final LocalDate PROJECT_START = LocalDate.of(2026, 8, 1);
    private static final LocalDate PROJECT_END = LocalDate.of(2026, 8, 31);

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private Clock clock;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TaskService taskService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TaskQueryService taskQueries;

    @Autowired
    private DailyProjectWorkReportService dailyReports;

    @Autowired
    private TaskDashboardService taskDashboard;

    @Autowired
    private TaskWorkLogRepository taskWorkLogs;

    @Autowired
    private TaskRemainingEffortForecastRepository forecasts;

    @Autowired
    private ProjectService projectMutations;

    @Autowired
    private ProjectQueryService projectQueries;

    @Autowired
    private CalendarApplicationService calendar;

    @Autowired
    private TaskTransferService taskTransfers;

    private long projectId;
    private long leaderMembershipId;
    private long memberMembershipId;

    @BeforeEach
    void setUpProject() {
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
        projectId = insertProject(mentorId, "PLANNED");
        leaderMembershipId = insertMembership(projectId, leaderId, mentorId);
        memberMembershipId = insertMembership(projectId, memberId, mentorId);
        jdbc.sql("update project_memberships set joined_at = :joinedAt where id in (:leader, :member)")
                .param("joinedAt", Timestamp.from(Instant.parse("2026-08-14T00:00:00Z")))
                .param("leader", leaderMembershipId)
                .param("member", memberMembershipId)
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

    @Test
    void activeMemberCreatesOnlyASelfAssignedTaskWithEqualActors() {
        TaskView task = taskService.create(
                "member@example.test",
                new CreateTaskCommand(projectId, memberMembershipId, "  Draft results  ", "  notes  ", PROJECT_START));

        assertThat(task.status()).isEqualTo(TaskStatus.TODO);
        assertThat(task.title()).isEqualTo("Draft results");
        assertThat(task.description()).isEqualTo("notes");
        assertThat(task.creatorMembershipId()).isEqualTo(memberMembershipId);
        assertThat(task.assignerMembershipId()).isEqualTo(memberMembershipId);
        assertThat(task.assigneeMembershipId()).isEqualTo(memberMembershipId);
        assertThat(task.assigneeName()).isEqualTo("member@example.test");

        assertThatThrownBy(() -> taskService.create(
                        "member@example.test",
                        new CreateTaskCommand(projectId, leaderMembershipId, "Forbidden", null, null)))
                .isInstanceOf(TaskNotFoundException.class);
        assertThat(taskCount()).isEqualTo(1);
        assertThat(notificationCount()).isZero();
    }

    @Test
    void leaderAssignmentNotifiesOnlyNewAssigneeWithUnavailableEmail() {
        TaskView task = taskService.create(
                "leader@example.test",
                new CreateTaskCommand(projectId, memberMembershipId, "Notify assignee", null, null));

        assertThat(task.assigneeMembershipId()).isEqualTo(memberMembershipId);
        assertThat(notificationRecipientIds()).containsExactly(userId("member@example.test"));
        assertThat(notificationTypes()).containsExactly("TASK_ASSIGNED");
        assertThat(notificationEmailStatuses()).containsExactly("UNAVAILABLE");
        assertThat(notificationActionUrls()).containsExactly(
                "/projects/%d/tasks/%d".formatted(projectId, task.id()));
    }

    @Test
    void currentLeaderCreatesForAnotherActiveSameProjectMember() {
        TaskView task = taskService.create(
                "leader@example.test",
                new CreateTaskCommand(projectId, memberMembershipId, "Review results", null, PROJECT_END));

        assertThat(task.creatorMembershipId()).isEqualTo(leaderMembershipId);
        assertThat(task.assignerMembershipId()).isEqualTo(leaderMembershipId);
        assertThat(task.assigneeMembershipId()).isEqualTo(memberMembershipId);
    }

    /**
     * TSK-003, TSK-018, D35, AC-TSK-010 and AC-TSK-019 require both creation paths to begin
     * at TODO on PLANNED and ACTIVE Projects, with current membership attribution and no
     * self-notification.
     */
    @Test
    void leaderAssignmentAndMemberSelfTaskStartAtTodoOnPlannedAndActiveProjects() {
        assertCreationPathsStartAtTodo();

        activateProject();

        assertCreationPathsStartAtTodo();
        assertThat(taskCount()).isEqualTo(4);
        assertThat(notificationRecipientIds()).containsExactly(
                userId("member@example.test"), userId("member@example.test"));
    }

    /**
     * TSK-003 and AC-TSK-019 require all 12 combinations of Leader/member creation, PLANNED/ACTIVE
     * Project, and IN_PROGRESS/BLOCKED/DONE to throw TaskValidationException with Task and
     * notification counts unchanged. A missing exception or either count increasing is the
     * observable failure; both expected counts are derived from the pre-request database counts.
     */
    @Test
    void serviceRejectsEveryNonTodoRequestedInitialStatusBeforeWriting() {
        SoftAssertions softly = new SoftAssertions();
        assertServiceRejectsStatusesInProjectState(softly, "PLANNED");

        activateProject();

        assertServiceRejectsStatusesInProjectState(softly, "ACTIVE");
        softly.assertAll();
    }

    /**
     * TSK-003 and AC-TSK-019 require Leader assignment and member self-creation requests for
     * IN_PROGRESS, BLOCKED and DONE to redisplay the form with errors, never redirect, and leave
     * Task/notification counts unchanged. A success redirect or a changed count is the observable
     * failure; each expected count is derived from the pre-request database counts.
     */
    @Test
    void webRouteRejectsEveryNonTodoRequestedInitialStatusWithoutWriting() throws Exception {
        for (TaskStatus initialStatus : List.of(TaskStatus.IN_PROGRESS, TaskStatus.BLOCKED, TaskStatus.DONE)) {
            assertRejectedWebCreate("leader@example.test", initialStatus);
            assertRejectedWebCreate("member@example.test", initialStatus);
        }
    }

    private void assertServiceRejectsStatusesInProjectState(SoftAssertions softly, String projectState) {
        for (TaskStatus initialStatus : List.of(TaskStatus.IN_PROGRESS, TaskStatus.BLOCKED, TaskStatus.DONE)) {
            assertServiceRejectedWithoutWrites(
                    softly, "leader@example.test", memberMembershipId,
                    "Leader assignment", projectState, "leader-to-member", initialStatus);
            assertServiceRejectedWithoutWrites(
                    softly, "member@example.test", memberMembershipId,
                    "Member self-task", projectState, "member-self", initialStatus);
        }
    }

    private void assertServiceRejectedWithoutWrites(
            SoftAssertions softly,
            String actorEmail,
            long assigneeMembershipId,
            String title,
            String projectState,
            String creationPath,
            TaskStatus initialStatus) {
        long tasksBefore = taskCount();
        long notificationsBefore = notificationCount();
        String scenario = "%s Project, %s path, requested %s"
                .formatted(projectState, creationPath, initialStatus);

        softly.assertThatThrownBy(() -> taskService.create(
                        actorEmail,
                        new CreateTaskCommand(projectId, assigneeMembershipId, title, null,
                                null, null, initialStatus)))
                .as(scenario)
                .isInstanceOf(TaskValidationException.class);
        softly.assertThat(taskCount()).as("Task rows after %s", scenario).isEqualTo(tasksBefore);
        softly.assertThat(notificationCount()).as("Notifications after %s", scenario)
                .isEqualTo(notificationsBefore);
    }

    private void assertRejectedWebCreate(String actorEmail, TaskStatus initialStatus) throws Exception {
        long tasksBefore = taskCount();
        long notificationsBefore = notificationCount();
        mockMvc.perform(post("/projects/{projectId}/tasks", projectId)
                        .with(user(actorEmail).roles("INTERN"))
                        .with(csrf())
                        .param("title", "Invalid initial state")
                        .param("assigneeMembershipId", Long.toString(memberMembershipId))
                        .param("initialStatus", initialStatus.name()))
                .andExpect(status().isOk())
                .andExpect(view().name("tasks/form"))
                .andExpect(model().attributeHasErrors("taskForm"));
        assertThat(taskCount()).as("Task rows after %s web create by %s", initialStatus, actorEmail)
                .isEqualTo(tasksBefore);
        assertThat(notificationCount()).as("Notifications after %s web create by %s", initialStatus, actorEmail)
                .isEqualTo(notificationsBefore);
    }

    /**
     * TSK-003, TSK-018 and AC-TSK-019 preserve the pending-exit guard: the requester cannot
     * create a self-Task and a Leader cannot assign new work to that membership.
     */
    @Test
    void pendingExitMemberCannotCreateOrReceiveANewTask() {
        projectMutations.requestOwnLeave(userId("member@example.test"), projectId, "Leaving Project");
        long notificationsAfterExitRequest = notificationCount();

        assertThatThrownBy(() -> taskService.create(
                        "member@example.test",
                        new CreateTaskCommand(projectId, memberMembershipId, "Self task", null, null)))
                .isInstanceOf(TaskNotFoundException.class);
        assertThatThrownBy(() -> taskService.create(
                        "leader@example.test",
                        new CreateTaskCommand(projectId, memberMembershipId, "Assigned task", null, null)))
                .isInstanceOf(TaskNotFoundException.class);

        assertThat(taskCount()).isZero();
        assertThat(notificationCount()).isEqualTo(notificationsAfterExitRequest);
    }

    private void assertCreationPathsStartAtTodo() {
        long notificationsBefore = notificationCount();
        TaskView assigned = taskService.create(
                "leader@example.test",
                new CreateTaskCommand(projectId, memberMembershipId, "Leader assignment", null,
                        null, null, TaskStatus.TODO));
        assertThat(assigned.status()).isEqualTo(TaskStatus.TODO);
        assertThat(assigned.creatorMembershipId()).isEqualTo(leaderMembershipId);
        assertThat(assigned.assignerMembershipId()).isEqualTo(leaderMembershipId);
        assertThat(assigned.assigneeMembershipId()).isEqualTo(memberMembershipId);

        TaskView self = taskService.create(
                "member@example.test",
                new CreateTaskCommand(projectId, memberMembershipId, "Member self task", null, null));
        assertThat(self.status()).isEqualTo(TaskStatus.TODO);
        assertThat(self.creatorMembershipId()).isEqualTo(memberMembershipId);
        assertThat(self.assignerMembershipId()).isEqualTo(memberMembershipId);
        assertThat(self.assigneeMembershipId()).isEqualTo(memberMembershipId);
        assertThat(notificationCount()).isEqualTo(notificationsBefore + 1);
        assertThat(notificationRecipientIds()).hasSize((int) notificationsBefore + 1)
                .last().isEqualTo(userId("member@example.test"));
    }

    /**
     * TSK-001, DB-004 and AC-TSK-001 require both service validation and the V1 composite
     * membership/Project foreign key to reject a foreign-Project assignee. The service must leave
     * zero Tasks; bypassing it with a Task row using Project A and Project B's membership must
     * fail at PostgreSQL with SQLState 23503.
     */
    @Test
    void rejectsCrossProjectAndInactiveAssigneesWithoutWriting() {
        long mentorId = userId("mentor@example.test");
        long outsiderId = insertIntern("outsider@example.test");
        long otherProjectId = insertProject(mentorId, "PLANNED");
        long otherMembershipId = insertMembership(otherProjectId, outsiderId, mentorId);
        jdbc.sql("update project_memberships set left_at = joined_at + interval '1 second', removed_by_mentor_user_id = :mentorId where id = :id")
                .param("mentorId", mentorId)
                .param("id", memberMembershipId)
                .update();

        assertThatThrownBy(() -> taskService.create(
                        "leader@example.test",
                        new CreateTaskCommand(projectId, otherMembershipId, "Cross project", null, null)))
                .isInstanceOf(TaskNotFoundException.class);
        assertThatThrownBy(() -> taskService.create(
                        "leader@example.test",
                        new CreateTaskCommand(projectId, memberMembershipId, "Inactive", null, null)))
                .isInstanceOf(TaskNotFoundException.class);
        assertThat(taskCount()).isZero();

        assertThatThrownBy(() -> jdbc.sql("""
                        insert into tasks (
                            project_id, assignee_membership_id, title,
                            created_by_membership_id, assigned_by_membership_id)
                        values (:projectId, :foreignMembershipId, 'Foreign SQL Task',
                                :leaderMembershipId, :leaderMembershipId)
                        """)
                .param("projectId", projectId)
                .param("foreignMembershipId", otherMembershipId)
                .param("leaderMembershipId", leaderMembershipId)
                .update())
                .isInstanceOf(DataIntegrityViolationException.class)
                .rootCause()
                .isInstanceOfSatisfying(PSQLException.class,
                        failure -> assertThat(failure.getSQLState()).isEqualTo("23503"));
    }

    /**
     * TSK-002 and AC-TSK-015 require an active Intern to hold Tasks across Projects without a
     * membership-based cap. One Task in each Project and a second in the first yields three rows;
     * each row has exactly its Project-local membership as its single current assignee.
     */
    @Test
    void activeInternCanBeAssignedTasksAcrossProjectsWithoutACap() {
        long mentorId = userId("mentor@example.test");
        long secondProjectId = insertProject(mentorId, "PLANNED");
        long secondLeaderMembershipId = insertMembership(
                secondProjectId, userId("leader@example.test"), mentorId);
        long secondMembershipId = insertMembership(
                secondProjectId, userId("member@example.test"), mentorId);
        jdbc.sql("""
                        insert into project_leadership_terms
                            (project_id, membership_id, appointed_by_mentor_user_id)
                        values (:projectId, :membershipId, :mentorId)
                        """)
                .param("projectId", secondProjectId)
                .param("membershipId", secondLeaderMembershipId)
                .param("mentorId", mentorId)
                .update();
        jdbc.sql("update project_memberships set joined_at = :joinedAt where id in (:leaderId, :memberId)")
                .param("joinedAt", Timestamp.from(Instant.parse("2026-08-14T00:00:00Z")))
                .param("leaderId", secondLeaderMembershipId)
                .param("memberId", secondMembershipId)
                .update();

        TaskView firstProjectTask = taskService.create(
                "member@example.test",
                new CreateTaskCommand(projectId, memberMembershipId, "First Project task", null, null));
        TaskView secondProjectTask = taskService.create(
                "member@example.test",
                new CreateTaskCommand(secondProjectId, secondMembershipId, "Second Project task", null, null));
        TaskView additionalFirstProjectTask = taskService.create(
                "member@example.test",
                new CreateTaskCommand(projectId, memberMembershipId, "Additional first Project task", null, null));

        assertThat(List.of(firstProjectTask, secondProjectTask, additionalFirstProjectTask))
                .extracting(TaskView::assigneeMembershipId)
                .containsExactly(memberMembershipId, secondMembershipId, memberMembershipId);
        assertThat(jdbc.sql("select count(*) from tasks where assignee_membership_id in (:first, :second)")
                .param("first", memberMembershipId)
                .param("second", secondMembershipId)
                .query(Long.class).single()).isEqualTo(3L);
        assertThat(jdbc.sql("select count(*) from tasks where id in (:first, :second, :third)")
                .param("first", firstProjectTask.id())
                .param("second", secondProjectTask.id())
                .param("third", additionalFirstProjectTask.id())
                .query(Long.class).single()).isEqualTo(3L);
    }

    /**
     * TSK-009, TSK-019 and AC-TSK-004 require a DONE Task to reject reassignment before any Task,
     * retained-history, or notification change. The expected unchanged evidence is the complete
     * Task view, its TaskHistoryView including status, and the exact notification count.
     */
    @Test
    void doneTaskReassignmentIsRefusedWithoutChangingTaskHistoryOrNotifications() {
        TaskView task = createMemberTask("Completed transfer refusal");
        taskService.addComment("member@example.test", projectId, task.id(), "Retained before refusal");
        setStatus(task.id(), TaskStatus.DONE);
        TaskDetails before = taskService.details("leader@example.test", projectId, task.id());
        TaskHistoryView historyBefore = taskQueries.history(projectId).stream()
                .filter(row -> row.id() == task.id()).findFirst().orElseThrow();
        long notificationsBefore = notificationCount();

        assertThatThrownBy(() -> taskService.reassign(
                        "leader@example.test", projectId, task.id(), memberMembershipId))
                .isInstanceOf(TaskValidationException.class);

        assertThat(taskService.details("leader@example.test", projectId, task.id()).task())
                .isEqualTo(before.task());
        assertThat(taskQueries.history(projectId).stream()
                .filter(row -> row.id() == task.id()).findFirst().orElseThrow())
                .isEqualTo(historyBefore);
        assertThat(notificationCount()).isEqualTo(notificationsBefore);
    }

    /**
     * TSK-009 and TSK-019 require a current Leader and an eligible active target who is not in a
     * pending exit. A transfer to an active recipient succeeds; subsequent pending-exit and closed
     * membership targets, and a non-Leader actor, are refused with assignment and notifications
     * unchanged from the successful transfer.
     */
    @Test
    void reassignmentRequiresCurrentLeaderAndEligibleNonExitingActiveRecipient() {
        TaskView task = taskService.create(
                "leader@example.test",
                new CreateTaskCommand(projectId, leaderMembershipId, "Eligibility transfer", null, null));
        long activeRecipientId = insertIntern("active-recipient@example.test");
        long activeRecipientMembershipId = insertMembership(
                projectId, activeRecipientId, userId("mentor@example.test"));
        long closedRecipientId = insertIntern("closed-recipient@example.test");
        long closedRecipientMembershipId = insertMembership(
                projectId, closedRecipientId, userId("mentor@example.test"));
        projectMutations.requestOwnLeave(userId("member@example.test"), projectId, "Pending exit target");
        closeMembership(closedRecipientMembershipId);

        TaskView reassigned = taskService.reassign(
                "leader@example.test", projectId, task.id(), activeRecipientMembershipId);
        long notificationsAfterSuccess = notificationCount();

        assertThat(reassigned.assigneeMembershipId()).isEqualTo(activeRecipientMembershipId);
        assertThatThrownBy(() -> taskService.reassign(
                        "leader@example.test", projectId, task.id(), memberMembershipId))
                .isInstanceOf(TaskNotFoundException.class);
        assertThatThrownBy(() -> taskService.reassign(
                        "leader@example.test", projectId, task.id(), closedRecipientMembershipId))
                .isInstanceOf(TaskNotFoundException.class);
        assertThatThrownBy(() -> taskService.reassign(
                        "active-recipient@example.test", projectId, task.id(), leaderMembershipId))
                .isInstanceOf(TaskNotFoundException.class);

        TaskView unchanged = taskService.details("leader@example.test", projectId, task.id()).task();
        assertThat(unchanged.assigneeMembershipId()).isEqualTo(activeRecipientMembershipId);
        assertThat(unchanged.assignerMembershipId()).isEqualTo(leaderMembershipId);
        assertThat(unchanged.assignedAt()).isEqualTo(reassigned.assignedAt());
        assertThat(notificationCount()).isEqualTo(notificationsAfterSuccess);
    }

    /**
     * TSK-005, AC-TSK-002 require creation and edits outside the Project range or on a current
     * global day off to be rejected. From an August 1–31 Project, the manually derived invalid
     * inputs are July 31, September 1, and the configured August 15 day off; the two valid range
     * boundaries remain accepted and each rejected edit leaves its original due date unchanged.
     */
    @Test
    void acceptsProjectBoundaryDueDatesAndRejectsOutsideOrCurrentDayOff() {
        taskService.create(
                "leader@example.test",
                new CreateTaskCommand(projectId, memberMembershipId, "Start boundary", null, PROJECT_START));
        taskService.create(
                "leader@example.test",
                new CreateTaskCommand(projectId, memberMembershipId, "End boundary", null, PROJECT_END));
        insertDayOff(LocalDate.of(2026, 8, 15));

        TaskView editable = createMemberTask("Edit date boundaries");
        assertThatThrownBy(() -> taskService.create(
                        "leader@example.test",
                        new CreateTaskCommand(projectId, memberMembershipId, "Before", null, PROJECT_START.minusDays(1))))
                .isInstanceOf(TaskValidationException.class);
        assertThatThrownBy(() -> taskService.create(
                        "leader@example.test",
                        new CreateTaskCommand(projectId, memberMembershipId, "After", null, PROJECT_END.plusDays(1))))
                .isInstanceOf(TaskValidationException.class);
        assertThatThrownBy(() -> taskService.create(
                        "leader@example.test",
                        new CreateTaskCommand(projectId, memberMembershipId, "Day off", null, LocalDate.of(2026, 8, 15))))
                .isInstanceOf(TaskValidationException.class);
        assertThatThrownBy(() -> taskService.edit(
                        "member@example.test", projectId, editable.id(), "Edit before", null, PROJECT_START.minusDays(1)))
                .isInstanceOf(TaskValidationException.class);
        assertThatThrownBy(() -> taskService.edit(
                        "member@example.test", projectId, editable.id(), "Edit after", null, PROJECT_END.plusDays(1)))
                .isInstanceOf(TaskValidationException.class);
        assertThatThrownBy(() -> taskService.edit(
                        "member@example.test", projectId, editable.id(), "Edit day off", null, LocalDate.of(2026, 8, 15)))
                .isInstanceOf(TaskValidationException.class);
        assertThat(taskCount()).isEqualTo(3);
        assertThat(jdbc.sql("select count(*) from tasks where id = :id and due_date is null")
                .param("id", editable.id()).query(Long.class).single()).isEqualTo(1L);
    }

    /**
     * TSK-006, AC-TSK-002 require later day-off changes to preserve stored due dates and expose
     * exactly current Tasks on the affected date to the Task-owned impact query. The expected
     * result is the one live August 20 Task; a soft-deleted Task and a live August 21 Task are
     * excluded, while the stored due date remains August 20.
     */
    @Test
    void laterDayOffKeepsExistingDueDateAndListsOnlyCurrentAffectedTasks() {
        LocalDate impactDate = LocalDate.of(2026, 8, 20);
        TaskView affected = taskService.create(
                "leader@example.test",
                new CreateTaskCommand(projectId, memberMembershipId, "Affected", null, impactDate));
        TaskView deleted = taskService.create(
                "leader@example.test",
                new CreateTaskCommand(projectId, memberMembershipId, "Deleted", null, impactDate));
        softDelete(deleted.id());
        TaskView differentDate = taskService.create(
                "leader@example.test",
                new CreateTaskCommand(projectId, memberMembershipId, "Different date", null, impactDate.plusDays(1)));

        calendar.createManual(userId("bootstrap@example.test"), impactDate, "Task impact day off", true);

        assertThat(taskQueries.dueDateImpacts(impactDate))
                .containsExactly(new TaskDueDateImpactView(
                        affected.id(), projectId, "Affected", impactDate,
                        TaskStatus.TODO, memberMembershipId));
        assertThat(jdbc.sql("select due_date from tasks where id = :id")
                .param("id", affected.id()).query(LocalDate.class).single())
                .isEqualTo(impactDate);
        assertThat(jdbc.sql("select due_date from tasks where id = :id")
                .param("id", differentDate.id()).query(LocalDate.class).single())
                .isEqualTo(impactDate.plusDays(1));
    }

    @Test
    void taskProgressAndDueDatePathsHaveTheirSupportingPostgresIndexes() {
        List<String> indexes = jdbc.sql("""
                        select indexdef
                        from pg_indexes
                        where schemaname = 'public' and tablename = 'tasks'
                        """)
                .query(String.class)
                .list();

        assertThat(indexes).anyMatch(index -> index.contains("ix_tasks_project_status_active")
                && index.contains("(project_id, status, id)"));
        assertThat(indexes).anyMatch(index -> index.contains("ix_tasks_due_date_active")
                && index.contains("(due_date, project_id)"));
    }

    @Test
    void taskProgressAndDueDateQueriesUseTheirSupportingPostgresIndexes() {
        TaskView task = taskService.create(
                "leader@example.test",
                new CreateTaskCommand(projectId, memberMembershipId, "Planner proof", null,
                        LocalDate.of(2026, 8, 20)));
        taskWorkLogs.saveAndFlush(new TaskWorkLog(
                projectId,
                task.id(),
                memberMembershipId,
                LocalDate.of(2026, 8, 20),
                30,
                "Planner effort",
                Instant.parse("2026-08-20T01:00:00Z")));
        jdbc.sql("set local enable_seqscan = off").update();
        jdbc.sql("set local enable_indexscan = off").update();

        String progressPlan = explain("""
                        select
                            coalesce(sum(case when status = 'TODO' then 1 else 0 end), 0),
                            coalesce(sum(case when status = 'IN_PROGRESS' then 1 else 0 end), 0),
                            coalesce(sum(case when status = 'BLOCKED' then 1 else 0 end), 0),
                            coalesce(sum(case when status = 'DONE' then 1 else 0 end), 0),
                            coalesce((select sum(log.minutes)
                                      from task_work_logs log
                                      where log.project_id = :projectId), 0)
                        from tasks
                        where project_id = :projectId
                          and deleted_at is null
                        """)
                .replace("\n", " ");
        assertThat(progressPlan)
                .contains("Index")
                .contains("ix_tasks_")
                .contains("ix_task_work_logs_project_date")
                .doesNotContain("Seq Scan");
        jdbc.sql("set local enable_indexscan = on").update();
        jdbc.sql("set local enable_bitmapscan = off").update();
        String dueDatePlan = explain("""
                        select id, project_id, due_date
                        from tasks
                        where due_date = :dueDate and deleted_at is null
                        order by project_id, id
                        """)
                .replace("\n", " ");
        assertThat(dueDatePlan).contains("ix_tasks_due_date_active");
        assertThat(jdbc.sql("select due_date from tasks where id = :id")
                .param("id", task.id()).query(LocalDate.class).single())
                .isEqualTo(LocalDate.of(2026, 8, 20));
    }

    /**
     * TSK-007, TSK-025, DB-020, AC-TSK-003, AC-TSK-016 (assignee), AC-TSK-018 (permitted
     * assignee actor), and AC-TSK-020 require a successful block to retain exactly its before/after
     * states, actor and server time; the observable defect is a BLOCKED Task with no ledger row,
     * and the hand-derived expected ledger count is one.
     */
    @Test
    void successfulStatusChangeAppendsTransitionWithActorAndServerTime() {
        TaskView task = createMemberTask("Ledger transition");
        activateProject();
        taskService.changeStatus("member@example.test", projectId, task.id(), TaskStatus.BLOCKED);

        assertThat(jdbc.sql("select count(*) from task_status_transitions where task_id = :taskId")
                        .param("taskId", task.id()).query(Long.class).single())
                .isEqualTo(1);
        assertThat(jdbc.sql("select from_status from task_status_transitions where task_id = :taskId")
                        .param("taskId", task.id()).query(String.class).single())
                .isEqualTo("TODO");
        assertThat(jdbc.sql("select to_status from task_status_transitions where task_id = :taskId")
                        .param("taskId", task.id()).query(String.class).single())
                .isEqualTo("BLOCKED");
        assertThat(jdbc.sql("select actor_user_id from task_status_transitions where task_id = :taskId")
                        .param("taskId", task.id()).query(Long.class).single())
                .isEqualTo(userId("member@example.test"));
        Instant occurredAt = jdbc.sql("select occurred_at from task_status_transitions where task_id = :taskId")
                .param("taskId", task.id()).query(Timestamp.class).single().toInstant();
        assertThat(occurredAt).isEqualTo(clock.instant());
        assertThat(jdbc.sql("select count(*) from task_status_transitions where task_id = :taskId and reason is null")
                        .param("taskId", task.id()).query(Long.class).single())
                .isEqualTo(1);
    }

    /**
     * TSK-007, TSK-025, DB-020, D46, and AC-TSK-022 require an authorized assignee to choose
     * either legal destination when a legacy BLOCKED Task has no block history, retaining only
     * the observed unblock; the expected initial ledger count is one per Task and the later
     * recorded block must still restrict restoration to its actual origin under AC-TSK-020.
     */
    @Test
    void legacyBlockedTasksCanChooseEitherUnblockTargetWithoutInventingBlockHistory() {
        TaskView restoreTodo = createMemberTask("Legacy blocked to TODO");
        TaskView restoreInProgress = createMemberTask("Legacy blocked to IN_PROGRESS");
        activateProject();
        jdbc.sql("update tasks set status = 'BLOCKED' where id in (:todoId, :inProgressId)")
                .param("todoId", restoreTodo.id())
                .param("inProgressId", restoreInProgress.id())
                .update();
        entityManager.clear();

        taskService.changeStatus("member@example.test", projectId, restoreTodo.id(), TaskStatus.TODO);
        taskService.changeStatus(
                "member@example.test", projectId, restoreInProgress.id(), TaskStatus.IN_PROGRESS);

        assertThat(snapshot(restoreTodo.id()).status()).isEqualTo("TODO");
        assertThat(snapshot(restoreInProgress.id()).status()).isEqualTo("IN_PROGRESS");
        assertThat(jdbc.sql("""
                        select task_id, from_status, to_status, actor_user_id, occurred_at, reason
                        from task_status_transitions where task_id in (:todoId, :inProgressId)
                        order by task_id
                        """)
                .param("todoId", restoreTodo.id())
                .param("inProgressId", restoreInProgress.id())
                .query((row, index) -> new Object[] {
                        row.getLong("task_id"), row.getString("from_status"), row.getString("to_status"),
                        row.getLong("actor_user_id"), row.getTimestamp("occurred_at").toInstant(),
                        row.getString("reason")
                })
                .list()).containsExactlyInAnyOrder(
                        new Object[] { restoreTodo.id(), "BLOCKED", "TODO",
                                userId("member@example.test"), clock.instant(), null },
                        new Object[] { restoreInProgress.id(), "BLOCKED", "IN_PROGRESS",
                                userId("member@example.test"), clock.instant(), null });
        assertThat(jdbc.sql("""
                        select count(*) from task_status_transitions
                        where task_id in (:todoId, :inProgressId) and to_status = 'BLOCKED'
                        """)
                .param("todoId", restoreTodo.id())
                .param("inProgressId", restoreInProgress.id())
                .query(Long.class).single()).isZero();

        taskService.changeStatus("member@example.test", projectId, restoreTodo.id(), TaskStatus.BLOCKED);
        assertThatThrownBy(() -> taskService.changeStatus(
                        "member@example.test", projectId, restoreTodo.id(), TaskStatus.IN_PROGRESS))
                .isInstanceOf(TaskValidationException.class);
        taskService.changeStatus("member@example.test", projectId, restoreTodo.id(), TaskStatus.TODO);

        assertThat(snapshot(restoreTodo.id()).status()).isEqualTo("TODO");
        assertThat(jdbc.sql("""
                        select from_status || '>' || to_status
                        from task_status_transitions where task_id = :taskId order by id
                        """)
                .param("taskId", restoreTodo.id())
                .query(String.class).list())
                .containsExactly("BLOCKED>TODO", "TODO>BLOCKED", "BLOCKED>TODO");
        assertThat(jdbc.sql("select count(*) from task_status_transitions where task_id = :taskId")
                .param("taskId", restoreInProgress.id()).query(Long.class).single()).isEqualTo(1);
    }

    /**
     * TSK-007, TSK-025, AC-TSK-020, and DB-020 require unblock to restore the newest block origin,
     * reject a caller-selected alternative without changing history, and retain a nonblank reopen
     * reason. The expected sequence is TODO→BLOCKED→TODO→IN_PROGRESS→BLOCKED→IN_PROGRESS (four
     * ledger rows), followed by one reason-bearing reopen row; reasonless reopen adds none.
     */
    @Test
    void unblockRestoresLatestOriginAndReopenRequiresRetainedReason() {
        TaskView task = createMemberTask("Latest block origin");
        activateProject();

        taskService.changeStatus("member@example.test", projectId, task.id(), TaskStatus.BLOCKED);
        TaskSnapshot blockedBeforeWrongDestination = snapshot(task.id());
        long notificationsBeforeWrongDestination = notificationCount();
        assertThatThrownBy(() -> taskService.changeStatus(
                        "member@example.test", projectId, task.id(), TaskStatus.IN_PROGRESS))
                .isInstanceOf(TaskValidationException.class);
        assertThat(snapshot(task.id())).isEqualTo(blockedBeforeWrongDestination);
        assertThat(transitionCount(task.id())).isEqualTo(1);
        assertThat(notificationCount()).isEqualTo(notificationsBeforeWrongDestination);

        taskService.changeStatus("member@example.test", projectId, task.id(), TaskStatus.TODO);
        taskService.changeStatus("member@example.test", projectId, task.id(), TaskStatus.IN_PROGRESS);
        taskService.changeStatus("member@example.test", projectId, task.id(), TaskStatus.BLOCKED);
        TaskSnapshot latestBlockBeforeWrongDestination = snapshot(task.id());
        long notificationsBeforeLatestWrongDestination = notificationCount();
        assertThatThrownBy(() -> taskService.changeStatus(
                        "member@example.test", projectId, task.id(), TaskStatus.TODO))
                .isInstanceOf(TaskValidationException.class);
        assertThat(snapshot(task.id())).isEqualTo(latestBlockBeforeWrongDestination);
        assertThat(transitionCount(task.id())).isEqualTo(3);
        assertThat(notificationCount()).isEqualTo(notificationsBeforeLatestWrongDestination);
        taskService.changeStatus("member@example.test", projectId, task.id(), TaskStatus.IN_PROGRESS);
        assertThat(transitionCount(task.id())).isEqualTo(4);

        setStatus(task.id(), TaskStatus.DONE);
        long notificationsBeforeReopen = notificationCount();
        assertThatThrownBy(() -> taskService.changeStatus(
                        "member@example.test", projectId, task.id(), TaskStatus.IN_PROGRESS))
                .isInstanceOf(TaskValidationException.class);
        assertThatThrownBy(() -> taskService.changeStatus(
                        "member@example.test", projectId, task.id(), null,
                        new TaskStatusChangeCommand(TaskStatus.IN_PROGRESS, "   ")))
                .isInstanceOf(TaskValidationException.class);
        assertThat(snapshot(task.id()).status()).isEqualTo("DONE");
        assertThat(transitionCount(task.id())).isEqualTo(4);
        assertThat(notificationCount()).isEqualTo(notificationsBeforeReopen);

        taskService.changeStatus("member@example.test", projectId, task.id(), null,
                new TaskStatusChangeCommand(TaskStatus.IN_PROGRESS, "Correct the final calculation"));
        assertThat(snapshot(task.id()).status()).isEqualTo("IN_PROGRESS");
        assertThat(transitionCount(task.id())).isEqualTo(5);
        assertThat(jdbc.sql("select reason from task_status_transitions where task_id = :taskId and from_status = 'DONE'")
                        .param("taskId", task.id()).query(String.class).single())
                .isEqualTo("Correct the final calculation");
        assertThat(jdbc.sql("select from_status || '>' || to_status from task_status_transitions where task_id = :taskId order by id")
                        .param("taskId", task.id()).query(String.class).list())
                .containsExactly("TODO>BLOCKED", "BLOCKED>TODO", "IN_PROGRESS>BLOCKED",
                        "BLOCKED>IN_PROGRESS", "DONE>IN_PROGRESS");
        assertThat(jdbc.sql("""
                        select count(*) from task_status_transitions
                        where task_id = :taskId and actor_user_id = :actorId and occurred_at = :occurredAt
                        """)
                        .param("taskId", task.id())
                        .param("actorId", userId("member@example.test"))
                        .param("occurredAt", Timestamp.from(clock.instant()))
                        .query(Long.class).single())
                .isEqualTo(5);
    }

    /**
     * TSK-007, AC-TSK-021, PRJ-013 and DB-020 require deleted Tasks, PLANNED Projects, and edges
     * outside the fixed graph to refuse status writes with unchanged Task and transition history;
     * each refusal therefore leaves the expected ledger count at zero.
     */
    @Test
    void refusesDeletedPlannedAndInvalidStatusTransitionsWithoutLedgerRows() {
        TaskView plannedTask = createMemberTask("Planned status refusal");
        long notificationsBefore = notificationCount();
        assertThatThrownBy(() -> taskService.changeStatus(
                        "member@example.test", projectId, plannedTask.id(), TaskStatus.BLOCKED))
                .isInstanceOf(TaskNotFoundException.class);
        assertThat(snapshot(plannedTask.id()).status()).isEqualTo("TODO");
        assertThat(transitionCount(plannedTask.id())).isZero();
        assertThat(notificationCount()).isEqualTo(notificationsBefore);

        activateProject();
        assertThatThrownBy(() -> taskService.changeStatus(
                        "member@example.test", projectId, plannedTask.id(), TaskStatus.DONE))
                .isInstanceOf(TaskValidationException.class);
        assertThat(transitionCount(plannedTask.id())).isZero();

        TaskView deletedTask = createMemberTask("Deleted status refusal");
        softDelete(deletedTask.id());
        assertThatThrownBy(() -> taskService.changeStatus(
                        "member@example.test", projectId, deletedTask.id(), TaskStatus.BLOCKED))
                .isInstanceOf(TaskNotFoundException.class);
        assertThat(transitionCount(deletedTask.id())).isZero();
        assertThat(notificationCount()).isEqualTo(notificationsBefore);
    }

    /**
     * NOT-003 and AC-TSK-018 require an owning Mentor's successful block and reopen to notify the
     * assignee and current Leader in-app only; the hand-derived result is two recipients per event
     * with email status NOT_REQUIRED for every notification.
     */
    @Test
    void mentorStatusChangeNotifiesAssigneeAndLeaderInAppWithoutEmail() {
        TaskView task = createMemberTask("Mentor notification");
        activateProject();
        int before = notificationRecipientIds().size();

        taskService.changeStatus("mentor@example.test", projectId, task.id(), TaskStatus.BLOCKED);

        List<Long> recipientIds = notificationRecipientIds();
        assertThat(recipientIds.subList(before, recipientIds.size()))
                .containsExactly(userId("leader@example.test"), userId("member@example.test"));
        int afterBlock = recipientIds.size();
        setStatus(task.id(), TaskStatus.DONE);
        taskService.changeStatus("mentor@example.test", projectId, task.id(), null,
                new TaskStatusChangeCommand(TaskStatus.IN_PROGRESS, "Correct the submitted result"));
        List<Long> afterReopenRecipients = notificationRecipientIds();
        assertThat(afterReopenRecipients.subList(afterBlock, afterReopenRecipients.size()))
                .containsExactly(userId("leader@example.test"), userId("member@example.test"));
        assertThat(jdbc.sql("select email_status from notifications where notification_type = 'TASK_STATUS_CHANGED'")
                        .query(String.class).list())
                .containsOnly("NOT_REQUIRED");
    }

    private long transitionCount(long taskId) {
        return jdbc.sql("select count(*) from task_status_transitions where task_id = :taskId")
                .param("taskId", taskId).query(Long.class).single();
    }

    @Test
    void taskAuthorizationMatrixKeepsAdminReadOnlyAndRejectsGuessedOrCrossContextMutations() {
        TaskView task = createMemberTask("Authorization matrix");
        insertUser("admin@example.test", "ADMIN");

        assertThat(taskService.list("admin@example.test", projectId).tasks())
                .extracting(TaskView::id)
                .containsExactly(task.id());
        assertThatThrownBy(() -> taskService.edit(
                        "admin@example.test", projectId, task.id(), "No", null, null))
                .isInstanceOf(TaskNotFoundException.class);
        assertThatThrownBy(() -> taskService.changeStatus(
                        "admin@example.test", projectId, task.id(), TaskStatus.IN_PROGRESS))
                .isInstanceOf(TaskNotFoundException.class);
        assertThatThrownBy(() -> taskService.details(
                        "admin@example.test", projectId + 9999, task.id()))
                .isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    void authorizationMatrixKeepsEveryDeniedActorNonDisclosingAndStateUnchanged() {
        TaskView task = createMemberTask("Complete authorization matrix");
        long replacementLeaderId = insertIntern("matrix-current-leader@example.test");
        long replacementLeaderMembershipId = insertMembership(
                projectId, replacementLeaderId, userId("mentor@example.test"));
        long unassignedId = insertIntern("matrix-unassigned@example.test");
        insertMembership(projectId, unassignedId, userId("mentor@example.test"));
        long formerMemberId = insertIntern("matrix-former-member@example.test");
        long formerMemberMembershipId = insertMembership(
                projectId, formerMemberId, userId("mentor@example.test"));
        insertIntern("matrix-unrelated@example.test");
        insertUser("matrix-other-mentor@example.test", "MENTOR");
        insertUser("matrix-admin@example.test", "ADMIN");

        jdbc.sql("""
                        update project_leadership_terms
                        set ended_at = started_at + interval '1 second',
                            ended_by_mentor_user_id = :mentorId
                        where project_id = :projectId and ended_at is null
                        """)
                .param("projectId", projectId)
                .param("mentorId", userId("mentor@example.test"))
                .update();
        jdbc.sql("""
                        insert into project_leadership_terms
                            (project_id, membership_id, appointed_by_mentor_user_id, started_at)
                        values (:projectId, :membershipId, :mentorId,
                                current_timestamp + interval '1 second')
                        """)
                .param("projectId", projectId)
                .param("membershipId", replacementLeaderMembershipId)
                .param("mentorId", userId("mentor@example.test"))
                .update();
        closeMembership(formerMemberMembershipId);
        activateProject();

        assertThat(taskService.list("mentor@example.test", projectId).tasks())
                .extracting(TaskView::id).containsExactly(task.id());
        assertThat(taskService.list("matrix-admin@example.test", projectId).tasks())
                .extracting(TaskView::id).containsExactly(task.id());
        assertThat(taskService.list("leader@example.test", projectId).tasks())
                .extracting(TaskView::id).containsExactly(task.id());
        assertThat(taskService.list("matrix-current-leader@example.test", projectId).tasks())
                .extracting(TaskView::id).containsExactly(task.id());
        assertThat(taskService.list("matrix-unassigned@example.test", projectId).tasks())
                .extracting(TaskView::id).containsExactly(task.id());
        assertThatThrownBy(() -> taskService.list("matrix-other-mentor@example.test", projectId))
                .isInstanceOf(TaskNotFoundException.class);
        assertThatThrownBy(() -> taskService.list("matrix-former-member@example.test", projectId))
                .isInstanceOf(TaskNotFoundException.class);
        assertThatThrownBy(() -> taskService.list("matrix-unrelated@example.test", projectId))
                .isInstanceOf(TaskNotFoundException.class);

        assertThat(taskService.details("member@example.test", projectId, task.id()))
                .satisfies(details -> {
                    assertThat(details.canEdit()).isTrue();
                    assertThat(details.canDelete()).isTrue();
                    assertThat(details.canChangeStatus()).isTrue();
                });
        assertThat(taskService.details("matrix-current-leader@example.test", projectId, task.id()))
                .satisfies(details -> {
                    assertThat(details.canEdit()).isTrue();
                    assertThat(details.canDelete()).isTrue();
                    assertThat(details.canReassign()).isTrue();
                });
        assertThat(taskService.details("mentor@example.test", projectId, task.id()).canChangeStatus())
                .isTrue();
        assertDeniedAndUnchanged(task,
                () -> taskService.edit("mentor@example.test", projectId, task.id(), "Denied", null, null));
        assertDeniedAndUnchanged(task,
                () -> taskService.softDelete("mentor@example.test", projectId, task.id()));
        assertDeniedAndUnchanged(task,
                () -> taskService.reassign("mentor@example.test", projectId, task.id(), leaderMembershipId));

        List<String> deniedActors = List.of(
                "matrix-admin@example.test",
                "leader@example.test",
                "matrix-unassigned@example.test",
                "matrix-other-mentor@example.test",
                "matrix-former-member@example.test",
                "matrix-unrelated@example.test");
        for (String actor : deniedActors) {
            assertDeniedAndUnchanged(task,
                    () -> taskService.changeStatus(actor, projectId, task.id(), TaskStatus.IN_PROGRESS));
            assertDeniedAndUnchanged(task,
                    () -> taskService.edit(actor, projectId, task.id(), "Denied", null, null));
            assertDeniedAndUnchanged(task,
                    () -> taskService.softDelete(actor, projectId, task.id()));
            assertDeniedAndUnchanged(task,
                    () -> taskService.reassign(actor, projectId, task.id(), leaderMembershipId));
        }

    }

    @Test
    void onlyCurrentAssigneeOrOwningMentorChangesStatusOnAnActiveProject() {
        TaskView task = taskService.create(
                "member@example.test",
                new CreateTaskCommand(projectId, memberMembershipId, "Run experiment", null, null));

        assertThatThrownBy(() -> taskService.changeStatus(
                        "member@example.test", projectId, task.id(), TaskStatus.IN_PROGRESS))
                .isInstanceOf(TaskNotFoundException.class);
        activateProject();
        assertThatThrownBy(() -> taskService.changeStatus(
                        "leader@example.test", projectId, task.id(), TaskStatus.IN_PROGRESS))
                .isInstanceOf(TaskNotFoundException.class);

        TaskView inProgress = taskService.changeStatus(
                "member@example.test", projectId, task.id(), TaskStatus.IN_PROGRESS);

        assertThat(inProgress.status()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThatThrownBy(() -> taskService.changeStatus(
                        "member@example.test", projectId, task.id(), TaskStatus.TODO))
                .isInstanceOf(TaskValidationException.class);
        assertThat(notificationRecipientIds()).containsExactly(userId("leader@example.test"));
        assertThat(notificationTypes()).containsExactly("TASK_STATUS_CHANGED");
        assertThat(notificationEmailStatuses()).containsExactly("NOT_REQUIRED");
        assertThat(notificationActionUrls()).containsExactly(
                "/projects/%d/tasks/%d".formatted(projectId, task.id()));
    }

    @Test
    void owningMentorCanChangeStatusForAnyTaskOnAnActiveProject() {
        TaskView task = taskService.create(
                "member@example.test",
                new CreateTaskCommand(projectId, memberMembershipId, "Mentor status control", null, null));
        activateProject();

        TaskView changed = taskService.changeStatus(
                "mentor@example.test", projectId, task.id(), TaskStatus.IN_PROGRESS);

        assertThat(changed.status()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(jdbc.sql("select status from tasks where id = :id")
                .param("id", task.id())
                .query(String.class)
                .single()).isEqualTo(TaskStatus.IN_PROGRESS.name());
        assertThat(notificationRecipientIds()).containsExactly(userId("leader@example.test"));
        assertThat(notificationTypes()).containsExactly("TASK_STATUS_CHANGED");
        assertThat(notificationEmailStatuses()).containsExactly("NOT_REQUIRED");
    }

    /**
     * TSK-011, TSK-012 and AC-TSK-006 require an active member, current Leader, and owning Mentor
     * to append comments before Project completion. The three authorized appends produce exactly
     * three retained rows; completion then refuses another append and does not change that count.
     */
    @Test
    void activeMemberAndOwningMentorAppendCommentsUntilProjectCompletion() {
        TaskView task = taskService.create(
                "member@example.test",
                new CreateTaskCommand(projectId, memberMembershipId, "Discuss results", null, null));
        insertIntern("outsider@example.test");

        TaskCommentView memberComment = taskService.addComment(
                "member@example.test", projectId, task.id(), "  First note  ");
        TaskCommentView mentorComment = taskService.addComment(
                "mentor@example.test", projectId, task.id(), "Mentor note");
        TaskCommentView leaderComment = taskService.addComment(
                "leader@example.test", projectId, task.id(), "Leader note");

        TaskView leaderTask = taskService.create(
                "leader@example.test",
                new CreateTaskCommand(projectId, leaderMembershipId, "Leader task", null, null));
        taskService.addComment("member@example.test", projectId, leaderTask.id(), "Member on leader task");

        assertThat(memberComment.body()).isEqualTo("First note");
        assertThat(mentorComment.authorUserId()).isEqualTo(userId("mentor@example.test"));
        assertThat(leaderComment.authorUserId()).isEqualTo(userId("leader@example.test"));
        assertThatThrownBy(() -> taskService.addComment(
                        "outsider@example.test", projectId, task.id(), "Forbidden"))
                .isInstanceOf(TaskNotFoundException.class);
        assertThatThrownBy(() -> taskService.addComment(
                        "member@example.test", projectId, task.id(), "  "))
                .isInstanceOf(TaskValidationException.class);

        completeProject();
        assertThatThrownBy(() -> taskService.addComment(
                        "mentor@example.test", projectId, task.id(), "Too late"))
                .isInstanceOf(TaskNotFoundException.class);
        assertThat(commentCount()).isEqualTo(4);
        assertThat(notificationRecipientIds()).containsExactly(
                userId("leader@example.test"),
                userId("member@example.test"),
                userId("leader@example.test"),
                userId("member@example.test"),
                userId("leader@example.test"));
        assertThat(notificationTypes()).containsExactly(
                "TASK_COMMENTED", "TASK_COMMENTED", "TASK_COMMENTED", "TASK_COMMENTED", "TASK_COMMENTED");
        assertThat(notificationEmailStatuses()).containsExactly(
                "NOT_REQUIRED", "NOT_REQUIRED", "NOT_REQUIRED", "NOT_REQUIRED", "NOT_REQUIRED");
        assertThat(notificationActionUrls()).containsExactly(
                "/projects/%d/tasks/%d".formatted(projectId, task.id()),
                "/projects/%d/tasks/%d".formatted(projectId, task.id()),
                "/projects/%d/tasks/%d".formatted(projectId, task.id()),
                "/projects/%d/tasks/%d".formatted(projectId, task.id()),
                "/projects/%d/tasks/%d".formatted(projectId, leaderTask.id()));
    }

    /**
     * TSK-011 and AC-TSK-006 require comments to be append-only: edit/delete comment routes are
     * absent or refuse mutation. The current route contract exposes only POST to the collection,
     * so requests to edit or delete comment 1 must be 404 and leave its single retained row intact.
     */
    @Test
    void commentsHaveNoEditOrDeleteRoutes() throws Exception {
        TaskView task = createMemberTask("Append-only route");
        taskService.addComment("member@example.test", projectId, task.id(), "Keep this note");

        mockMvc.perform(post("/projects/{projectId}/tasks/{taskId}/comments/1/edit", projectId, task.id())
                        .with(user("member@example.test").roles("INTERN"))
                        .with(csrf())
                        .param("body", "Replace note"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/projects/{projectId}/tasks/{taskId}/comments/1/delete", projectId, task.id())
                        .with(user("member@example.test").roles("INTERN"))
                        .with(csrf()))
                .andExpect(status().isNotFound());

        assertThat(commentCount()).isEqualTo(1);
        assertThat(taskService.details("member@example.test", projectId, task.id()).comments())
                .extracting(TaskCommentView::body).containsExactly("Keep this note");
    }

    @Test
    void authorizedMentorCanCommentOnDoneTaskRetainingClosedAssigneeHistory() {
        TaskView task = createMemberTask("Closed assignee history");
        setStatus(task.id(), TaskStatus.DONE);
        closeMembership(memberMembershipId);

        TaskCommentView comment = taskService.addComment(
                "mentor@example.test", projectId, task.id(), "Mentor note after removal");

        assertThat(comment.body()).isEqualTo("Mentor note after removal");
        assertThat(commentCount()).isEqualTo(1);
        assertThat(notificationRecipientIds()).containsExactly(userId("leader@example.test"));
        assertThat(notificationTypes()).containsExactly("TASK_COMMENTED");
        assertThat(notificationEmailStatuses()).containsExactly("NOT_REQUIRED");
        assertThat(notificationActionUrls()).containsExactly(
                "/projects/%d/tasks/%d".formatted(projectId, task.id()));
    }

    @Test
    void authorizedListsAndDetailsExcludeDeletedTasksAndReportEmptyAsNotApplicable() {
        TaskView todo = createMemberTask("Todo");
        TaskView active = createMemberTask("Active");
        TaskView blocked = createMemberTask("Blocked");
        TaskView done = createMemberTask("Done");
        TaskView deleted = createMemberTask("Deleted");
        setStatus(active.id(), TaskStatus.IN_PROGRESS);
        setStatus(blocked.id(), TaskStatus.BLOCKED);
        setStatus(done.id(), TaskStatus.DONE);
        softDelete(deleted.id());
        taskService.addComment("member@example.test", projectId, todo.id(), "Visible comment");

        TaskListView list = taskService.list("member@example.test", projectId);
        TaskDetails details = taskService.details("mentor@example.test", projectId, todo.id());

        assertThat(list.tasks()).extracting(TaskView::title)
                .containsExactly("Todo", "Active", "Blocked", "Done");
        assertThat(list.tasks()).extracting(TaskView::assigneeName)
                .containsOnly("member@example.test");
        assertThat(list.canCreate()).isTrue();
        assertThat(list.progress().total()).isEqualTo(4);
        assertThat(list.progress().count(TaskStatus.TODO)).isEqualTo(1);
        assertThat(list.progress().count(TaskStatus.IN_PROGRESS)).isEqualTo(1);
        assertThat(list.progress().count(TaskStatus.BLOCKED)).isEqualTo(1);
        assertThat(list.progress().count(TaskStatus.DONE)).isEqualTo(1);
        assertThat(list.progress().completionPercentage()).hasValue(25.0);
        assertThat(details.comments()).extracting(TaskCommentView::body).containsExactly("Visible comment");
        assertThat(details.task().assigneeName()).isEqualTo("member@example.test");
        assertThat(details.canChangeStatus()).isFalse();
        assertThat(details.canComment()).isTrue();

        long emptyProjectId = insertProject(userId("mentor@example.test"), "PLANNED");
        long emptyLeaderMembershipId = insertMembership(
                emptyProjectId,
                userId("leader@example.test"),
                userId("mentor@example.test"));
        jdbc.sql("""
                        insert into project_leadership_terms
                            (project_id, membership_id, appointed_by_mentor_user_id)
                        values (:projectId, :membershipId, :mentorId)
                        """)
                .param("projectId", emptyProjectId)
                .param("membershipId", emptyLeaderMembershipId)
                .param("mentorId", userId("mentor@example.test"))
                .update();
        assertThat(taskService.list("mentor@example.test", emptyProjectId).progress().completionPercentage())
                .isEmpty();
    }

    @Test
    void directAndCrossProjectTaskIdentifiersDoNotDiscloseRecords() {
        TaskView task = createMemberTask("Private task");
        long otherProjectId = insertProject(userId("mentor@example.test"), "PLANNED");

        assertThatThrownBy(() -> taskService.details(
                        "mentor@example.test", otherProjectId, task.id()))
                .isInstanceOf(TaskNotFoundException.class);
        assertThatThrownBy(() -> taskService.details(
                        "outsider@example.test", projectId, task.id()))
                .isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    void formerMemberReadsOnlyCompletedProjectTaskHistory() {
        TaskView task = createMemberTask("Historical task");
        closeMembership(memberMembershipId);

        assertThatThrownBy(() -> taskService.list("member@example.test", projectId))
                .isInstanceOf(TaskNotFoundException.class);
        assertThatThrownBy(() -> taskService.details("member@example.test", projectId, task.id()))
                .isInstanceOf(TaskNotFoundException.class);

        completeProject();

        assertThat(currentLeadershipCount()).isZero();
        assertThat(currentMembershipCount()).isZero();

        assertThat(taskService.list("member@example.test", projectId).tasks())
                .extracting(TaskView::title)
                .containsExactly("Historical task");
        assertThat(taskService.details("member@example.test", projectId, task.id()).task().title())
                .isEqualTo("Historical task");
    }

    @Test
    void viewCapabilitiesFollowCurrentMembershipAssignmentAndProjectLifecycle() {
        TaskView task = createMemberTask("Capability task");

        assertThat(taskService.list("member@example.test", projectId).canCreate()).isTrue();
        assertThat(taskService.list("mentor@example.test", projectId).canCreate()).isFalse();
        assertThat(taskService.details("member@example.test", projectId, task.id()))
                .satisfies(details -> {
                    assertThat(details.canChangeStatus()).isFalse();
                    assertThat(details.canComment()).isTrue();
                });

        activateProject();

        assertThat(taskService.details("member@example.test", projectId, task.id()))
                .satisfies(details -> {
                    assertThat(details.canChangeStatus()).isTrue();
                    assertThat(details.canComment()).isTrue();
                });
        assertThat(taskService.details("mentor@example.test", projectId, task.id()).canChangeStatus())
                .isTrue();
        assertThat(taskService.details("leader@example.test", projectId, task.id()).canChangeStatus())
                .isFalse();

        completeProject();

        assertThat(taskService.list("member@example.test", projectId).canCreate()).isFalse();
        assertThat(taskService.details("member@example.test", projectId, task.id()))
                .satisfies(details -> {
                    assertThat(details.canChangeStatus()).isFalse();
                    assertThat(details.canComment()).isFalse();
                });
    }

    @Test
    void createFormChoicesAreSelfOnlyForMembersAndAllActiveMembersForLeader() {
        assertThat(taskService.assignmentChoices("member@example.test", projectId))
                .extracting(TaskAssigneeChoice::membershipId)
                .containsExactly(memberMembershipId);
        assertThat(taskService.assignmentChoices("leader@example.test", projectId))
                .extracting(TaskAssigneeChoice::membershipId)
                .containsExactly(leaderMembershipId, memberMembershipId);
    }

    @Test
    void projectActivationQueryCountsOnlyCurrentTasksOutsideActiveMemberships() {
        createMemberTask("Member task");
        TaskView leaderTask = taskService.create(
                "leader@example.test",
                new CreateTaskCommand(projectId, leaderMembershipId, "Leader task", null, null));

        assertThat(taskQueries.countCurrentTasksAssignedOutside(projectId, Set.of(memberMembershipId)))
                .isEqualTo(1L);

        softDelete(leaderTask.id());
        assertThat(taskQueries.countCurrentTasksAssignedOutside(projectId, Set.of(memberMembershipId)))
                .isZero();
    }

    /**
     * TSK-004, TSK-009, TSK-010, TSK-019 and AC-TSK-011 require creator attribution, status,
     * comments, work logs, and current assignment actor/time to remain truthful after reassignment
     * away and back. The hand-derived final facts are the original creator, IN_PROGRESS status,
     * one original comment, one 45-minute work log, and the current Leader as assignment actor;
     * creator edit/delete capability is absent away from and restored on return to the creator.
     */
    @Test
    void creatorMayEditOnlyWhileStillCurrentAssigneeAndLeaderMayEditAnyUnfinishedTask() {
        TaskView task = createMemberTask("Original");
        setStatus(task.id(), TaskStatus.IN_PROGRESS);
        Instant workLoggedAt = Instant.parse("2026-08-20T02:00:00Z");
        taskWorkLogs.saveAndFlush(new TaskWorkLog(
                projectId, task.id(), memberMembershipId, LocalDate.of(2026, 8, 20),
                45, "Initial work", workLoggedAt));
        taskService.addComment("member@example.test", projectId, task.id(), "Retain across transfer");
        TaskDetails initialDetails = taskService.details("member@example.test", projectId, task.id());
        assertThat(initialDetails.canEdit()).isTrue();
        assertThat(initialDetails.canDelete()).isTrue();

        TaskView edited = taskService.edit(
                "member@example.test", projectId, task.id(), "Updated", "Details", PROJECT_END);

        assertThat(edited.title()).isEqualTo("Updated");
        assertThat(edited.description()).isEqualTo("Details");
        assertThat(edited.dueDate()).isEqualTo(PROJECT_END);

        TaskView reassignedAway = taskService.reassign(
                "leader@example.test", projectId, task.id(), null, leaderMembershipId,
                new RemainingEffortForecastInput(90, "First handover"));
        assertThat(reassignedAway.assignerMembershipId()).isEqualTo(leaderMembershipId);
        assertThat(reassignedAway.assignedAt()).isEqualTo(task.assignedAt());
        assertThatThrownBy(() -> taskService.edit(
                        "member@example.test", projectId, task.id(), "Denied", null, null))
                .isInstanceOf(TaskNotFoundException.class);
        TaskDetails afterAway = taskService.details("member@example.test", projectId, task.id());
        assertThat(afterAway.canEdit()).isFalse();
        assertThat(afterAway.canDelete()).isFalse();

        TaskView reassignedBack = taskService.reassign(
                "leader@example.test", projectId, task.id(), null, memberMembershipId,
                new RemainingEffortForecastInput(60, "Return handover"));
        assertThat(reassignedBack.assigneeMembershipId()).isEqualTo(memberMembershipId);
        assertThat(reassignedBack.assignerMembershipId()).isEqualTo(leaderMembershipId);
        assertThat(reassignedBack.assignedAt()).isEqualTo(reassignedAway.assignedAt());
        TaskDetails afterBack = taskService.details("member@example.test", projectId, task.id());
        assertThat(afterBack.canEdit()).isTrue();
        assertThat(afterBack.canDelete()).isTrue();

        TaskView editedBack = taskService.edit(
                "member@example.test", projectId, task.id(), "Creator edit restored", null, null);
        assertThat(editedBack.title()).isEqualTo("Creator edit restored");
        taskService.softDelete("member@example.test", projectId, task.id());

        Instant persistedAssignmentAt = jdbc.sql("select assigned_at from tasks where id = :id")
                .param("id", task.id())
                .query(Instant.class)
                .single();
        TaskHistoryView retained = taskQueries.history(projectId).stream()
                .filter(history -> history.id() == task.id())
                .findFirst()
                .orElseThrow();
        assertThat(retained.assigneeMembershipId()).isEqualTo(memberMembershipId);
        assertThat(retained.assignerMembershipId()).isEqualTo(leaderMembershipId);
        assertThat(retained.assignedAt()).isEqualTo(reassignedBack.assignedAt());
        assertThat(retained.assignedAt()).isEqualTo(persistedAssignmentAt);
        assertThat(retained.deletedByMembershipId()).isEqualTo(memberMembershipId);
        assertThat(retained.creatorMembershipId()).isEqualTo(memberMembershipId);
        assertThat(retained.status()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(retained.comments()).extracting(TaskCommentView::body)
                .containsExactly("Retain across transfer");
        assertThat(retained.workLogs()).hasSize(1);
        assertThat(retained.workLogs().getFirst().minutes()).isEqualTo(45);
        assertThat(retained.workLogs().getFirst().note()).isEqualTo("Initial work");
    }

    @Test
    void reassignmentNotifiesPreviousAndNewAssigneeExactlyOnce() {
        TaskView task = createMemberTask("Reassignment notice");

        taskService.reassign("leader@example.test", projectId, task.id(), leaderMembershipId);

        assertThat(notificationRecipientIds()).containsExactly(
                userId("member@example.test"), userId("leader@example.test"));
        assertThat(notificationTypes()).containsExactly("TASK_REASSIGNED", "TASK_REASSIGNED");
        assertThat(notificationEmailStatuses()).containsExactly("UNAVAILABLE", "UNAVAILABLE");
        assertThat(notificationActionUrls()).containsExactly(
                "/projects/%d/tasks/%d".formatted(projectId, task.id()),
                "/projects/%d/tasks/%d".formatted(projectId, task.id()));
    }

    /**
     * TSK-010 and AC-TSK-005 require a soft-deleted Task to leave normal lists and progress while
     * remaining as one physical row in authorized Project History with deletion actor and time.
     * One initial Task therefore yields zero current Tasks, one retained row, and the Leader
     * membership plus the exact stored deletion instant in the owning Mentor's history view.
     */
    @Test
    void softDeleteExcludesTaskFromCurrentViewsButRetainsHistoricalRow() {
        TaskView task = createMemberTask("Retain me");
        Instant createdBeforeDelete = task.createdAt();

        taskService.softDelete("leader@example.test", projectId, task.id());

        assertThat(taskService.list("member@example.test", projectId).tasks()).isEmpty();
        assertThat(taskQueries.projectProgress(projectId).totalTasks()).isZero();
        Instant deletedAt = jdbc.sql("select deleted_at from tasks where id = :id")
                .param("id", task.id()).query(Instant.class).single();
        Long deletedBy = jdbc.sql("select deleted_by_membership_id from tasks where id = :id")
                .param("id", task.id()).query(Long.class).single();
        assertThat(deletedAt).isNotNull().isAfterOrEqualTo(createdBeforeDelete);
        assertThat(deletedBy).isEqualTo(leaderMembershipId);
        assertThat(jdbc.sql("select count(*) from tasks where id = :id")
                .param("id", task.id()).query(Long.class).single()).isEqualTo(1L);
        assertThatThrownBy(() -> taskService.details("member@example.test", projectId, task.id()))
                .isInstanceOf(TaskNotFoundException.class);
        assertThat(projectQueries.history(userId("mentor@example.test"), projectId).tasks())
                .filteredOn(history -> history.id() == task.id())
                .singleElement()
                .satisfies(history -> {
                    assertThat(history.deletedAt()).isEqualTo(deletedAt);
                    assertThat(history.deletedByMembershipId()).isEqualTo(leaderMembershipId);
                });
        assertThat(projectQueries.history(userId("mentor@example.test"), projectId)
                        .usernamesByMembershipId())
                .containsKey(leaderMembershipId);
    }

    @Test
    void unfinishedReassignmentPreservesCreatorStatusAndCreationAtAndDoneRequiresReopen() {
        TaskView task = createMemberTask("Transfer me");
        setStatus(task.id(), TaskStatus.IN_PROGRESS);
        Instant createdAt = task.createdAt();

        TaskView reassigned = taskService.reassign(
                "leader@example.test", projectId, task.id(), leaderMembershipId);

        assertThat(reassigned.assigneeMembershipId()).isEqualTo(leaderMembershipId);
        assertThat(reassigned.creatorMembershipId()).isEqualTo(memberMembershipId);
        assertThat(reassigned.status()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(reassigned.createdAt()).isEqualTo(createdAt);

        setStatus(task.id(), TaskStatus.DONE);
        assertThatThrownBy(() -> taskService.reassign(
                        "leader@example.test", projectId, task.id(), memberMembershipId))
                .isInstanceOf(TaskValidationException.class);
    }

    @Test
    void projectProgressAndHistoryReadPersistedWorkAndRetainedDeletedRows() {
        TaskView deleted = createMemberTask("Deleted effort");
        taskService.softDelete("member@example.test", projectId, deleted.id());
        TaskView done = createMemberTask("Done effort");
        setStatus(done.id(), TaskStatus.DONE);
        TaskView retainedCurrent = createMemberTask("Retained current effort");
        taskService.addComment("member@example.test", projectId, retainedCurrent.id(), "Retained note");

        taskWorkLogs.saveAndFlush(new TaskWorkLog(
                projectId,
                deleted.id(),
                memberMembershipId,
                LocalDate.of(2026, 8, 20),
                60,
                "Deleted effort",
                Instant.parse("2026-08-20T01:00:00Z")));
        taskWorkLogs.saveAndFlush(new TaskWorkLog(
                projectId,
                retainedCurrent.id(),
                memberMembershipId,
                LocalDate.of(2026, 8, 20),
                120,
                "Current effort",
                Instant.parse("2026-08-20T02:00:00Z")));

        TaskProjectProgress progress = taskQueries.projectProgress(projectId);
        assertThat(progress.todo()).isEqualTo(1L);
        assertThat(progress.done()).isEqualTo(1L);
        assertThat(progress.totalTasks()).isEqualTo(2L);
        assertThat(progress.totalMinutes()).isEqualTo(180L);
        assertThat(progress.completionPercentage()).hasValue(50.0);

        List<TaskHistoryView> history = taskQueries.history(projectId);
        assertThat(history).extracting(TaskHistoryView::title)
                .containsExactly("Deleted effort", "Done effort", "Retained current effort");
        assertThat(history.get(0).deletedAt()).isNotNull();
        assertThat(history.get(0).workLogs()).hasSize(1);
        assertThat(history.get(2).comments()).extracting(TaskCommentView::body)
                .containsExactly("Retained note");
    }

    @Test
    void projectProgressAggregateReturnsEmptyDenominatorForNoCurrentTasks() {
        TaskProjectProgress progress = taskQueries.projectProgress(projectId);

        assertThat(progress.todo()).isZero();
        assertThat(progress.inProgress()).isZero();
        assertThat(progress.blocked()).isZero();
        assertThat(progress.done()).isZero();
        assertThat(progress.totalMinutes()).isZero();
        assertThat(progress.completionPercentage()).isEmpty();
    }

    @Test
    void dailyReportReadsPostgresRetainedDeletedLogsAndLatestForecastSnapshot() {
        TaskView worked = taskService.create("leader@example.test", new CreateTaskCommand(
                projectId, leaderMembershipId, "Daily retained work", "", null));
        activateProject();
        taskService.addWorkLog("leader@example.test", projectId, worked.id(),
                LocalDate.of(2026, 8, 14), 45, "former author");
        TaskView current = taskService.list("leader@example.test", projectId).tasks().stream()
                .filter(task -> task.id() == worked.id()).findFirst().orElseThrow();
        TaskView reassigned = taskService.reassign(
                "leader@example.test", projectId, worked.id(), current.version(), memberMembershipId,
                new RemainingEffortForecastInput(90, "handover forecast"));
        taskService.addWorkLog("member@example.test", projectId, worked.id(),
                LocalDate.of(2026, 8, 14), 15, "incoming author");
        softDelete(worked.id());

        List<TaskDailyReportView> report = taskQueries.dailyReport(
                projectId, LocalDate.of(2026, 8, 14));

        assertThat(report).singleElement().satisfies(task -> {
            assertThat(task.id()).isEqualTo(worked.id());
            assertThat(task.assigneeMembershipId()).isEqualTo(memberMembershipId);
            assertThat(task.deleted()).isTrue();
            assertThat(task.lifetimeActualMinutes()).isEqualTo(60L);
            assertThat(task.workLogs()).extracting(log -> log.membershipId())
                    .containsExactly(leaderMembershipId, memberMembershipId);
            assertThat(task.latestForecast()).isNotNull();
            assertThat(task.latestForecast().remainingMinutes()).isEqualTo(90);
            assertThat(task.latestForecast().actualMinutesSnapshot()).isEqualTo(45L);
            assertThat(task.latestForecast().forecastTotalMinutes()).isEqualTo(135L);
        });
        assertThat(reassigned.assigneeMembershipId()).isEqualTo(memberMembershipId);

        var htmlDataset = dailyReports.build(
                "mentor@example.test", null, LocalDate.of(2026, 8, 14));
        assertThat(htmlDataset.projects()).hasSize(1);
        assertThat(htmlDataset.projects().getFirst().totalMinutes()).isEqualTo(60L);
        assertThat(htmlDataset.projects().getFirst().members())
                .extracting(member -> member.membershipId())
                .containsExactly(leaderMembershipId, memberMembershipId);
        assertThat(htmlDataset.overallTotalMinutes()).isEqualTo(60L);
    }

    @Test
    void leaderEstimateIsVisibleAndLifetimeVarianceIsDerivedFromRetainedLogs() {
        TaskView task = taskService.create("leader@example.test", new CreateTaskCommand(
                projectId, memberMembershipId, "Estimated task", "", null, 120));
        assertThat(taskService.details("member@example.test", projectId, task.id()).effortPlanning())
                .extracting(TaskEffortPlanningView::estimatedMinutes,
                        TaskEffortPlanningView::actualMinutes,
                        TaskEffortPlanningView::varianceState)
                .containsExactly(120, 0L, TaskVarianceState.PENDING);

        activateProject();
        taskService.addWorkLog("member@example.test", projectId, task.id(),
                LocalDate.of(2026, 8, 14), 150, "Lifetime effort");
        TaskView current = taskService.list("leader@example.test", projectId).tasks().stream()
                .filter(candidate -> candidate.id() == task.id()).findFirst().orElseThrow();
        assertThatThrownBy(() -> taskService.estimate(
                        "leader@example.test", projectId, task.id(), current.version(), 200))
                .isInstanceOf(TaskValidationException.class)
                .hasMessageContaining("cannot change after work");
        setStatus(task.id(), TaskStatus.DONE);
        TaskEffortPlanningView planning = taskService.details(
                "leader@example.test", projectId, task.id()).effortPlanning();
        assertThat(planning.estimatedMinutes()).isEqualTo(120);
        assertThat(planning.actualMinutes()).isEqualTo(150);
        assertThat(planning.varianceState()).isEqualTo(TaskVarianceState.VALUE);
        assertThat(planning.varianceMinutes()).isEqualTo(30L);
        assertThat(planning.canEditEstimate()).isFalse();
        setStatus(task.id(), TaskStatus.IN_PROGRESS);
        assertThat(taskService.details("leader@example.test", projectId, task.id())
                .effortPlanning().varianceState()).isEqualTo(TaskVarianceState.PENDING);
    }

    @Test
    void estimateBoundsAndMemberForgeryAreRejectedWhileUnestimatedTaskIsNADisplay() {
        TaskView minimum = taskService.create("leader@example.test", new CreateTaskCommand(
                projectId, memberMembershipId, "Minimum", "", null, 1));
        assertThat(taskService.details("leader@example.test", projectId, minimum.id())
                .effortPlanning().estimatedMinutes()).isEqualTo(1);
        TaskView maximum = taskService.create("leader@example.test", new CreateTaskCommand(
                projectId, memberMembershipId, "Maximum", "", null, 527040));
        assertThat(taskService.details("leader@example.test", projectId, maximum.id())
                .effortPlanning().estimatedMinutes()).isEqualTo(527040);
        assertThatThrownBy(() -> taskService.create("leader@example.test", new CreateTaskCommand(
                        projectId, memberMembershipId, "Too small", "", null, 0)))
                .isInstanceOf(TaskValidationException.class);
        assertThatThrownBy(() -> taskService.create("leader@example.test", new CreateTaskCommand(
                        projectId, memberMembershipId, "Too large", "", null, 527041)))
                .isInstanceOf(TaskValidationException.class);
        assertThatThrownBy(() -> taskService.create("member@example.test", new CreateTaskCommand(
                        projectId, memberMembershipId, "Forged", "", null, 120)))
                .isInstanceOf(TaskNotFoundException.class);
        TaskView unestimated = createMemberTask("No estimate");
        assertThat(taskService.details("member@example.test", projectId, unestimated.id())
                .effortPlanning().varianceState()).isEqualTo(TaskVarianceState.NOT_ESTIMATED);
    }

    @Test
    void leaderCanChangeAndClearEstimateBeforeWork() {
        TaskView task = taskService.create("leader@example.test", new CreateTaskCommand(
                projectId, memberMembershipId, "Editable estimate", "", null, 120));
        TaskView changed = taskService.estimate("leader@example.test", projectId, task.id(), task.version(), 240);
        assertThat(changed).isNotNull();
        TaskView current = taskService.list("leader@example.test", projectId).tasks().stream()
                .filter(candidate -> candidate.id() == task.id()).findFirst().orElseThrow();
        taskService.estimate("leader@example.test", projectId, task.id(), current.version(), null);
        assertThat(taskService.details("leader@example.test", projectId, task.id())
                .effortPlanning().varianceState()).isEqualTo(TaskVarianceState.NOT_ESTIMATED);
    }

    @Test
    void workedReassignmentPersistsForecastProvenanceAndSnapshot() {
        TaskView task = taskService.create("leader@example.test", new CreateTaskCommand(
                projectId, leaderMembershipId, "Worked transfer", "", null, 120));
        activateProject();
        taskService.addWorkLog("leader@example.test", projectId, task.id(),
                LocalDate.of(2026, 8, 14), 135, "Author A");
        TaskView current = taskService.list("leader@example.test", projectId).tasks().stream()
                .filter(candidate -> candidate.id() == task.id()).findFirst().orElseThrow();

        taskService.reassign("leader@example.test", projectId, task.id(), current.version(),
                memberMembershipId, new RemainingEffortForecastInput(90, "  next phase  "));

        TaskRemainingEffortForecast forecast = forecasts
                .findAllByProjectIdAndTaskIdOrderByAssignmentStartedAtAscCreatedAtAscIdAsc(projectId, task.id())
                .stream().findFirst().orElseThrow();
        assertThat(forecast.getProjectId()).isEqualTo(projectId);
        assertThat(forecast.getTaskId()).isEqualTo(task.id());
        assertThat(forecast.getIncomingMembershipId()).isEqualTo(memberMembershipId);
        assertThat(forecast.getForecastingLeaderMembershipId()).isEqualTo(leaderMembershipId);
        assertThat(forecast.getRemainingMinutes()).isEqualTo(90);
        assertThat(forecast.getActualMinutesSnapshot()).isEqualTo(135);
        assertThat(forecast.getInitialNote()).isEqualTo("next phase");
        assertThat(forecast.getAssignmentStartedAt()).isEqualTo(forecast.getCreatedAt());
        TaskDetails reassigned = taskService.details("leader@example.test", projectId, task.id());
        assertThat(reassigned.task().assigneeMembershipId()).isEqualTo(memberMembershipId);
        assertThat(reassigned.task().assignedAt()).isEqualTo(forecast.getAssignmentStartedAt());
    }

    @Test
    void workedReassignmentAcceptsInclusiveForecastMinuteBounds() {
        List<TaskView> tasks = List.of(
                taskService.create("leader@example.test", new CreateTaskCommand(
                        projectId, memberMembershipId, "Bound 1", "", null, 120)),
                taskService.create("leader@example.test", new CreateTaskCommand(
                        projectId, memberMembershipId, "Bound 527040", "", null, 120)));
        activateProject();
        int index = 0;
        for (int minutes : List.of(1, 527040)) {
            TaskView task = tasks.get(index++);
            taskService.addWorkLog("member@example.test", projectId, task.id(),
                    LocalDate.of(2026, 8, 14), 10, "Effort");
            TaskView current = taskService.list("leader@example.test", projectId).tasks().stream()
                    .filter(candidate -> candidate.id() == task.id()).findFirst().orElseThrow();
            taskService.reassign("leader@example.test", projectId, task.id(), current.version(),
                    leaderMembershipId, new RemainingEffortForecastInput(minutes, null));
            assertThat(forecasts.findAllByProjectIdAndTaskIdOrderByAssignmentStartedAtAscCreatedAtAscIdAsc(
                    projectId, task.id()).get(0).getRemainingMinutes()).isEqualTo(minutes);
        }
    }

    @Test
    void unsolicitedForecastOnUnworkedReassignmentDoesNotMutateState() {
        TaskView task = taskService.create("leader@example.test", new CreateTaskCommand(
                projectId, memberMembershipId, "Unworked transfer", "", null));
        long beforeTasks = taskCount();
        long beforeForecasts = forecasts.count();
        assertThatThrownBy(() -> taskService.reassign("leader@example.test", projectId, task.id(),
                task.version(), leaderMembershipId, new RemainingEffortForecastInput(90, "not needed")))
                .isInstanceOf(TaskValidationException.class);
        assertThat(taskService.list("leader@example.test", projectId).tasks()).extracting(TaskView::id)
                .contains(task.id());
        assertThat(taskCount()).isEqualTo(beforeTasks);
        assertThat(forecasts.count()).isEqualTo(beforeForecasts);
        assertThat(taskService.details("leader@example.test", projectId, task.id())
                .remainingEffortForecasts()).isEmpty();
    }

    @Test
    void multiAuthorLifetimeActualAndOriginalEstimateSurviveWorkedReassignment() {
        TaskView task = taskService.create("leader@example.test", new CreateTaskCommand(
                projectId, memberMembershipId, "Two authors", "", null, 120));
        activateProject();
        taskService.addWorkLog("member@example.test", projectId, task.id(),
                LocalDate.of(2026, 8, 14), 80, "Author A");
        TaskView current = taskService.list("leader@example.test", projectId).tasks().stream()
                .filter(candidate -> candidate.id() == task.id()).findFirst().orElseThrow();
        taskService.reassign("leader@example.test", projectId, task.id(), current.version(),
                leaderMembershipId, new RemainingEffortForecastInput(70, null));
        TaskView afterReassign = taskService.list("leader@example.test", projectId).tasks().stream()
                .filter(candidate -> candidate.id() == task.id()).findFirst().orElseThrow();
        taskService.addWorkLog("leader@example.test", projectId, task.id(),
                LocalDate.of(2026, 8, 14), 70, "Author B");
        setStatus(task.id(), TaskStatus.DONE);

        TaskDetails details = taskService.details("leader@example.test", projectId, task.id());
        assertThat(details.effortPlanning().estimatedMinutes()).isEqualTo(120);
        assertThat(details.effortPlanning().actualMinutes()).isEqualTo(150);
        assertThat(details.effortPlanning().varianceState()).isEqualTo(TaskVarianceState.VALUE);
        assertThat(details.effortPlanning().varianceMinutes()).isEqualTo(30L);
        assertThat(details.remainingEffortForecasts()).singleElement()
                .extracting(TaskRemainingEffortForecastView::actualMinutesSnapshot)
                .isEqualTo(80L);
        assertThat(afterReassign.assigneeMembershipId()).isEqualTo(leaderMembershipId);
    }

    @Test
    void directTransferAllUnfinishedUsesOrderedIdProjectionAndLeavesDoneTasksUntouched() {
        TaskView first = createMemberTask("First unfinished");
        TaskView second = createMemberTask("Second unfinished");
        TaskView done = createMemberTask("Done retained");
        setStatus(done.id(), TaskStatus.DONE);

        ProjectTaskContext context = projectMutations.taskMutationContext(
                userId("leader@example.test"), projectId);
        TaskTransferResult result = taskTransfers.transferAllUnfinished(
                context, leaderMembershipId, memberMembershipId, leaderMembershipId);

        assertThat(result.transferredTaskCount()).isEqualTo(2L);
        assertThat(result.recipientMembershipId()).isEqualTo(leaderMembershipId);
        assertThat(jdbc.sql("""
                        select count(*) from tasks
                        where project_id = :projectId
                          and assignee_membership_id = :leaderMembershipId
                          and status <> 'DONE' and deleted_at is null
                        """)
                .param("projectId", projectId)
                .param("leaderMembershipId", leaderMembershipId)
                .query(Long.class)
                .single()).isEqualTo(2L);
        assertThat(jdbc.sql("select assignee_membership_id from tasks where id = :id")
                .param("id", done.id())
                .query(Long.class)
                .single()).isEqualTo(memberMembershipId);
        assertThat(jdbc.sql("select count(*) from tasks where id in (:first, :second)")
                .param("first", first.id())
                .param("second", second.id())
                .query(Long.class)
                .single()).isEqualTo(2L);
        assertThat(notificationRecipientIds()).containsExactly(
                userId("member@example.test"), userId("leader@example.test"),
                userId("member@example.test"), userId("leader@example.test"));
        assertThat(notificationTypes()).containsExactly(
                "TASK_REASSIGNED", "TASK_REASSIGNED", "TASK_REASSIGNED", "TASK_REASSIGNED");
        assertThat(notificationEmailStatuses()).containsExactly(
                "UNAVAILABLE", "UNAVAILABLE", "UNAVAILABLE", "UNAVAILABLE");
        assertThat(notificationActionUrls()).containsExactly(
                "/projects/%d/tasks/%d".formatted(projectId, first.id()),
                "/projects/%d/tasks/%d".formatted(projectId, first.id()),
                "/projects/%d/tasks/%d".formatted(projectId, second.id()),
                "/projects/%d/tasks/%d".formatted(projectId, second.id()));
    }

    @Test
    void workedBatchTransferRequiresAForecastBeforeChangingAnyTask() {
        TaskView task = createMemberTask("Worked batch transfer");
        activateProject();
        taskService.addWorkLog("member@example.test", projectId, task.id(),
                LocalDate.of(2026, 8, 14), 45, "Existing effort");
        ProjectTaskContext context = projectMutations.taskMutationContext(
                userId("leader@example.test"), projectId);
        long notificationsBefore = jdbc.sql("select count(*) from notifications")
                .query(Long.class)
                .single();

        assertThatThrownBy(() -> taskTransfers.transferBatch(
                context,
                leaderMembershipId,
                memberMembershipId,
                Set.of(task.id()),
                leaderMembershipId))
                .isInstanceOf(TaskValidationException.class)
                .hasMessageContaining("forecast");

        assertThat(taskService.details("leader@example.test", projectId, task.id())
                .task().assigneeMembershipId()).isEqualTo(memberMembershipId);
        assertThat(forecasts.findAllByProjectIdAndTaskIdOrderByAssignmentStartedAtAscCreatedAtAscIdAsc(
                projectId, task.id())).isEmpty();
        assertThat(jdbc.sql("select count(*) from notifications")
                .query(Long.class)
                .single()).isEqualTo(notificationsBefore);
    }

    @Test
    void mixedBatchTransferPersistsOneWorkedForecastAndLeavesUnworkedHistoryEmpty() {
        TaskView worked = createMemberTask("Worked mixed batch");
        TaskView unworked = createMemberTask("Unworked mixed batch");
        activateProject();
        taskService.addWorkLog("member@example.test", projectId, worked.id(),
                LocalDate.of(2026, 8, 14), 45, "Existing effort");
        TaskView currentWorked = taskService.list("leader@example.test", projectId).tasks().stream()
                .filter(candidate -> candidate.id() == worked.id()).findFirst().orElseThrow();
        TaskView currentUnworked = taskService.list("leader@example.test", projectId).tasks().stream()
                .filter(candidate -> candidate.id() == unworked.id()).findFirst().orElseThrow();
        ProjectTaskContext context = projectMutations.taskMutationContext(
                userId("leader@example.test"), projectId);
        long notificationsBefore = notificationCount();

        TaskTransferResult result = taskTransfers.transferBatch(
                context,
                leaderMembershipId,
                memberMembershipId,
                Set.of(worked.id(), unworked.id()),
                Map.of(worked.id(), currentWorked.version(), unworked.id(), currentUnworked.version()),
                Map.of(worked.id(), new RemainingEffortForecastInput(90, "  next phase  ")),
                leaderMembershipId);

        assertThat(result.transferredTaskCount()).isEqualTo(2L);
        assertThat(result.recipientMembershipId()).isEqualTo(leaderMembershipId);
        assertThat(taskService.details("leader@example.test", projectId, worked.id())
                .task().assigneeMembershipId()).isEqualTo(leaderMembershipId);
        assertThat(taskService.details("leader@example.test", projectId, unworked.id())
                .task().assigneeMembershipId()).isEqualTo(leaderMembershipId);
        TaskRemainingEffortForecast forecast = forecasts
                .findAllByProjectIdAndTaskIdOrderByAssignmentStartedAtAscCreatedAtAscIdAsc(
                        projectId, worked.id())
                .stream().findFirst().orElseThrow();
        assertThat(forecast.getIncomingMembershipId()).isEqualTo(leaderMembershipId);
        assertThat(forecast.getForecastingLeaderMembershipId()).isEqualTo(leaderMembershipId);
        assertThat(forecast.getRemainingMinutes()).isEqualTo(90);
        assertThat(forecast.getActualMinutesSnapshot()).isEqualTo(45);
        assertThat(forecast.getInitialNote()).isEqualTo("next phase");
        assertThat(forecasts.findAllByProjectIdAndTaskIdOrderByAssignmentStartedAtAscCreatedAtAscIdAsc(
                projectId, unworked.id())).isEmpty();
        assertThat(notificationCount()).isEqualTo(notificationsBefore + 4);
    }

    @Test
    void mixedBatchMissingWorkedForecastRollsBackEveryAssignmentAndNotification() {
        TaskView worked = createMemberTask("Missing worked forecast");
        TaskView unworked = createMemberTask("Missing worked forecast companion");
        activateProject();
        taskService.addWorkLog("member@example.test", projectId, worked.id(),
                LocalDate.of(2026, 8, 14), 45, "Existing effort");
        TaskView currentWorked = taskService.list("leader@example.test", projectId).tasks().stream()
                .filter(candidate -> candidate.id() == worked.id()).findFirst().orElseThrow();
        TaskView currentUnworked = taskService.list("leader@example.test", projectId).tasks().stream()
                .filter(candidate -> candidate.id() == unworked.id()).findFirst().orElseThrow();
        ProjectTaskContext context = projectMutations.taskMutationContext(
                userId("leader@example.test"), projectId);
        long notificationsBefore = notificationCount();

        assertThatThrownBy(() -> taskTransfers.transferBatch(
                context,
                leaderMembershipId,
                memberMembershipId,
                Set.of(worked.id(), unworked.id()),
                Map.of(worked.id(), currentWorked.version(), unworked.id(), currentUnworked.version()),
                Map.of(),
                leaderMembershipId))
                .isInstanceOf(TaskValidationException.class)
                .hasMessageContaining("forecast");

        assertThat(taskService.details("leader@example.test", projectId, worked.id())
                .task().assigneeMembershipId()).isEqualTo(memberMembershipId);
        assertThat(taskService.details("leader@example.test", projectId, unworked.id())
                .task().assigneeMembershipId()).isEqualTo(memberMembershipId);
        assertThat(forecasts.findAllByProjectIdAndTaskIdOrderByAssignmentStartedAtAscCreatedAtAscIdAsc(
                projectId, worked.id())).isEmpty();
        assertThat(notificationCount()).isEqualTo(notificationsBefore);
    }

    @Test
    void mixedBatchForecastForUnworkedTaskRollsBackBeforeAnyWorkedForecastIsSaved() {
        TaskView worked = createMemberTask("Extra unworked forecast");
        TaskView unworked = createMemberTask("Extra unworked forecast companion");
        activateProject();
        taskService.addWorkLog("member@example.test", projectId, worked.id(),
                LocalDate.of(2026, 8, 14), 45, "Existing effort");
        TaskView currentWorked = taskService.list("leader@example.test", projectId).tasks().stream()
                .filter(candidate -> candidate.id() == worked.id()).findFirst().orElseThrow();
        TaskView currentUnworked = taskService.list("leader@example.test", projectId).tasks().stream()
                .filter(candidate -> candidate.id() == unworked.id()).findFirst().orElseThrow();
        ProjectTaskContext context = projectMutations.taskMutationContext(
                userId("leader@example.test"), projectId);
        long notificationsBefore = notificationCount();

        assertThatThrownBy(() -> taskTransfers.transferBatch(
                context,
                leaderMembershipId,
                memberMembershipId,
                Set.of(worked.id(), unworked.id()),
                Map.of(worked.id(), currentWorked.version(), unworked.id(), currentUnworked.version()),
                Map.of(
                        worked.id(), new RemainingEffortForecastInput(90, "worked"),
                        unworked.id(), new RemainingEffortForecastInput(30, "not allowed")),
                leaderMembershipId))
                .isInstanceOf(TaskValidationException.class)
                .hasMessageContaining("unworked");

        assertThat(taskService.details("leader@example.test", projectId, worked.id())
                .task().assigneeMembershipId()).isEqualTo(memberMembershipId);
        assertThat(taskService.details("leader@example.test", projectId, unworked.id())
                .task().assigneeMembershipId()).isEqualTo(memberMembershipId);
        assertThat(forecasts.findAllByProjectIdAndTaskIdOrderByAssignmentStartedAtAscCreatedAtAscIdAsc(
                projectId, worked.id())).isEmpty();
        assertThat(notificationCount()).isEqualTo(notificationsBefore);
    }

    @Test
    void mixedBatchStaleTaskVersionRollsBackAssignmentsForecastsAndNotifications() {
        TaskView worked = createMemberTask("Stale mixed batch");
        TaskView unworked = createMemberTask("Stale mixed batch companion");
        activateProject();
        taskService.addWorkLog("member@example.test", projectId, worked.id(),
                LocalDate.of(2026, 8, 14), 45, "Existing effort");
        TaskView currentWorked = taskService.list("leader@example.test", projectId).tasks().stream()
                .filter(candidate -> candidate.id() == worked.id()).findFirst().orElseThrow();
        TaskView currentUnworked = taskService.list("leader@example.test", projectId).tasks().stream()
                .filter(candidate -> candidate.id() == unworked.id()).findFirst().orElseThrow();
        jdbc.sql("update tasks set version = version + 1 where id = :id")
                .param("id", unworked.id()).update();
        entityManager.clear();
        ProjectTaskContext context = projectMutations.taskMutationContext(
                userId("leader@example.test"), projectId);
        long notificationsBefore = notificationCount();

        assertThatThrownBy(() -> taskTransfers.transferBatch(
                context,
                leaderMembershipId,
                memberMembershipId,
                Set.of(worked.id(), unworked.id()),
                Map.of(worked.id(), currentWorked.version(), unworked.id(), currentUnworked.version()),
                Map.of(worked.id(), new RemainingEffortForecastInput(90, "stale")),
                leaderMembershipId))
                .isInstanceOf(TaskConflictException.class);

        assertThat(taskService.details("leader@example.test", projectId, worked.id())
                .task().assigneeMembershipId()).isEqualTo(memberMembershipId);
        assertThat(taskService.details("leader@example.test", projectId, unworked.id())
                .task().assigneeMembershipId()).isEqualTo(memberMembershipId);
        assertThat(forecasts.findAllByProjectIdAndTaskIdOrderByAssignmentStartedAtAscCreatedAtAscIdAsc(
                projectId, worked.id())).isEmpty();
        assertThat(notificationCount()).isEqualTo(notificationsBefore);
    }

    @Test
    void internDashboardCountsAssignmentsAndOrdersFivePriorityTasks() {
        createMemberTask("Late");
        taskService.create("member@example.test", new CreateTaskCommand(
                projectId, memberMembershipId, "No due date", null, null));
        taskService.create("member@example.test", new CreateTaskCommand(
                projectId, memberMembershipId, "Earliest A", null, LocalDate.of(2026, 8, 10)));
        taskService.create("member@example.test", new CreateTaskCommand(
                projectId, memberMembershipId, "Earliest B", null, LocalDate.of(2026, 8, 10)));
        taskService.create("member@example.test", new CreateTaskCommand(
                projectId, memberMembershipId, "Middle", null, LocalDate.of(2026, 8, 11)));
        taskService.create("member@example.test", new CreateTaskCommand(
                projectId, memberMembershipId, "Next", null, LocalDate.of(2026, 8, 13)));
        setDueDateForTitle("Late", LocalDate.of(2026, 8, 12));
        activateProject();

        var dashboard = taskDashboard.dashboard("member@example.test");

        assertThat(dashboard.assignedTaskCount()).isEqualTo(6L);
        assertThat(dashboard.priorityTasks())
                .extracting(task -> task.title())
                .containsExactly("Earliest A", "Earliest B", "Middle", "Late", "Next");
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

    private String explain(String query) {
        return String.join(" ", jdbc.sql("explain (costs off) " + query)
                .param("projectId", projectId)
                .param("dueDate", LocalDate.of(2026, 8, 20))
                .query(String.class)
                .list());
    }

    private void assertDeniedAndUnchanged(TaskView task, ThrowingCallable operation) {
        TaskSnapshot before = snapshot(task.id());
        assertThatThrownBy(operation).isInstanceOf(TaskNotFoundException.class);
        assertThat(snapshot(task.id())).isEqualTo(before);
    }

    private TaskSnapshot snapshot(long taskId) {
        TaskRow task = jdbc.sql("""
                        select status, title, description, assignee_membership_id,
                               version, deleted_at
                        from tasks where id = :id
                        """)
                .param("id", taskId)
                .query((result, row) -> new TaskRow(
                        result.getString("status"),
                        result.getString("title"),
                        result.getString("description"),
                        result.getLong("assignee_membership_id"),
                        result.getLong("version"),
                        result.getTimestamp("deleted_at") == null
                                ? null
                                : result.getTimestamp("deleted_at").toInstant()))
                .single();
        return new TaskSnapshot(
                task.status(), task.title(), task.description(), task.assigneeMembershipId(),
                task.version(), task.deletedAt(),
                jdbc.sql("select count(*) from task_comments where task_id = :id")
                        .param("id", taskId).query(Long.class).single(),
                jdbc.sql("select count(*) from task_work_logs where task_id = :id")
                        .param("id", taskId).query(Long.class).single());
    }

    private record TaskSnapshot(
            String status,
            String title,
            String description,
            long assigneeMembershipId,
            long version,
            Instant deletedAt,
            long commentCount,
            long workLogCount) {}

    private record TaskRow(
            String status,
            String title,
            String description,
            long assigneeMembershipId,
            long version,
            Instant deletedAt) {}

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

    private long taskCount() {
        return jdbc.sql("select count(*) from tasks").query(Long.class).single();
    }

    private long notificationCount() {
        return jdbc.sql("select count(*) from notifications").query(Long.class).single();
    }

    private List<Long> notificationRecipientIds() {
        return jdbc.sql("select recipient_user_id from notifications order by id")
                .query(Long.class)
                .list();
    }

    private List<String> notificationTypes() {
        return jdbc.sql("select notification_type from notifications order by id")
                .query(String.class)
                .list();
    }

    private List<String> notificationEmailStatuses() {
        return jdbc.sql("select email_status from notifications order by id")
                .query(String.class)
                .list();
    }

    private List<String> notificationActionUrls() {
        return jdbc.sql("select action_url from notifications order by id")
                .query(String.class)
                .list();
    }

    private long commentCount() {
        return jdbc.sql("select count(*) from task_comments").query(Long.class).single();
    }

    private long currentLeadershipCount() {
        return jdbc.sql("""
                        select count(*) from project_leadership_terms
                        where project_id = :projectId and ended_at is null
                        """)
                .param("projectId", projectId)
                .query(Long.class)
                .single();
    }

    private long currentMembershipCount() {
        return jdbc.sql("""
                        select count(*) from project_memberships
                        where project_id = :projectId and left_at is null
                        """)
                .param("projectId", projectId)
                .query(Long.class)
                .single();
    }

    private TaskView createMemberTask(String title) {
        return taskService.create(
                "member@example.test",
                new CreateTaskCommand(projectId, memberMembershipId, title, null, null));
    }

    private void activateProject() {
        jdbc.sql("update projects set status = 'ACTIVE', activated_at = current_timestamp where id = :id")
                .param("id", projectId)
                .update();
        entityManager.clear();
    }

    private void completeProject() {
        long mentorId = userId("mentor@example.test");
        jdbc.sql("""
                        update tasks
                        set status = 'DONE'
                        where project_id = :id and deleted_at is null
                        """)
                .param("id", projectId)
                .update();
        jdbc.sql("""
                        update project_leadership_terms
                        set ended_at = started_at + interval '1 second', ended_by_mentor_user_id = :mentorId
                        where project_id = :id and ended_at is null
                        """)
                .param("id", projectId)
                .param("mentorId", mentorId)
                .update();
        jdbc.sql("""
                        update project_memberships
                        set left_at = joined_at + interval '1 second', removed_by_mentor_user_id = :mentorId
                        where project_id = :id and left_at is null
                        """)
                .param("id", projectId)
                .param("mentorId", mentorId)
                .update();
        jdbc.sql("""
                        update projects
                        set status = 'COMPLETED', activated_at = current_timestamp,
                            completed_at = current_timestamp
                        where id = :id
                        """)
                .param("id", projectId)
                .update();
        entityManager.clear();
    }

    private void setStatus(long taskId, TaskStatus status) {
        jdbc.sql("update tasks set status = :status where id = :id")
                .param("status", status.name())
                .param("id", taskId)
                .update();
        entityManager.clear();
    }

    private void softDelete(long taskId) {
        jdbc.sql("""
                        update tasks
                        set deleted_at = current_timestamp, deleted_by_membership_id = :membershipId
                        where id = :id
                        """)
                .param("membershipId", memberMembershipId)
                .param("id", taskId)
                .update();
        entityManager.clear();
    }

    private void closeMembership(long membershipId) {
        jdbc.sql("""
                        update project_memberships
                        set left_at = joined_at + interval '1 second', removed_by_mentor_user_id = :mentorId
                        where id = :id
                        """)
                .param("mentorId", userId("mentor@example.test"))
                .param("id", membershipId)
                .update();
        entityManager.clear();
    }

    private void setDueDateForTitle(String title, LocalDate dueDate) {
        jdbc.sql("update tasks set due_date = :dueDate where title = :title")
                .param("dueDate", dueDate)
                .param("title", title)
                .update();
        entityManager.clear();
    }
}
