package com.lab.labtimesheet.feature.task.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.lab.labtimesheet.feature.task.model.entity.Task;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TaskDomainRulesTest {

    private static final Instant NOW = Instant.parse("2026-08-15T00:00:00Z");

    private static final Map<TaskStatus, Set<TaskStatus>> ALLOWED_TRANSITIONS = Map.of(
            TaskStatus.TODO, Set.of(TaskStatus.IN_PROGRESS, TaskStatus.BLOCKED),
            TaskStatus.IN_PROGRESS, Set.of(TaskStatus.DONE, TaskStatus.BLOCKED),
            TaskStatus.BLOCKED, Set.of(TaskStatus.TODO, TaskStatus.IN_PROGRESS),
            TaskStatus.DONE, Set.of(TaskStatus.IN_PROGRESS));

    @ParameterizedTest
    @MethodSource("allStatusTransitions")
    void acceptsOnlyTheFixedStatusGraph(TaskStatus current, TaskStatus target, boolean expected) {
        assertThat(current.canTransitionTo(target)).isEqualTo(expected);
    }

    @Test
    void reportsNoPercentageForAProjectWithoutTasks() {
        TaskProgress progress = TaskProgress.from(List.of());

        assertThat(progress.completionPercentage()).isEmpty();
        assertThat(progress.total()).isZero();
        assertThat(progress.count(TaskStatus.DONE)).isZero();
    }

    @Test
    void countsStatusesAndDonePercentageFromCurrentTasks() {
        TaskProgress progress = TaskProgress.from(List.of(
                TaskStatus.TODO,
                TaskStatus.IN_PROGRESS,
                TaskStatus.DONE,
                TaskStatus.DONE));

        assertThat(progress.completionPercentage()).hasValue(50);
        assertThat(progress.total()).isEqualTo(4);
        assertThat(progress.count(TaskStatus.TODO)).isEqualTo(1);
        assertThat(progress.count(TaskStatus.IN_PROGRESS)).isEqualTo(1);
        assertThat(progress.count(TaskStatus.BLOCKED)).isZero();
        assertThat(progress.count(TaskStatus.DONE)).isEqualTo(2);
    }

    /** [I2-PRJ-04] Transfer giữ creator/status nhưng cập nhật assignee và assignment actor/time. */
    @Test
    void transfersOnlyAnUnfinishedTaskToTheAssistedTarget() {
        var task = new Task(42L, 7L, "Task", null, null, 7L, NOW);

        task.transferUnfinishedTo(9L, 9L, NOW.plusSeconds(60));

        assertThat(task.getAssigneeMembershipId()).isEqualTo(9L);
        assertThat(task.getAssignerMembershipId()).isEqualTo(9L);
        assertThat(task.getCreatorMembershipId()).isEqualTo(7L);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.TODO);
        assertThat(task.getAssignedAt()).isEqualTo(NOW.plusSeconds(60));
    }

    /** [I2-PRJ-04] DONE Task không bị chuyển vì phải giữ attribution lịch sử. */
    @Test
    void refusesToTransferADoneTask() {
        var task = new Task(42L, 7L, "Task", null, null, 7L, NOW);
        task.changeStatus(TaskStatus.IN_PROGRESS, NOW.plusSeconds(1));
        task.changeStatus(TaskStatus.DONE, NOW.plusSeconds(2));

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> task.transferUnfinishedTo(9L, 9L, NOW.plusSeconds(60)));
        assertThat(task.getAssigneeMembershipId()).isEqualTo(7L);
    }

    private static Stream<Arguments> allStatusTransitions() {
        return Stream.of(TaskStatus.values())
                .flatMap(current -> Stream.of(TaskStatus.values())
                        .map(target -> Arguments.of(
                                current,
                                target,
                                ALLOWED_TRANSITIONS.get(current).contains(target))));
    }
}
