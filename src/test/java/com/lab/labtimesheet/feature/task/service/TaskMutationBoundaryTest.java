package com.lab.labtimesheet.feature.task.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.attendance.service.CalendarApplicationService;
import com.lab.labtimesheet.feature.project.model.dto.ProjectActorView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectTaskContext;
import com.lab.labtimesheet.feature.project.model.dto.ProjectTaskMemberView;
import com.lab.labtimesheet.feature.project.service.ProjectQueryService;
import com.lab.labtimesheet.feature.project.service.ProjectService;
import com.lab.labtimesheet.feature.task.exception.TaskNotFoundException;
import com.lab.labtimesheet.feature.task.exception.TaskValidationException;
import com.lab.labtimesheet.feature.task.model.TaskStatus;
import com.lab.labtimesheet.feature.task.model.dto.CreateTaskCommand;
import com.lab.labtimesheet.feature.task.model.dto.EditTaskCommand;
import com.lab.labtimesheet.feature.task.model.entity.Task;
import com.lab.labtimesheet.feature.task.model.entity.TaskComment;
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

@ExtendWith(MockitoExtension.class)
class TaskMutationBoundaryTest {

    private static final Instant NOW = Instant.parse("2026-08-15T00:00:00Z");

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
                70L,
                List.of(new ProjectTaskMemberView(70L, 5L, "Member")));
        when(projectQueries.authenticatedActor("member@example.test"))
                .thenReturn(new ProjectActorView(5L, "INTERN"));
        lenient().when(projectMutations.taskMutationContext(5L, 10L)).thenReturn(context);
    }

    @Test
    void createLocksAndRechecksProjectBeforeWriting() {
        Task saved = taskForView(TaskStatus.TODO);
        when(tasks.saveAndFlush(any(Task.class))).thenReturn(saved);

        service.create(
                "member@example.test",
                new CreateTaskCommand(10L, 70L, "Task", null, null));

        InOrder order = inOrder(projectMutations, tasks);
        order.verify(projectMutations).taskMutationContext(5L, 10L);
        order.verify(tasks).saveAndFlush(any(Task.class));
        verify(projectQueries, never()).taskContext(5L, 10L);
    }

    @Test
    void statusChangeLocksProjectThenTaskBeforeMutation() {
        Task task = taskForView(TaskStatus.TODO);
        when(tasks.findLockedByIdAndProjectIdAndDeletedAtIsNull(25L, 10L))
                .thenReturn(Optional.of(task));
        when(tasks.saveAndFlush(task)).thenReturn(task);

        service.changeStatus("member@example.test", 10L, 25L, TaskStatus.IN_PROGRESS);

        InOrder order = inOrder(projectMutations, tasks, task);
        order.verify(projectMutations).taskMutationContext(5L, 10L);
        order.verify(tasks).findLockedByIdAndProjectIdAndDeletedAtIsNull(25L, 10L);
        order.verify(task).changeStatus(TaskStatus.IN_PROGRESS, NOW);
    }

    @Test
    void commentLocksProjectThenTaskBeforeWriting() {
        Task task = mock(Task.class);
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

        InOrder order = inOrder(projectMutations, tasks, comments);
        order.verify(projectMutations).taskMutationContext(5L, 10L);
        order.verify(tasks).findLockedByIdAndProjectIdAndDeletedAtIsNull(25L, 10L);
        order.verify(comments).saveAndFlush(any(TaskComment.class));
    }

    @Test
    void reassignmentRequiresCurrentLeaderInActiveProject() {
        Task task = taskForView(TaskStatus.IN_PROGRESS);
        when(tasks.findLockedByIdAndProjectIdAndDeletedAtIsNull(25L, 10L))
                .thenReturn(Optional.of(task));
        when(tasks.saveAndFlush(task)).thenReturn(task);
        when(projectQueries.authenticatedActor("member@example.test"))
                .thenReturn(new ProjectActorView(5L, "INTERN"));
        when(projectMutations.taskMutationContext(5L, 10L))
                .thenReturn(context);

        var reassigned = service.reassign("member@example.test", 10L, 25L, 70L);

        InOrder order = inOrder(projectMutations, tasks, task);
        order.verify(projectMutations).taskMutationContext(5L, 10L);
        order.verify(tasks).findLockedByIdAndProjectIdAndDeletedAtIsNull(25L, 10L);
        order.verify(task).reassign(70L, 70L, NOW);
        org.assertj.core.api.Assertions.assertThat(reassigned.assigneeMembershipId()).isEqualTo(70L);
    }

    @Test
    void nonLeaderAndNonActiveProjectCannotReassign() {
        ProjectTaskContext completed = new ProjectTaskContext(
                10L, 3L, "COMPLETED",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), 70L,
                List.of(new ProjectTaskMemberView(70L, 5L, "Member")));
        when(projectMutations.taskMutationContext(5L, 10L)).thenReturn(completed);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        service.reassign("member@example.test", 10L, 25L, 70L))
                .isInstanceOf(TaskNotFoundException.class);

        ProjectTaskContext leaderContext = new ProjectTaskContext(
                10L, 3L, "ACTIVE",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), 71L,
                List.of(new ProjectTaskMemberView(70L, 5L, "Member")));
        when(projectMutations.taskMutationContext(5L, 10L)).thenReturn(leaderContext);

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        service.reassign("member@example.test", 10L, 25L, 70L))
                .isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    void doneTaskMustBeReopenedBeforeReassignment() {
        Task task = mock(Task.class);
        when(tasks.findLockedByIdAndProjectIdAndDeletedAtIsNull(25L, 10L))
                .thenReturn(Optional.of(task));
        org.mockito.Mockito.doThrow(new IllegalArgumentException(
                        "A DONE Task must be reopened before reassignment"))
                .when(task).reassign(anyLong(), anyLong(), any());

        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        service.reassign("member@example.test", 10L, 25L, 70L))
                .isInstanceOf(TaskValidationException.class)
                .hasMessage("A DONE Task must be reopened before reassignment");
        verify(task, times(1)).reassign(anyLong(), anyLong(), any());
    }

    @Test
    void leaderEditsUnfinishedTaskAndSelfCreatorEditsOwnWhileAssigned() {
        Task task = taskForView(TaskStatus.IN_PROGRESS);
        when(tasks.findLockedByIdAndProjectIdAndDeletedAtIsNull(25L, 10L))
                .thenReturn(Optional.of(task));
        when(tasks.saveAndFlush(task)).thenReturn(task);
        when(projectQueries.members(5L, 10L))
                .thenReturn(List.of(new com.lab.labtimesheet.feature.project.model.dto.ProjectMemberView(
                        70L, 5L, "Member", Instant.parse("2026-08-01T00:00:00Z"), null, false)));

        service.edit("member@example.test", new EditTaskCommand(10L, 25L, "Retitled", "Notes", null));

        verify(task).edit("Retitled", "Notes", null, NOW);
    }

    @Test
    void nonCreatorOrReassignedAwayMemberCannotEditOrDelete() {
        ProjectTaskContext anotherLeader = new ProjectTaskContext(
                10L, 3L, "ACTIVE",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), 80L,
                List.of(new ProjectTaskMemberView(70L, 5L, "Member")));
        when(projectMutations.taskMutationContext(5L, 10L)).thenReturn(anotherLeader);
        Task task = mock(Task.class);
        when(task.getStatus()).thenReturn(TaskStatus.IN_PROGRESS);
        when(task.getCreatorMembershipId()).thenReturn(80L);
        when(tasks.findLockedByIdAndProjectIdAndDeletedAtIsNull(25L, 10L))
                .thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.edit(
                        "member@example.test", new EditTaskCommand(10L, 25L, "Retitled", null, null)))
                .isInstanceOf(TaskNotFoundException.class);
        assertThatThrownBy(() -> service.softDelete("member@example.test", 10L, 25L))
                .isInstanceOf(TaskNotFoundException.class);
        verify(task, never()).edit(any(), any(), any(), any());
        verify(task, never()).softDelete(anyLong(), any());
    }

    @Test
    void doneTaskCannotBeEditedOrSoftDeleted() {
        Task task = mock(Task.class);
        when(task.getStatus()).thenReturn(TaskStatus.DONE);
        when(tasks.findLockedByIdAndProjectIdAndDeletedAtIsNull(25L, 10L))
                .thenReturn(Optional.of(task));

        assertThatThrownBy(() -> service.edit(
                        "member@example.test", new EditTaskCommand(10L, 25L, "Retitled", null, null)))
                .isInstanceOf(TaskNotFoundException.class);
        assertThatThrownBy(() -> service.softDelete("member@example.test", 10L, 25L))
                .isInstanceOf(TaskNotFoundException.class);
        verify(task, never()).edit(any(), any(), any(), any());
        verify(task, never()).softDelete(anyLong(), any());
    }

    @Test
    void leaderSoftDeletesAndEntityRecordsActorAndNow() {
        Task task = taskForView(TaskStatus.IN_PROGRESS);
        when(tasks.findLockedByIdAndProjectIdAndDeletedAtIsNull(25L, 10L))
                .thenReturn(Optional.of(task));
        when(tasks.saveAndFlush(task)).thenReturn(task);
        when(projectQueries.members(5L, 10L))
                .thenReturn(List.of(new com.lab.labtimesheet.feature.project.model.dto.ProjectMemberView(
                        70L, 5L, "Member", Instant.parse("2026-08-01T00:00:00Z"), null, false)));

        service.softDelete("member@example.test", 10L, 25L);

        verify(task).softDelete(70L, NOW);
    }

    @Test
    void historicalDetailsRequireLeaderOrSelfCreatorIncludingDeleted() {
        Task deleted = taskForView(TaskStatus.DONE);
        when(deleted.getDeletedAt()).thenReturn(NOW);
        when(projectQueries.taskContext(5L, 10L)).thenReturn(context);
        when(projectQueries.members(5L, 10L))
                .thenReturn(List.of(new com.lab.labtimesheet.feature.project.model.dto.ProjectMemberView(
                        70L, 5L, "Member", Instant.parse("2026-08-01T00:00:00Z"), null, false)));
        when(tasks.findByIdAndProjectId(25L, 10L)).thenReturn(Optional.of(deleted));
        when(comments.findAllByTaskIdOrderByCreatedAtAscIdAsc(25L)).thenReturn(List.of());
        when(workLogs.findAllByTaskIdAndProjectIdOrderByWorkDateAscIdAsc(25L, 10L)).thenReturn(List.of());

        var historical = service.historicalDetails("member@example.test", 10L, 25L);

        org.assertj.core.api.Assertions.assertThat(historical.deleted()).isTrue();
        org.assertj.core.api.Assertions.assertThat(historical.canEdit()).isFalse();
        org.assertj.core.api.Assertions.assertThat(historical.canDelete()).isFalse();
    }

    @Test
    void historicalDetailsRejectUnrelatedMember() {
        ProjectTaskContext anotherLeader = new ProjectTaskContext(
                10L, 3L, "ACTIVE",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), 80L,
                List.of(new ProjectTaskMemberView(70L, 5L, "Member")));
        Task deleted = mock(Task.class);
        when(deleted.getCreatorMembershipId()).thenReturn(80L);
        when(projectQueries.authenticatedActor("member@example.test"))
                .thenReturn(new ProjectActorView(5L, "INTERN"));
        when(projectQueries.taskContext(5L, 10L)).thenReturn(anotherLeader);
        when(tasks.findByIdAndProjectId(25L, 10L)).thenReturn(Optional.of(deleted));

        assertThatThrownBy(() -> service.historicalDetails("member@example.test", 10L, 25L))
                .isInstanceOf(TaskNotFoundException.class);
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
