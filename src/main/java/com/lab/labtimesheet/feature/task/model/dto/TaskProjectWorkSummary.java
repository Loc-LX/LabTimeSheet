package com.lab.labtimesheet.feature.task.model.dto;

import com.lab.labtimesheet.feature.task.model.TaskProgress;
import java.util.List;
import java.util.OptionalDouble;

/**
 * Authorized Project/Task work summary consumed by the Project and reporting features.
 *
 * @param progress fixed-status counts of current non-deleted Tasks
 * @param completionPercentage DONE share of all counted Tasks, empty for {@code N/A} when zero
 * @param totalMinutes all logged effort minutes in the Project
 * @param perMemberVisible true when the actor may view the per-member breakdown
 * @param perMemberHours scoped per-membership minutes, empty for aggregate-only actors
 */
public record TaskProjectWorkSummary(
        TaskProgress progress,
        OptionalDouble completionPercentage,
        long totalMinutes,
        boolean perMemberVisible,
        List<TaskMemberHours> perMemberHours) {

    /** Snapshots the scoped breakdown so consumers cannot alter the authorized result. */
    public TaskProjectWorkSummary {
        perMemberHours = List.copyOf(perMemberHours);
    }
}
