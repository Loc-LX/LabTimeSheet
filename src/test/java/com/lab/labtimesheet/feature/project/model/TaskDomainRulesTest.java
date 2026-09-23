package com.lab.labtimesheet.feature.project.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class TaskDomainRulesTest {

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

    private static Stream<Arguments> allStatusTransitions() {
        return Stream.of(TaskStatus.values())
                .flatMap(current -> Stream.of(TaskStatus.values())
                        .map(target -> Arguments.of(
                                current,
                                target,
                                ALLOWED_TRANSITIONS.get(current).contains(target))));
    }
}
