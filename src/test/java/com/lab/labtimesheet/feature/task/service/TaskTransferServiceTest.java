package com.lab.labtimesheet.feature.task.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lab.labtimesheet.feature.project.model.dto.ProjectTaskContext;
import com.lab.labtimesheet.feature.project.model.dto.ProjectTaskMemberView;
import com.lab.labtimesheet.feature.task.exception.TaskValidationException;
import com.lab.labtimesheet.feature.task.model.TaskStatus;
import com.lab.labtimesheet.feature.task.model.entity.Task;
import com.lab.labtimesheet.feature.task.repository.TaskRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskTransferServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-20T00:00:00Z");

    @Mock
    private TaskRepository tasks;

    @Test
    void batchTransfersOnlySelectedUnfinishedTasksAndReturnsCount() {
        Task first = task(11L);
        Task second = task(12L);
        when(tasks.findLockedByIdAndProjectIdAndDeletedAtIsNull(11L, 10L)).thenReturn(Optional.of(first));
        when(tasks.findLockedByIdAndProjectIdAndDeletedAtIsNull(12L, 10L)).thenReturn(Optional.of(second));
        when(tasks.saveAllAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        TaskTransferService service = new TaskTransferService(tasks, Clock.fixed(NOW, ZoneOffset.UTC));

        TaskTransferResult result = service.transferBatch(
                context(), 70L, 71L, Set.of(12L, 11L), 72L);

        assertThat(result.transferredTaskCount()).isEqualTo(2);
        verify(first).reassign(72L, 70L, NOW);
        verify(second).reassign(72L, 70L, NOW);
    }

    @Test
    void rejectsSameSourceAndRecipientBeforeLoadingTasks() {
        TaskTransferService service = new TaskTransferService(tasks, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.transferBatch(
                        context(), 70L, 71L, Set.of(11L), 71L))
                .isInstanceOf(TaskValidationException.class);

        verify(tasks, org.mockito.Mockito.never())
                .findLockedByIdAndProjectIdAndDeletedAtIsNull(11L, 10L);
    }

    @Test
    void validatesEverySelectedTaskBeforeMutatingAnyRow() {
        Task first = task(11L);
        Task done = org.mockito.Mockito.mock(Task.class);
        when(done.getStatus()).thenReturn(TaskStatus.DONE);
        when(tasks.findLockedByIdAndProjectIdAndDeletedAtIsNull(11L, 10L)).thenReturn(Optional.of(first));
        when(tasks.findLockedByIdAndProjectIdAndDeletedAtIsNull(12L, 10L)).thenReturn(Optional.of(done));
        TaskTransferService service = new TaskTransferService(tasks, Clock.fixed(NOW, ZoneOffset.UTC));

        assertThatThrownBy(() -> service.transferBatch(
                        context(), 70L, 71L, Set.of(11L, 12L), 72L))
                .isInstanceOf(TaskValidationException.class);

        verify(first, org.mockito.Mockito.never()).reassign(72L, 70L, NOW);
        verify(tasks, org.mockito.Mockito.never()).saveAllAndFlush(any());
    }

    private static ProjectTaskContext context() {
        return new ProjectTaskContext(
                10L,
                3L,
                "ACTIVE",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                70L,
                List.of(
                        new ProjectTaskMemberView(70L, 5L, "Leader"),
                        new ProjectTaskMemberView(71L, 6L, "Target"),
                        new ProjectTaskMemberView(72L, 7L, "Recipient")));
    }

    private static Task task(long id) {
        Task task = org.mockito.Mockito.mock(Task.class);
        when(task.getAssigneeMembershipId()).thenReturn(71L);
        when(task.getStatus()).thenReturn(TaskStatus.IN_PROGRESS);
        return task;
    }
}
