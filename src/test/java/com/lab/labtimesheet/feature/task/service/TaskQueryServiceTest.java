package com.lab.labtimesheet.feature.task.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.lab.labtimesheet.feature.project.service.ProjectQueryService;
import com.lab.labtimesheet.feature.task.model.TaskStatus;
import com.lab.labtimesheet.feature.task.repository.TaskRepository;
import com.lab.labtimesheet.feature.task.repository.TaskWorkLogRepository;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskQueryServiceTest {

    @Mock
    private TaskRepository tasks;

    @Mock
    private TaskWorkLogRepository workLogs;

    @Mock
    private ProjectQueryService projects;

    @InjectMocks
    private TaskQueryService taskQueries;

    @Test
    void countsEveryCurrentTaskWhenProjectHasNoActiveMemberships() {
        given(tasks.countByProjectIdAndDeletedAtIsNull(42L)).willReturn(3L);

        assertThat(taskQueries.countCurrentTasksAssignedOutside(42L, Set.of())).isEqualTo(3L);

        verify(tasks).countByProjectIdAndDeletedAtIsNull(42L);
    }

    @Test
    void countsCurrentTasksWhoseAssigneeIsOutsideActiveMemberships() {
        given(tasks.countCurrentTasksAssignedOutside(42L, Set.of(7L, 9L))).willReturn(2L);

        assertThat(taskQueries.countCurrentTasksAssignedOutside(42L, Set.of(7L, 9L))).isEqualTo(2L);

        verify(tasks).countCurrentTasksAssignedOutside(42L, Set.of(7L, 9L));
    }

    @Test
    void reportsCurrentAndDoneCountsForCompletionGate() {
        given(tasks.countByProjectIdAndDeletedAtIsNull(42L)).willReturn(4L);
        given(tasks.countByProjectIdAndStatusAndDeletedAtIsNull(42L, TaskStatus.DONE)).willReturn(4L);

        assertThat(taskQueries.countCurrentTasks(42L)).isEqualTo(4L);
        assertThat(taskQueries.countDoneTasks(42L)).isEqualTo(4L);
    }
}
