package com.lab.labtimesheet.feature.task.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lab.labtimesheet.feature.attendance.service.CalendarApplicationService;
import com.lab.labtimesheet.feature.project.model.dto.ProjectActorView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectTaskContext;
import com.lab.labtimesheet.feature.project.model.dto.ProjectTaskMemberView;
import com.lab.labtimesheet.feature.project.service.ProjectQueryService;
import com.lab.labtimesheet.feature.project.service.ProjectService;
import com.lab.labtimesheet.feature.task.model.TaskStatus;
import com.lab.labtimesheet.feature.task.model.dto.CreateTaskCommand;
import com.lab.labtimesheet.feature.task.model.entity.Task;
import com.lab.labtimesheet.feature.task.model.entity.TaskComment;
import com.lab.labtimesheet.feature.task.repository.TaskCommentRepository;
import com.lab.labtimesheet.feature.task.repository.TaskRepository;
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

    @Mock private TaskRepository tasks;
    @Mock private TaskCommentRepository comments;
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
                projectQueries,
                projectMutations,
                calendar,
                Clock.fixed(NOW, ZoneOffset.UTC));
        context = new ProjectTaskContext(
                10L,
                3L,
                "ACTIVE",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                70L,
                List.of(new ProjectTaskMemberView(70L, 5L, "Member", NOW)),
                java.util.Set.of());
        when(projectQueries.authenticatedActor("member@example.test"))
                .thenReturn(new ProjectActorView(5L, "INTERN"));
        when(projectMutations.taskMutationContext(5L, 10L)).thenReturn(context);
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
