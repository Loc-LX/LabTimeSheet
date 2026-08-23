package com.lab.labtimesheet.feature.task.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import com.lab.labtimesheet.feature.task.model.TaskStatus;
import com.lab.labtimesheet.feature.task.model.dto.TaskDueDateImpactView;
import com.lab.labtimesheet.feature.task.model.entity.Task;
import com.lab.labtimesheet.feature.task.repository.TaskRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskIteration3QueryTest {

    private static final LocalDate DAY_OFF = LocalDate.of(2026, 8, 20);

    @Mock
    private TaskRepository tasks;

    @Test
    void dueDateImpactListsCurrentTasksWithoutRewritingTheirStoredDueDate() {
        Task task = mock(Task.class);
        when(task.getId()).thenReturn(25L);
        when(task.getProjectId()).thenReturn(10L);
        when(task.getAssigneeMembershipId()).thenReturn(70L);
        when(task.getTitle()).thenReturn("Ship results");
        when(task.getDueDate()).thenReturn(DAY_OFF);
        when(task.getStatus()).thenReturn(TaskStatus.TODO);
        given(tasks.findAllByDueDateAndDeletedAtIsNullOrderByProjectIdAscIdAsc(DAY_OFF))
                .willReturn(List.of(task));

        TaskQueryService queries = new TaskQueryService(tasks, null, null);

        assertThat(queries.dueDateImpacts(DAY_OFF))
                .containsExactly(new TaskDueDateImpactView(
                        25L, 10L, "Ship results", DAY_OFF, TaskStatus.TODO, 70L));
        verify(tasks).findAllByDueDateAndDeletedAtIsNullOrderByProjectIdAscIdAsc(DAY_OFF);
    }
}
