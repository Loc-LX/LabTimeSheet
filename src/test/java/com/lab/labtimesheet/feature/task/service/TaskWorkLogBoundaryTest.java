package com.lab.labtimesheet.feature.task.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.attendance.service.CalendarApplicationService;
import com.lab.labtimesheet.feature.project.model.dto.ProjectActorView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectMemberView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectTaskContext;
import com.lab.labtimesheet.feature.project.model.dto.ProjectTaskMemberView;
import com.lab.labtimesheet.feature.project.service.ProjectQueryService;
import com.lab.labtimesheet.feature.project.service.ProjectService;
import com.lab.labtimesheet.feature.task.exception.TaskNotFoundException;
import com.lab.labtimesheet.feature.task.exception.TaskValidationException;
import com.lab.labtimesheet.feature.task.model.TaskStatus;
import com.lab.labtimesheet.feature.task.model.dto.LogWorkCommand;
import com.lab.labtimesheet.feature.task.model.dto.LogWorkCorrection;
import com.lab.labtimesheet.feature.task.model.entity.Task;
import com.lab.labtimesheet.feature.task.model.entity.TaskWorkLog;
import com.lab.labtimesheet.feature.task.repository.TaskCommentRepository;
import com.lab.labtimesheet.feature.task.repository.TaskRepository;
import com.lab.labtimesheet.feature.task.repository.TaskWorkLogRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class TaskWorkLogBoundaryTest {

    private static final Instant NOW = Instant.parse("2026-08-14T00:00:00Z");
    private static final LocalDate TODAY = LocalDate.of(2026, 8, 14);

    @Mock private AccountService accounts;
    @Mock private TaskRepository tasks;
    @Mock private TaskCommentRepository comments;
    @Mock private TaskWorkLogRepository workLogs;
    @Mock private ProjectQueryService projectQueries;
    @Mock private ProjectService projectMutations;
    @Mock private CalendarApplicationService calendar;

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
                accounts,
                Clock.fixed(NOW, ZoneOffset.UTC));
        context = new ProjectTaskContext(
                10L,
                3L,
                "ACTIVE",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                71L,
                List.of(
                        new ProjectTaskMemberView(70L, 5L, "Member"),
                        new ProjectTaskMemberView(71L, 6L, "Leader")));
        when(projectQueries.authenticatedActor("member@example.test"))
                .thenReturn(new ProjectActorView(5L, "INTERN"));
        when(projectQueries.authenticatedActor("leader@example.test"))
                .thenReturn(new ProjectActorView(6L, "INTERN"));
        when(projectMutations.taskMutationContext(5L, 10L)).thenReturn(context);
        when(projectMutations.taskMutationContext(6L, 10L)).thenReturn(context);
        when(projectQueries.members(5L, 10L))
                .thenReturn(List.of(new ProjectMemberView(
                        70L, 5L, "Member", Instant.parse("2026-08-01T00:00:00Z"), null, false)));
    }

    @Test
    void currentAssigneeCreatesWorkLogAfterLockingProjectThenTask() {
        Task task = taskForView();
        when(tasks.findLockedByIdAndProjectIdAndDeletedAtIsNull(25L, 10L))
                .thenReturn(Optional.of(task));
        TaskWorkLog saved = mock(TaskWorkLog.class);
        when(workLogs.saveAndFlush(any(TaskWorkLog.class))).thenReturn(saved);
        when(saved.getId()).thenReturn(4L);
        when(saved.getProjectId()).thenReturn(10L);
        when(saved.getTaskId()).thenReturn(25L);
        when(saved.getMembershipId()).thenReturn(70L);
        when(saved.getWorkDate()).thenReturn(TODAY);
        when(saved.getMinutes()).thenReturn(90);
        when(saved.getNote()).thenReturn("Ran experiment");
        when(saved.getCreatedAt()).thenReturn(NOW);
        when(saved.getUpdatedAt()).thenReturn(NOW);

        var view = service.logWork(
                "member@example.test",
                10L,
                25L,
                new LogWorkCommand(TODAY, 90, "  Ran experiment  "));

        InOrder order = inOrder(projectMutations, tasks, accounts, workLogs);
        order.verify(projectMutations).taskMutationContext(5L, 10L);
        order.verify(tasks).findLockedByIdAndProjectIdAndDeletedAtIsNull(25L, 10L);
        order.verify(accounts).lockInternProfileForDailyWork(5L);
        order.verify(workLogs).saveAndFlush(any(TaskWorkLog.class));
        verify(calendar, never()).isGlobalDayOff(any());
        org.assertj.core.api.Assertions.assertThat(view.minutes()).isEqualTo(90);
        org.assertj.core.api.Assertions.assertThat(view.note()).isEqualTo("Ran experiment");
    }

    @Test
    void newLogIsRejectedWhenDailyTotalAcrossAllProjectsWouldExceedFourteenForty() {
        Task task = taskForView();
        when(tasks.findLockedByIdAndProjectIdAndDeletedAtIsNull(25L, 10L))
                .thenReturn(Optional.of(task));
        when(projectQueries.membershipIdsForIntern(5L)).thenReturn(List.of(70L, 75L));
        when(workLogs.sumMinutesByMembershipIdsAndWorkDate(List.of(70L, 75L), TODAY))
                .thenReturn(1380);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.logWork(
                        "member@example.test", 10L, 25L,
                        new LogWorkCommand(TODAY, 61, null)))
                .isInstanceOf(TaskValidationException.class)
                .hasMessageContaining("1440");
        verify(workLogs, never()).saveAndFlush(any());
    }

    @Test
    void correctionIsRejectedWhenDailyTotalAcrossAllProjectsWouldExceedFourteenForty() {
        Task task = taskForView();
        when(tasks.findLockedByIdAndProjectIdAndDeletedAtIsNull(25L, 10L))
                .thenReturn(Optional.of(task));
        TaskWorkLog log = mock(TaskWorkLog.class);
        when(workLogs.findLockedByIdAndTaskIdAndProjectId(4L, 25L, 10L))
                .thenReturn(Optional.of(log));
        when(log.getMembershipId()).thenReturn(70L);
        when(log.getWorkDate()).thenReturn(TODAY);
        when(log.getMinutes()).thenReturn(45);
        when(projectQueries.membershipIdsForIntern(5L)).thenReturn(List.of(70L, 75L));
        when(workLogs.sumMinutesByMembershipIdsAndWorkDate(List.of(70L, 75L), TODAY))
                .thenReturn(1420);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.correctWorkLog(
                        "member@example.test", 10L, 25L, 4L,
                        new LogWorkCorrection(66, null)))
                .isInstanceOf(TaskValidationException.class)
                .hasMessageContaining("1440");
        verify(log, never()).correct(anyInt(), any(), any());
    }

    @Test
    void dailyTotalIsCheckedAfterLockingInternProfileAndBeforeWriting() {
        Task task = taskForView();
        when(tasks.findLockedByIdAndProjectIdAndDeletedAtIsNull(25L, 10L))
                .thenReturn(Optional.of(task));
        when(projectQueries.membershipIdsForIntern(5L)).thenReturn(List.of(70L, 75L));
        when(workLogs.sumMinutesByMembershipIdsAndWorkDate(List.of(70L, 75L), TODAY))
                .thenReturn(1380);
        TaskWorkLog saved = mock(TaskWorkLog.class);
        when(workLogs.saveAndFlush(any(TaskWorkLog.class))).thenReturn(saved);
        when(saved.getId()).thenReturn(4L);
        when(saved.getProjectId()).thenReturn(10L);
        when(saved.getTaskId()).thenReturn(25L);
        when(saved.getMembershipId()).thenReturn(70L);
        when(saved.getWorkDate()).thenReturn(TODAY);
        when(saved.getMinutes()).thenReturn(60);
        when(saved.getNote()).thenReturn(null);
        when(saved.getCreatedAt()).thenReturn(NOW);
        when(saved.getUpdatedAt()).thenReturn(NOW);

        service.logWork("member@example.test", 10L, 25L, new LogWorkCommand(TODAY, 60, null));

        InOrder order = inOrder(projectMutations, tasks, accounts, projectQueries, workLogs);
        order.verify(projectMutations).taskMutationContext(5L, 10L);
        order.verify(tasks).findLockedByIdAndProjectIdAndDeletedAtIsNull(25L, 10L);
        order.verify(accounts).lockInternProfileForDailyWork(5L);
        order.verify(projectQueries).membershipIdsForIntern(5L);
        order.verify(workLogs).sumMinutesByMembershipIdsAndWorkDate(List.of(70L, 75L), TODAY);
        order.verify(workLogs).saveAndFlush(any(TaskWorkLog.class));
    }

    @Test
    void nonAssigneeAndInactiveProjectCannotCreateWorkLogs() {
        Task task = taskForView();
        when(tasks.findLockedByIdAndProjectIdAndDeletedAtIsNull(25L, 10L))
                .thenReturn(Optional.of(task));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.logWork(
                        "leader@example.test",
                        10L,
                        25L,
                        new LogWorkCommand(TODAY, 30, null)))
                .isInstanceOf(TaskNotFoundException.class);

        ProjectTaskContext planned = new ProjectTaskContext(
                10L, 3L, "PLANNED",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), 71L,
                List.of(new ProjectTaskMemberView(70L, 5L, "Member")));
        when(projectMutations.taskMutationContext(5L, 10L)).thenReturn(planned);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.logWork(
                        "member@example.test",
                        10L,
                        25L,
                        new LogWorkCommand(TODAY, 30, null)))
                .isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    void workDateMustNotBeFutureAndMustFallWithinProjectAndMembership() {
        Task task = taskForView();
        when(tasks.findLockedByIdAndProjectIdAndDeletedAtIsNull(25L, 10L))
                .thenReturn(Optional.of(task));
        when(projectQueries.members(5L, 10L))
                .thenReturn(List.of(new ProjectMemberView(
                        70L, 5L, "Member", Instant.parse("2026-08-10T00:00:00Z"), null, false)));

        var future = new LogWorkCommand(TODAY.plusDays(1), 30, null);
        var beforeProject = new LogWorkCommand(LocalDate.of(2026, 7, 31), 30, null);
        var afterProject = new LogWorkCommand(LocalDate.of(2026, 9, 1), 30, null);
        var beforeMembership = new LogWorkCommand(LocalDate.of(2026, 8, 9), 30, null);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.logWork(
                        "member@example.test", 10L, 25L, future))
                .isInstanceOf(TaskValidationException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.logWork(
                        "member@example.test", 10L, 25L, beforeProject))
                .isInstanceOf(TaskValidationException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.logWork(
                        "member@example.test", 10L, 25L, afterProject))
                .isInstanceOf(TaskValidationException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.logWork(
                        "member@example.test", 10L, 25L, beforeMembership))
                .isInstanceOf(TaskValidationException.class);
        verify(workLogs, never()).saveAndFlush(any());
    }

    @Test
    void minutesMustBeBetweenOneAndFourteenFortyAndNoteOptionalNonBlank() {
        Task task = taskForView();
        when(tasks.findLockedByIdAndProjectIdAndDeletedAtIsNull(25L, 10L))
                .thenReturn(Optional.of(task));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.logWork(
                        "member@example.test", 10L, 25L,
                        new LogWorkCommand(TODAY, 0, null)))
                .isInstanceOf(TaskValidationException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.logWork(
                        "member@example.test", 10L, 25L,
                        new LogWorkCommand(TODAY, 1441, null)))
                .isInstanceOf(TaskValidationException.class);
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.logWork(
                        "member@example.test", 10L, 25L,
                        new LogWorkCommand(TODAY, 30, "   ")))
                .isInstanceOf(TaskValidationException.class);
        verify(workLogs, never()).saveAndFlush(any());
    }

    @Test
    void onlyLogAuthorMayCorrectTheirOwnLogEvenAfterReassignment() {
        Task task = taskForView();
        when(task.getAssigneeMembershipId()).thenReturn(71L);
        when(tasks.findLockedByIdAndProjectIdAndDeletedAtIsNull(25L, 10L))
                .thenReturn(Optional.of(task));
        TaskWorkLog log = mock(TaskWorkLog.class);
        when(workLogs.findLockedByIdAndTaskIdAndProjectId(4L, 25L, 10L))
                .thenReturn(Optional.of(log));
        when(log.getId()).thenReturn(4L);
        when(log.getProjectId()).thenReturn(10L);
        when(log.getTaskId()).thenReturn(25L);
        when(log.getMembershipId()).thenReturn(70L);
        when(log.getWorkDate()).thenReturn(TODAY);
        when(log.getMinutes()).thenReturn(45);
        when(log.getNote()).thenReturn(null);
        when(log.getCreatedAt()).thenReturn(NOW);
        when(log.getUpdatedAt()).thenReturn(NOW);
        when(workLogs.saveAndFlush(log)).thenReturn(log);

        var corrected = service.correctWorkLog(
                "member@example.test", 10L, 25L, 4L,
                new LogWorkCorrection(60, "Adjusted"));

        InOrder order = inOrder(projectMutations, tasks, workLogs, log);
        order.verify(projectMutations).taskMutationContext(5L, 10L);
        order.verify(tasks).findLockedByIdAndProjectIdAndDeletedAtIsNull(25L, 10L);
        order.verify(workLogs).findLockedByIdAndTaskIdAndProjectId(4L, 25L, 10L);
        order.verify(log).correct(60, "Adjusted", NOW);
        order.verify(workLogs).saveAndFlush(log);
        org.assertj.core.api.Assertions.assertThat(corrected.minutes()).isEqualTo(45);
    }

    @Test
    void nonAuthorCannotCorrectAndCorrectionRequiresActiveMembership() {
        Task task = taskForView();
        when(tasks.findLockedByIdAndProjectIdAndDeletedAtIsNull(25L, 10L))
                .thenReturn(Optional.of(task));
        TaskWorkLog log = mock(TaskWorkLog.class);
        when(workLogs.findLockedByIdAndTaskIdAndProjectId(4L, 25L, 10L))
                .thenReturn(Optional.of(log));
        when(log.getMembershipId()).thenReturn(71L);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.correctWorkLog(
                        "member@example.test", 10L, 25L, 4L,
                        new LogWorkCorrection(60, "Adjusted")))
                .isInstanceOf(TaskNotFoundException.class);
        verify(workLogs, never()).saveAndFlush(any());
    }

    private static Task taskForView() {
        Task task = mock(Task.class);
        when(task.getId()).thenReturn(25L);
        when(task.getProjectId()).thenReturn(10L);
        when(task.getAssigneeMembershipId()).thenReturn(70L);
        when(task.getTitle()).thenReturn("Task");
        when(task.getStatus()).thenReturn(TaskStatus.IN_PROGRESS);
        when(task.getCreatorMembershipId()).thenReturn(70L);
        when(task.getAssignerMembershipId()).thenReturn(70L);
        when(task.getAssignedAt()).thenReturn(NOW);
        when(task.getCreatedAt()).thenReturn(NOW);
        return task;
    }
}
