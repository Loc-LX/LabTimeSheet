package com.lab.labtimesheet.feature.task.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.lab.labtimesheet.feature.task.model.entity.Task;
import com.lab.labtimesheet.feature.task.repository.TaskRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskTransferServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-14T00:00:00Z");

    @Mock
    private TaskRepository tasks;

    @Mock
    private Clock clock;

    @InjectMocks
    private TaskTransferService transfer;

    @Test
    void transfersOnlyUnfinishedTasksToTargetAndPreservesStatusAndCreator() {
        Task todo = mockTask();
        Task blocked = mockTask();
        given(clock.instant()).willReturn(NOW);
        given(tasks.findLockedUnfinishedByProjectIdAndAssigneeMembershipId(10L, 7L))
                .willReturn(List.of(todo, blocked));

        assertThat(transfer.transferUnfinishedTasks(10L, 7L, 9L)).isEqualTo(2);

        verify(todo).reassign(9L, 9L, NOW);
        verify(blocked).reassign(9L, 9L, NOW);
        verify(tasks).flush();
    }

    @Test
    void returnsZeroAndDoesNotFlushWhenNoUnfinishedTasksExist() {
        given(tasks.findLockedUnfinishedByProjectIdAndAssigneeMembershipId(10L, 7L))
                .willReturn(List.of());

        assertThat(transfer.transferUnfinishedTasks(10L, 7L, 9L)).isZero();

        verify(tasks, never()).flush();
    }

    private static Task mockTask() {
        return org.mockito.Mockito.mock(Task.class);
    }
}
