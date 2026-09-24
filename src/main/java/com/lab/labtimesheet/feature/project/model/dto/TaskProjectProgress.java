package com.lab.labtimesheet.feature.project.model.dto;

import java.util.OptionalDouble;

/**
 * Current non-deleted Project Task counts and retained total work minutes.
 *
 * @param todo current TODO count
 * @param inProgress current IN_PROGRESS count
 * @param blocked current BLOCKED count
 * @param done current DONE count
 * @param totalMinutes sum of all retained work-log minutes in the Project
 */
public record TaskProjectProgress(
        long todo,
        long inProgress,
        long blocked,
        long done,
        long totalMinutes) {

    /**
     * Returns the non-deleted Task denominator used by Project progress.
     *
     * @return total current Task count
     */
    public long totalTasks() {
        return todo + inProgress + blocked + done;
    }

    /**
     * Computes DONE as a percentage of current Tasks.
     *
     * @return empty for a Project without current Tasks, otherwise 0 through 100
     */
    public OptionalDouble completionPercentage() {
        return totalTasks() == 0
                ? OptionalDouble.empty()
                : OptionalDouble.of(done * 100.0 / totalTasks());
    }
}
