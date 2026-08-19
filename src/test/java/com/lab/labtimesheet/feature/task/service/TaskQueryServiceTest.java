package com.lab.labtimesheet.feature.task.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.lab.labtimesheet.feature.task.repository.TaskRepository;
import com.lab.labtimesheet.feature.task.model.entity.Task;
import java.time.Instant;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskQueryServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-15T00:00:00Z");

    @Mock
    private TaskRepository tasks;

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

    /** [I2-PRJ-05] Boundary trả về số Task chưa DONE để Project quyết định hoàn tất. */
    @Test
    void countsUnfinishedTasksForProjectCompletion() {
        given(tasks.countUnfinishedByProjectId(42L)).willReturn(2L);

        assertThat(taskQueries.countUnfinishedTasks(42L)).isEqualTo(2L);

        verify(tasks).countUnfinishedByProjectId(42L);
    }

    /** [I2-PRJ-04] Boundary khóa và chuyển các Task chưa hoàn thành sang Leader nhận. */
    @Test
    void transfersLockedUnfinishedTasksAndFlushesBeforeReturning() {
        Task task = new Task(42L, 7L, "Task", null, null, 7L, NOW);
        given(tasks.findLockedUnfinishedByProjectIdAndAssigneeMembershipId(42L, 7L))
                .willReturn(java.util.List.of(task));

        taskQueries.transferUnfinishedTasks(42L, 7L, 9L, NOW.plusSeconds(60));

        assertThat(task.getAssigneeMembershipId()).isEqualTo(9L);
        assertThat(task.getAssignerMembershipId()).isEqualTo(9L);
        verify(tasks).flush();
    }
}
