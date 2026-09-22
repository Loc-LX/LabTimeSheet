package com.lab.labtimesheet.feature.task.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyLong;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lab.labtimesheet.feature.identity.model.AccountStatus;
import com.lab.labtimesheet.feature.identity.model.InternshipStatus;
import com.lab.labtimesheet.feature.identity.model.dto.AccountIdentity;
import com.lab.labtimesheet.feature.identity.model.dto.InternWorkWindow;
import com.lab.labtimesheet.feature.identity.service.AccountService;
import com.lab.labtimesheet.feature.attendance.service.CalendarApplicationService;
import com.lab.labtimesheet.feature.notification.service.NotificationService;
import com.lab.labtimesheet.feature.project.model.dto.ProjectActorView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectMemberView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectMembershipIntervalView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectTaskContext;
import com.lab.labtimesheet.feature.project.model.dto.ProjectTaskMemberView;
import com.lab.labtimesheet.feature.project.service.ProjectQueryService;
import com.lab.labtimesheet.feature.project.service.ProjectService;
import com.lab.labtimesheet.feature.task.exception.TaskConflictException;
import com.lab.labtimesheet.feature.task.exception.TaskValidationException;
import com.lab.labtimesheet.feature.task.model.TaskStatus;
import com.lab.labtimesheet.feature.task.model.dto.CreateTaskCommand;
import com.lab.labtimesheet.feature.task.model.dto.TaskWorkLogCandidate;
import com.lab.labtimesheet.feature.task.model.entity.Task;
import com.lab.labtimesheet.feature.task.model.entity.TaskComment;
import com.lab.labtimesheet.feature.task.model.entity.TaskRemainingEffortForecast;
import com.lab.labtimesheet.feature.task.model.entity.TaskWorkLog;
import com.lab.labtimesheet.feature.task.repository.TaskCommentRepository;
import com.lab.labtimesheet.feature.task.repository.TaskRemainingEffortForecastRepository;
import com.lab.labtimesheet.feature.task.repository.TaskRepository;
import com.lab.labtimesheet.feature.task.repository.TaskWorkLogRepository;
import com.lab.labtimesheet.platform.model.GlobalRole;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskMutationBoundaryTest {

    private static final Instant NOW = Instant.parse("2026-08-15T00:00:00Z");
    private static final Instant JOINED = Instant.parse("2026-08-01T00:00:00Z");

    @Mock private TaskRepository tasks;
    @Mock private TaskCommentRepository comments;
    @Mock private TaskWorkLogRepository workLogs;
    @Mock private ProjectQueryService projectQueries;
    @Mock private ProjectService projectMutations;
    @Mock private CalendarApplicationService calendar;
    @Mock private AccountService accounts;
    @Mock private NotificationService notifications;
    @Mock private TaskRemainingEffortForecastRepository forecasts;

    private TaskService service;
    private ProjectTaskContext context;

    @BeforeEach
    void setUp() {
        service = new TaskService(
                tasks,
                comments,
                workLogs,
                projectQueries,
                projectMutations,
                calendar,
                Clock.fixed(NOW, ZoneOffset.UTC),
                accounts,
                notifications,
                forecasts);
        context = new ProjectTaskContext(
                10L,
                3L,
                "ACTIVE",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                70L,
                List.of(new ProjectTaskMemberView(70L, 5L, "Member", JOINED)),
                java.util.Set.of());
        lenient().when(accounts.requireAccountIdByEmail("member@example.test")).thenReturn(5L);
        lenient().when(accounts.requireIdentityById(5L)).thenReturn(new AccountIdentity(
                5L, "member@example.test", "Member", GlobalRole.INTERN, AccountStatus.ACTIVE));
        lenient().when(projectMutations.taskMutationContext(5L, 10L)).thenReturn(context);
    }

    @Test
    void createLocksAndRechecksProjectBeforeWriting() {
        Task saved = taskForView(TaskStatus.TODO);
        when(tasks.saveAndFlush(any(Task.class))).thenReturn(saved);

        service.create(
                "member@example.test",
                new CreateTaskCommand(10L, 70L, "Task", null, null));

        InOrder order = inOrder(accounts, projectMutations, tasks);
        order.verify(accounts).requireAccountIdByEmail("member@example.test");
        order.verify(projectMutations).taskMutationContext(5L, 10L);
        order.verify(tasks).saveAndFlush(any(Task.class));
        verify(projectQueries, never()).taskContext(5L, 10L);
    }

    @Test
    void createRejectsPendingExitRecipientWithoutWriting() {
        ProjectTaskContext pending = new ProjectTaskContext(
                10L,
                3L,
                "ACTIVE",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                70L,
                List.of(new ProjectTaskMemberView(70L, 5L, "Member", JOINED)),
                Set.of(70L));
        when(projectMutations.taskMutationContext(5L, 10L)).thenReturn(pending);

        assertThatThrownBy(() -> service.create(
                        "member@example.test",
                        new CreateTaskCommand(10L, 70L, "Task", null, null)))
                .isInstanceOf(com.lab.labtimesheet.feature.task.exception.TaskNotFoundException.class);

        verify(tasks, never()).saveAndFlush(any(Task.class));
    }

    @Test
    void pendingExitCurrentAssigneeRetainsExistingStatusEditAndDeleteRights() {
        ProjectTaskContext pending = new ProjectTaskContext(
                10L,
                3L,
                "ACTIVE",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                70L,
                List.of(new ProjectTaskMemberView(70L, 5L, "Member", JOINED)),
                Set.of(70L));
        when(projectMutations.taskMutationContext(5L, 10L)).thenReturn(pending);
        Task task = taskForView(TaskStatus.TODO);
        when(tasks.findLockedByIdAndProjectIdAndDeletedAtIsNull(25L, 10L))
                .thenReturn(Optional.of(task));
        when(tasks.saveAndFlush(task)).thenReturn(task);
        when(projectQueries.members(5L, 10L)).thenReturn(List.of(
                new ProjectMemberView(70L, 5L, "Member", JOINED, null, true, 10L, null)));

        service.changeStatus("member@example.test", 10L, 25L, TaskStatus.IN_PROGRESS);
        service.edit("member@example.test", 10L, 25L, "Updated", "Details", null);
        service.softDelete("member@example.test", 10L, 25L);

        verify(task).changeStatus(TaskStatus.IN_PROGRESS, NOW);
        verify(task).updateDefinition("Updated", "Details", null, NOW);
        verify(task).softDelete(70L, NOW);
    }

    @Test
    void pendingExitCurrentAssigneeRetainsExistingWorkLogRight() {
        LocalDate workDate = LocalDate.of(2026, 8, 14);
        ProjectTaskContext pending = new ProjectTaskContext(
                10L,
                3L,
                "ACTIVE",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                70L,
                List.of(new ProjectTaskMemberView(70L, 5L, "Member", JOINED)),
                Set.of(70L));
        when(projectMutations.taskMutationContext(5L, 10L)).thenReturn(pending);
        Task task = mock(Task.class);
        when(task.getAssigneeMembershipId()).thenReturn(70L);
        TaskWorkLog saved = mock(TaskWorkLog.class);
        when(tasks.findLockedByIdAndProjectIdAndDeletedAtIsNull(25L, 10L))
                .thenReturn(Optional.of(task));
        when(accounts.lockedInternWorkWindow(5L, workDate)).thenReturn(new InternWorkWindow(
                5L,
                workDate,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                AccountStatus.ACTIVE,
                InternshipStatus.ACTIVE));
        when(projectQueries.membershipIntervals(5L)).thenReturn(List.of(
                new ProjectMembershipIntervalView(10L, 70L, JOINED, null)));
        when(workLogs.sumMinutesByMembershipIdsAndWorkDate(Set.of(70L), workDate)).thenReturn(0L);
        when(workLogs.saveAndFlush(any(TaskWorkLog.class))).thenReturn(saved);
        when(saved.getId()).thenReturn(90L);
        when(saved.getProjectId()).thenReturn(10L);
        when(saved.getTaskId()).thenReturn(25L);
        when(saved.getMembershipId()).thenReturn(70L);
        when(saved.getWorkDate()).thenReturn(workDate);
        when(saved.getMinutes()).thenReturn(60);
        when(saved.getCreatedAt()).thenReturn(NOW);
        when(saved.getUpdatedAt()).thenReturn(NOW);

        service.addWorkLog("member@example.test", 10L, 25L, workDate, 60, "Existing right");

        verify(workLogs).saveAndFlush(any(TaskWorkLog.class));
    }

    @Test
    void statusChangeLocksProjectThenTaskBeforeMutation() {
        Task task = taskForView(TaskStatus.TODO);
        when(tasks.findLockedByIdAndProjectIdAndDeletedAtIsNull(25L, 10L))
                .thenReturn(Optional.of(task));
        when(tasks.saveAndFlush(task)).thenReturn(task);

        service.changeStatus("member@example.test", 10L, 25L, TaskStatus.IN_PROGRESS);

        InOrder order = inOrder(accounts, projectMutations, tasks, task);
        order.verify(accounts).requireAccountIdByEmail("member@example.test");
        order.verify(projectMutations).taskMutationContext(5L, 10L);
        order.verify(tasks).findLockedByIdAndProjectIdAndDeletedAtIsNull(25L, 10L);
        order.verify(task).changeStatus(TaskStatus.IN_PROGRESS, NOW);
    }

    @Test
    void workedReassignmentWithoutForecastRejectsBeforeAnyMutation() {
        Task task = leaderTask();
        when(workLogs.existsByTaskIdAndProjectId(25L, 10L)).thenReturn(true);
        when(tasks.findLockedByIdAndProjectIdAndDeletedAtIsNull(25L, 10L)).thenReturn(Optional.of(task));
        assertThatThrownBy(() -> service.reassign("leader@example.test", 10L, 25L, 0L, 80L))
                .isInstanceOf(com.lab.labtimesheet.feature.task.exception.TaskValidationException.class);
        verify(task, never()).reassign(anyLong(), anyLong(), any(Instant.class));
        verify(forecasts, never()).save(any());
        verify(tasks, never()).saveAndFlush(any());
        verify(notifications, never()).publish(any(), any(), any());
    }

    @Test
    void unworkedReassignmentWithForecastRejectsBeforeAnyMutation() {
        Task task = leaderTask();
        when(workLogs.existsByTaskIdAndProjectId(25L, 10L)).thenReturn(false);
        when(tasks.findLockedByIdAndProjectIdAndDeletedAtIsNull(25L, 10L)).thenReturn(Optional.of(task));
        assertThatThrownBy(() -> service.reassign("leader@example.test", 10L, 25L, 0L, 80L,
                        new com.lab.labtimesheet.feature.task.model.dto.RemainingEffortForecastInput(10, "note")))
                .isInstanceOf(com.lab.labtimesheet.feature.task.exception.TaskValidationException.class);
        verify(task, never()).reassign(anyLong(), anyLong(), any(Instant.class));
        verify(forecasts, never()).save(any());
        verify(tasks, never()).saveAndFlush(any());
    }

    @Test
    void workedReassignmentRejectsInvalidForecastBeforeMutation() {
        Task task = leaderTask();
        when(workLogs.existsByTaskIdAndProjectId(25L, 10L)).thenReturn(true);
        when(tasks.findLockedByIdAndProjectIdAndDeletedAtIsNull(25L, 10L)).thenReturn(Optional.of(task));
        assertThatThrownBy(() -> service.reassign("leader@example.test", 10L, 25L, 0L, 80L,
                        new com.lab.labtimesheet.feature.task.model.dto.RemainingEffortForecastInput(null, "note")))
                .isInstanceOf(com.lab.labtimesheet.feature.task.exception.TaskValidationException.class);
        verify(forecasts, never()).save(any());
        verify(task, never()).reassign(anyLong(), anyLong(), any(Instant.class));
    }

    @Test
    void unworkedReassignmentWithoutForecastPersistsTaskOnly() {
        Task task = leaderTask();
        prepareTaskPersistence(task);
        when(workLogs.existsByTaskIdAndProjectId(25L, 10L)).thenReturn(false);
        when(tasks.findLockedByIdAndProjectIdAndDeletedAtIsNull(25L, 10L)).thenReturn(Optional.of(task));
        service.reassign("leader@example.test", 10L, 25L, 0L, 80L);
        verify(task).reassign(80L, 80L, NOW);
        verify(tasks).saveAndFlush(task);
        verify(forecasts, never()).save(any());
        verify(notifications).publish(any(), any(), any());
    }

    @Test
    void workedReassignmentWithForecastPersistsExactSnapshotAndReassignsAtSameInstant() {
        Task task = leaderTask();
        prepareTaskPersistence(task);
        when(workLogs.existsByTaskIdAndProjectId(25L, 10L)).thenReturn(true);
        when(workLogs.sumMinutesByTaskIdAndProjectId(25L, 10L)).thenReturn(135L);
        when(tasks.findLockedByIdAndProjectIdAndDeletedAtIsNull(25L, 10L)).thenReturn(Optional.of(task));
        service.reassign("leader@example.test", 10L, 25L, 0L, 80L,
                new com.lab.labtimesheet.feature.task.model.dto.RemainingEffortForecastInput(90, "  next phase  "));
        ArgumentCaptor<TaskRemainingEffortForecast> captor = ArgumentCaptor.forClass(TaskRemainingEffortForecast.class);
        verify(forecasts).save(captor.capture());
        TaskRemainingEffortForecast forecast = captor.getValue();
        assertThat(forecast.getProjectId()).isEqualTo(10L);
        assertThat(forecast.getTaskId()).isEqualTo(25L);
        assertThat(forecast.getIncomingMembershipId()).isEqualTo(80L);
        assertThat(forecast.getForecastingLeaderMembershipId()).isEqualTo(80L);
        assertThat(forecast.getAssignmentStartedAt()).isEqualTo(NOW);
        assertThat(forecast.getCreatedAt()).isEqualTo(NOW);
        assertThat(forecast.getRemainingMinutes()).isEqualTo(90);
        assertThat(forecast.getActualMinutesSnapshot()).isEqualTo(135L);
        assertThat(forecast.getInitialNote()).isEqualTo("next phase");
        verify(task).reassign(80L, 80L, NOW);
        verify(tasks).saveAndFlush(task);
        verify(notifications).publish(any(), any(), any());
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 527040})
    void workedReassignmentAcceptsMinuteBoundsThroughService(int minutes) {
        Task task = leaderTask();
        prepareTaskPersistence(task);
        when(workLogs.existsByTaskIdAndProjectId(25L, 10L)).thenReturn(true);
        when(workLogs.sumMinutesByTaskIdAndProjectId(25L, 10L)).thenReturn(135L);
        when(tasks.findLockedByIdAndProjectIdAndDeletedAtIsNull(25L, 10L)).thenReturn(Optional.of(task));
        service.reassign("leader@example.test", 10L, 25L, 0L, 80L,
                new com.lab.labtimesheet.feature.task.model.dto.RemainingEffortForecastInput(minutes, null));
        ArgumentCaptor<TaskRemainingEffortForecast> captor = ArgumentCaptor.forClass(TaskRemainingEffortForecast.class);
        verify(forecasts).save(captor.capture());
        assertThat(captor.getValue().getRemainingMinutes()).isEqualTo(minutes);
    }

    @Test
    void workedReassignmentRejects501CharNoteBeforeMutation() {
        Task task = leaderTask();
        when(workLogs.existsByTaskIdAndProjectId(25L, 10L)).thenReturn(true);
        when(tasks.findLockedByIdAndProjectIdAndDeletedAtIsNull(25L, 10L)).thenReturn(Optional.of(task));
        assertThatThrownBy(() -> service.reassign("leader@example.test", 10L, 25L, 0L, 80L,
                        new com.lab.labtimesheet.feature.task.model.dto.RemainingEffortForecastInput(90, "x".repeat(501))))
                .isInstanceOf(com.lab.labtimesheet.feature.task.exception.TaskValidationException.class);
        verify(forecasts, never()).save(any());
        verify(task, never()).reassign(anyLong(), anyLong(), any(Instant.class));
        verify(tasks, never()).saveAndFlush(any());
    }

    @Test
    void staleVersionRejectsBeforeForecastLookupOrPersistence() {
        Task task = leaderTask();
        when(task.getVersion()).thenReturn(4L);
        when(tasks.findLockedByIdAndProjectIdAndDeletedAtIsNull(25L, 10L)).thenReturn(Optional.of(task));
        assertThatThrownBy(() -> service.reassign("leader@example.test", 10L, 25L, 3L, 80L,
                        new com.lab.labtimesheet.feature.task.model.dto.RemainingEffortForecastInput(90, "note")))
                .isInstanceOf(TaskConflictException.class);
        verify(workLogs, never()).existsByTaskIdAndProjectId(anyLong(), anyLong());
        verify(forecasts, never()).save(any());
        verify(task, never()).reassign(anyLong(), anyLong(), any(Instant.class));
        verify(tasks, never()).saveAndFlush(any());
    }

    @Test
    void staleVersionRejectsBeforeSameRecipientValidation() {
        Task task = leaderTask();
        when(task.getAssigneeMembershipId()).thenReturn(80L);
        when(task.getVersion()).thenReturn(4L);
        when(tasks.findLockedByIdAndProjectIdAndDeletedAtIsNull(25L, 10L)).thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.reassign(
                        "leader@example.test", 10L, 25L, 3L, 80L,
                        new com.lab.labtimesheet.feature.task.model.dto.RemainingEffortForecastInput(90, "note")))
                .isInstanceOf(TaskConflictException.class)
                .hasMessage("Task changed concurrently; reload before trying again");
        verify(workLogs, never()).existsByTaskIdAndProjectId(anyLong(), anyLong());
        verify(forecasts, never()).save(any());
        verify(task, never()).reassign(anyLong(), anyLong(), any(Instant.class));
        verify(tasks, never()).saveAndFlush(any());
    }

    @Test
    void correctionAppendsSuccessorWithImmutableAssignmentFacts() {
        Task task = leaderTask();
        when(task.getAssignedAt()).thenReturn(NOW);
        when(tasks.findLockedByIdAndProjectIdAndDeletedAtIsNull(25L, 10L)).thenReturn(Optional.of(task));
        TaskRemainingEffortForecast predecessor = mock(TaskRemainingEffortForecast.class);
        when(predecessor.getId()).thenReturn(44L);
        when(predecessor.getProjectId()).thenReturn(10L);
        when(predecessor.getTaskId()).thenReturn(25L);
        when(predecessor.getIncomingMembershipId()).thenReturn(70L);
        when(predecessor.getAssignmentStartedAt()).thenReturn(NOW);
        when(forecasts.findByIdAndProjectIdAndTaskId(44L, 10L, 25L)).thenReturn(Optional.of(predecessor));
        when(forecasts.existsBySupersedesForecastId(44L)).thenReturn(false);
        when(workLogs.existsByTaskIdAndProjectIdAndMembershipIdAndCreatedAtGreaterThanEqual(
                25L, 10L, 70L, NOW)).thenReturn(false);
        when(workLogs.sumMinutesByTaskIdAndProjectId(25L, 10L)).thenReturn(135L);
        when(projectQueries.authenticatedActor("leader@example.test"))
                .thenReturn(new ProjectActorView(6L, "INTERN"));
        when(projectQueries.taskContext(6L, 10L)).thenReturn(context);
        when(projectQueries.members(6L, 10L)).thenReturn(List.of(
                new ProjectMemberView(70L, 5L, "Member", JOINED, null, false, 10L, null),
                new ProjectMemberView(80L, 6L, "Recipient", JOINED, null, true, 10L, null)));
        when(tasks.findByIdAndProjectIdAndDeletedAtIsNull(25L, 10L)).thenReturn(Optional.of(task));
        when(comments.findAllByTaskIdOrderByCreatedAtAscIdAsc(25L)).thenReturn(List.of());
        when(workLogs.findAllByTaskIdAndProjectIdOrderByWorkDateAscIdAsc(25L, 10L)).thenReturn(List.of());
        when(forecasts.findAllByProjectIdAndTaskIdOrderByAssignmentStartedAtAscCreatedAtAscIdAsc(10L, 25L))
                .thenReturn(List.of(predecessor));

        assertThat(service.details("leader@example.test", 10L, 25L)
                .remainingEffortForecasts()).singleElement()
                .extracting(view -> view.correctionOpen())
                .isEqualTo(true);
        verify(workLogs, never()).existsByTaskIdAndProjectIdAndMembershipIdAndCreatedAtGreaterThanEqual(
                25L, 10L, 70L, NOW);

        service.correctForecast("leader@example.test", 10L, 25L, 44L, 90, "  reason  ");

        ArgumentCaptor<TaskRemainingEffortForecast> captor = ArgumentCaptor.forClass(TaskRemainingEffortForecast.class);
        verify(forecasts).saveAndFlush(captor.capture());
        TaskRemainingEffortForecast successor = captor.getValue();
        assertThat(successor.getProjectId()).isEqualTo(10L);
        assertThat(successor.getTaskId()).isEqualTo(25L);
        assertThat(successor.getIncomingMembershipId()).isEqualTo(70L);
        assertThat(successor.getForecastingLeaderMembershipId()).isEqualTo(80L);
        assertThat(successor.getAssignmentStartedAt()).isEqualTo(NOW);
        assertThat(successor.getActualMinutesSnapshot()).isEqualTo(135L);
        assertThat(successor.getRemainingMinutes()).isEqualTo(90);
        assertThat(successor.getInitialNote()).isNull();
        assertThat(successor.getCorrectionReason()).isEqualTo("reason");
        assertThat(successor.getSupersedesForecastId()).isEqualTo(44L);
        verify(workLogs).existsByTaskIdAndProjectIdAndMembershipIdAndCreatedAtGreaterThanEqual(
                25L, 10L, 70L, NOW);
        verify(task, never()).reassign(anyLong(), anyLong(), any(Instant.class));
        verify(notifications, never()).publish(any(), any(), any());
    }

    @Test
    void correctionRejectsInvalidReasonLateWorkAndStaleSuccessor() {
        Task task = leaderTask();
        when(task.getAssignedAt()).thenReturn(NOW);
        when(tasks.findLockedByIdAndProjectIdAndDeletedAtIsNull(25L, 10L)).thenReturn(Optional.of(task));
        TaskRemainingEffortForecast predecessor = mock(TaskRemainingEffortForecast.class);
        when(predecessor.getId()).thenReturn(44L);
        when(predecessor.getIncomingMembershipId()).thenReturn(70L);
        when(predecessor.getAssignmentStartedAt()).thenReturn(NOW);
        when(forecasts.findByIdAndProjectIdAndTaskId(44L, 10L, 25L)).thenReturn(Optional.of(predecessor));
        when(forecasts.existsBySupersedesForecastId(44L)).thenReturn(false);
        when(workLogs.existsByTaskIdAndProjectIdAndMembershipIdAndCreatedAtGreaterThanEqual(
                25L, 10L, 70L, NOW)).thenReturn(false);
        assertThatThrownBy(() -> service.correctForecast("leader@example.test", 10L, 25L, 44L, 0, "reason"))
                .isInstanceOf(TaskValidationException.class);
        assertThatThrownBy(() -> service.correctForecast("leader@example.test", 10L, 25L, 44L, 90, "  "))
                .isInstanceOf(TaskValidationException.class);
        verify(forecasts, never()).saveAndFlush(any());
    }

    private void prepareTaskPersistence(Task task) {
        lenient().when(accounts.requireIdentityById(5L)).thenReturn(new AccountIdentity(
                5L, "member@example.test", "Member", GlobalRole.INTERN, AccountStatus.ACTIVE));
        lenient().when(accounts.requireIdentityById(6L)).thenReturn(new AccountIdentity(
                6L, "leader@example.test", "Leader", GlobalRole.INTERN, AccountStatus.ACTIVE));
        when(tasks.saveAndFlush(task)).thenReturn(task);
    }

    @Test
    void nonLeaderCannotCreateForecast() {
        Task task = leaderTask();
        when(accounts.requireAccountIdByEmail("member@example.test")).thenReturn(5L);
        when(projectMutations.taskMutationContext(5L, 10L)).thenReturn(context);
        assertThatThrownBy(() -> service.reassign("member@example.test", 10L, 25L, 0L, 80L,
                        new com.lab.labtimesheet.feature.task.model.dto.RemainingEffortForecastInput(10, "n")))
                .isInstanceOf(com.lab.labtimesheet.feature.task.exception.TaskNotFoundException.class);
        verify(forecasts, never()).save(any());
    }

    private Task leaderTask() {
        context = new ProjectTaskContext(10L, 3L, "ACTIVE", LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31), 80L,
                List.of(new ProjectTaskMemberView(70L, 5L, "Member", JOINED),
                        new ProjectTaskMemberView(80L, 6L, "Recipient", JOINED)), Set.of());
        lenient().when(accounts.requireAccountIdByEmail("leader@example.test")).thenReturn(6L);
        lenient().when(projectMutations.taskMutationContext(6L, 10L)).thenReturn(context);
        Task task = mock(Task.class);
        lenient().when(task.getId()).thenReturn(25L);
        lenient().when(task.getAssigneeMembershipId()).thenReturn(70L);
        lenient().when(task.getStatus()).thenReturn(TaskStatus.IN_PROGRESS);
        lenient().when(task.getVersion()).thenReturn(0L);
        return task;
    }

    @Test
    void owningMentorCanChangeStatusForAnyProjectTask() {
        when(accounts.requireAccountIdByEmail("mentor@example.test")).thenReturn(3L);
        when(projectMutations.taskMutationContext(3L, 10L)).thenReturn(context);
        Task task = taskForView(TaskStatus.TODO);
        when(tasks.findLockedByIdAndProjectIdAndDeletedAtIsNull(25L, 10L))
                .thenReturn(Optional.of(task));
        when(tasks.saveAndFlush(task)).thenReturn(task);

        service.changeStatus("mentor@example.test", 10L, 25L, TaskStatus.IN_PROGRESS);

        verify(task).changeStatus(TaskStatus.IN_PROGRESS, NOW);
        verify(tasks).saveAndFlush(task);
    }

    @Test
    void statusChangeTurnsAnOptimisticTaskRaceIntoAnExplicitConflict() {
        Task task = mock(Task.class);
        when(task.getAssigneeMembershipId()).thenReturn(70L);
        when(task.getStatus()).thenReturn(TaskStatus.TODO);
        when(tasks.findLockedByIdAndProjectIdAndDeletedAtIsNull(25L, 10L))
                .thenReturn(Optional.of(task));
        when(tasks.saveAndFlush(task)).thenThrow(
                new ObjectOptimisticLockingFailureException(Task.class, 25L));

        assertThatThrownBy(() -> service.changeStatus(
                        "member@example.test", 10L, 25L, TaskStatus.IN_PROGRESS))
                .isInstanceOf(TaskConflictException.class)
                .hasMessage("Task changed concurrently; reload before trying again");

        verify(notifications, never()).publish(any(), any(), any());
    }

    @Test
    void commentLocksProjectThenTaskBeforeWriting() {
        Task task = mock(Task.class);
        when(task.getAssigneeMembershipId()).thenReturn(70L);
        TaskComment saved = mock(TaskComment.class);
        when(tasks.findLockedByIdAndProjectIdAndDeletedAtIsNull(25L, 10L))
                .thenReturn(Optional.of(task));
        when(comments.saveAndFlush(any(TaskComment.class))).thenReturn(saved);
        when(saved.getId()).thenReturn(4L);
        when(saved.getTaskId()).thenReturn(25L);
        when(saved.getAuthorUserId()).thenReturn(5L);
        when(saved.getBody()).thenReturn("Comment");
        when(saved.getCreatedAt()).thenReturn(NOW);

        service.addComment("member@example.test", 10L, 25L, "Comment");

        InOrder order = inOrder(accounts, projectMutations, tasks, comments);
        order.verify(accounts).requireAccountIdByEmail("member@example.test");
        order.verify(projectMutations).taskMutationContext(5L, 10L);
        order.verify(tasks).findLockedByIdAndProjectIdAndDeletedAtIsNull(25L, 10L);
        order.verify(comments).saveAndFlush(any(TaskComment.class));
        verify(projectQueries, never()).members(5L, 10L);
    }

    @Test
    void workLogLocksAccountBeforeReadingDailyTotalAndWriting() {
        LocalDate workDate = LocalDate.of(2026, 8, 14);
        Task task = org.mockito.Mockito.mock(Task.class);
        when(task.getAssigneeMembershipId()).thenReturn(70L);
        TaskWorkLog saved = org.mockito.Mockito.mock(TaskWorkLog.class);
        when(tasks.findLockedByIdAndProjectIdAndDeletedAtIsNull(25L, 10L))
                .thenReturn(Optional.of(task));
        when(accounts.lockedInternWorkWindow(5L, workDate)).thenReturn(new InternWorkWindow(
                5L,
                workDate,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                AccountStatus.ACTIVE,
                InternshipStatus.ACTIVE));
        when(projectQueries.membershipIntervals(5L)).thenReturn(List.of(
                new ProjectMembershipIntervalView(10L, 70L, JOINED, null)));
        when(workLogs.sumMinutesByMembershipIdsAndWorkDate(Set.of(70L), workDate)).thenReturn(0L);
        when(workLogs.saveAndFlush(any(TaskWorkLog.class))).thenReturn(saved);
        when(saved.getId()).thenReturn(90L);
        when(saved.getProjectId()).thenReturn(10L);
        when(saved.getTaskId()).thenReturn(25L);
        when(saved.getMembershipId()).thenReturn(70L);
        when(saved.getWorkDate()).thenReturn(workDate);
        when(saved.getMinutes()).thenReturn(60);
        when(saved.getCreatedAt()).thenReturn(NOW);
        when(saved.getUpdatedAt()).thenReturn(NOW);

        service.addWorkLog(
                "member@example.test", 10L, 25L, workDate, 60, "Experiment");

        InOrder order = inOrder(accounts, projectMutations, projectQueries, tasks, workLogs);
        order.verify(accounts).requireAccountIdByEmail("member@example.test");
        order.verify(projectMutations).taskMutationContext(5L, 10L);
        order.verify(accounts).lockedInternWorkWindow(5L, workDate);
        order.verify(projectQueries).membershipIntervals(5L);
        order.verify(tasks).findLockedByIdAndProjectIdAndDeletedAtIsNull(25L, 10L);
        order.verify(workLogs).sumMinutesByMembershipIdsAndWorkDate(Set.of(70L), workDate);
        order.verify(workLogs).saveAndFlush(any(TaskWorkLog.class));
    }

    @Test
    void workLogRejectsLockedAccountBeforeReadingDailyTotal() {
        LocalDate workDate = LocalDate.of(2026, 8, 14);
        when(accounts.lockedInternWorkWindow(5L, workDate)).thenReturn(new InternWorkWindow(
                5L,
                workDate,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                AccountStatus.LOCKED,
                InternshipStatus.ACTIVE));

        assertThatThrownBy(() -> service.addWorkLog(
                        "member@example.test", 10L, 25L, workDate, 60, "Blocked"))
                .isInstanceOf(com.lab.labtimesheet.feature.task.exception.TaskValidationException.class);

        verify(projectMutations).taskMutationContext(5L, 10L);
        verify(tasks, org.mockito.Mockito.never()).findLockedByIdAndProjectIdAndDeletedAtIsNull(25L, 10L);
        verify(workLogs, org.mockito.Mockito.never())
                .sumMinutesByMembershipIdsAndWorkDate(any(), any());
        verify(workLogs, org.mockito.Mockito.never()).saveAndFlush(any(TaskWorkLog.class));
    }

    @Test
    void correctionPreReadsDateThenLocksAccountBeforeProjectLogAndTask() {
        LocalDate workDate = LocalDate.of(2026, 8, 14);
        TaskWorkLogCandidate candidate = new TaskWorkLogCandidate(90L, 10L, 25L, 70L, workDate);
        TaskWorkLog locked = org.mockito.Mockito.mock(TaskWorkLog.class);
        Task task = org.mockito.Mockito.mock(Task.class);
        when(locked.getProjectId()).thenReturn(10L);
        when(locked.getTaskId()).thenReturn(25L);
        when(locked.getMembershipId()).thenReturn(70L);
        when(locked.getWorkDate()).thenReturn(workDate);
        when(locked.getId()).thenReturn(90L);
        when(locked.getMinutes()).thenReturn(120);
        when(locked.getNote()).thenReturn("Corrected");
        when(locked.getCreatedAt()).thenReturn(NOW);
        when(locked.getUpdatedAt()).thenReturn(NOW);
        when(workLogs.findCandidateByIdAndProjectId(90L, 10L)).thenReturn(Optional.of(candidate));
        when(workLogs.findLockedByIdAndProjectId(90L, 10L)).thenReturn(Optional.of(locked));
        when(tasks.findLockedByIdAndProjectIdAndDeletedAtIsNull(25L, 10L)).thenReturn(Optional.of(task));
        when(accounts.requireAccountIdByEmail("member@example.test")).thenReturn(5L);
        when(accounts.lockedInternWorkWindow(5L, workDate)).thenReturn(new InternWorkWindow(
                5L,
                workDate,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                AccountStatus.ACTIVE,
                InternshipStatus.ACTIVE));
        when(projectQueries.membershipIntervals(5L)).thenReturn(List.of(
                new ProjectMembershipIntervalView(10L, 70L, JOINED, null)));
        when(workLogs.sumMinutesByMembershipIdsAndWorkDate(Set.of(70L), workDate)).thenReturn(120L);
        when(workLogs.saveAndFlush(locked)).thenReturn(locked);

        service.correctWorkLog("member@example.test", 10L, 90L, 60, "Corrected");

        InOrder order = inOrder(workLogs, accounts, projectMutations, projectQueries, tasks);
        order.verify(workLogs).findCandidateByIdAndProjectId(90L, 10L);
        order.verify(accounts).requireAccountIdByEmail("member@example.test");
        order.verify(projectMutations).taskMutationContext(5L, 10L);
        order.verify(accounts).lockedInternWorkWindow(5L, workDate);
        order.verify(projectQueries).membershipIntervals(5L);
        order.verify(workLogs).findLockedByIdAndProjectId(90L, 10L);
        order.verify(tasks).findLockedByIdAndProjectIdAndDeletedAtIsNull(25L, 10L);
        order.verify(workLogs).sumMinutesByMembershipIdsAndWorkDate(Set.of(70L), workDate);
        order.verify(workLogs).saveAndFlush(locked);
    }

    @Test
    void correctionRejectsAStaleClientWorkLogVersionBeforeMutation() {
        LocalDate workDate = LocalDate.of(2026, 8, 14);
        TaskWorkLogCandidate candidate = new TaskWorkLogCandidate(90L, 10L, 25L, 70L, workDate);
        TaskWorkLog locked = mock(TaskWorkLog.class);
        when(locked.getId()).thenReturn(90L);
        when(locked.getProjectId()).thenReturn(10L);
        when(locked.getTaskId()).thenReturn(25L);
        when(locked.getMembershipId()).thenReturn(70L);
        when(locked.getWorkDate()).thenReturn(workDate);
        when(locked.getVersion()).thenReturn(4L);
        when(workLogs.findCandidateByIdAndProjectId(90L, 10L)).thenReturn(Optional.of(candidate));
        when(workLogs.findLockedByIdAndProjectId(90L, 10L)).thenReturn(Optional.of(locked));
        when(accounts.requireAccountIdByEmail("member@example.test")).thenReturn(5L);
        when(accounts.lockedInternWorkWindow(5L, workDate)).thenReturn(new InternWorkWindow(
                5L,
                workDate,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                AccountStatus.ACTIVE,
                InternshipStatus.ACTIVE));
        when(projectQueries.membershipIntervals(5L)).thenReturn(List.of(
                new ProjectMembershipIntervalView(10L, 70L, JOINED, null)));

        assertThatThrownBy(() -> service.correctWorkLog(
                        "member@example.test", 10L, 90L, 1L, 3L, 60, "Corrected"))
                .isInstanceOf(TaskConflictException.class)
                .hasMessage("Task work log changed concurrently; reload before trying again");

        verify(tasks, never()).findLockedByIdAndProjectIdAndDeletedAtIsNull(25L, 10L);
        verify(workLogs, never()).saveAndFlush(any(TaskWorkLog.class));
        verify(notifications, never()).publish(any(), any(), any());
    }

    private static Task taskForView(TaskStatus status) {
        Task task = mock(Task.class);
        when(task.getId()).thenReturn(25L);
        when(task.getProjectId()).thenReturn(10L);
        when(task.getAssigneeMembershipId()).thenReturn(70L);
        when(task.getTitle()).thenReturn("Task");
        when(task.getStatus()).thenReturn(status);
        when(task.getCreatorMembershipId()).thenReturn(70L);
        when(task.getAssignerMembershipId()).thenReturn(70L);
        when(task.getAssignedAt()).thenReturn(NOW);
        when(task.getCreatedAt()).thenReturn(NOW);
        return task;
    }
}
