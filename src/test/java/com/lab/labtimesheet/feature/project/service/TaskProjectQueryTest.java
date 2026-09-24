package com.lab.labtimesheet.feature.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.lab.labtimesheet.feature.project.model.TaskStatus;
import com.lab.labtimesheet.feature.project.model.dto.TaskMemberWorkView;
import com.lab.labtimesheet.feature.project.model.dto.TaskProjectProgress;
import com.lab.labtimesheet.feature.project.repository.TaskRepository;
import com.lab.labtimesheet.feature.project.repository.TaskWorkLogRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskProjectQueryTest {

    @Mock
    private TaskRepository tasks;

    @Mock
    private TaskWorkLogRepository workLogs;

    @InjectMocks
    private TaskQueryService taskQueries;

    @Test
    void returnsHandCheckableStatusProgressAndMinutes() {
        given(tasks.projectProgress(
                        10L,
                        TaskStatus.TODO,
                        TaskStatus.IN_PROGRESS,
                        TaskStatus.BLOCKED,
                        TaskStatus.DONE))
                .willReturn(new TaskProjectProgress(1L, 2L, 1L, 2L, 450L));

        TaskProjectProgress progress = taskQueries.projectProgress(10L);

        assertThat(progress.todo()).isEqualTo(1L);
        assertThat(progress.inProgress()).isEqualTo(2L);
        assertThat(progress.blocked()).isEqualTo(1L);
        assertThat(progress.done()).isEqualTo(2L);
        assertThat(progress.totalMinutes()).isEqualTo(450L);
        assertThat(progress.completionPercentage()).hasValue(33.333333333333336);
        verify(tasks).projectProgress(
                10L,
                TaskStatus.TODO,
                TaskStatus.IN_PROGRESS,
                TaskStatus.BLOCKED,
                TaskStatus.DONE);
        verify(workLogs, never()).sumMinutesByProjectId(10L);
    }

    @Test
    void returnsMemberTotalsInStableMembershipOrder() {
        given(workLogs.sumMinutesByProjectGroupedByMembership(10L))
                .willReturn(List.of(new TaskMemberWorkView(70L, 120L), new TaskMemberWorkView(71L, 330L)));

        assertThat(taskQueries.memberWork(10L))
                .containsExactly(new TaskMemberWorkView(70L, 120L), new TaskMemberWorkView(71L, 330L));
    }
}
