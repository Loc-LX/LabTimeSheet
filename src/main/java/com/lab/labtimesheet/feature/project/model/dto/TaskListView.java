package com.lab.labtimesheet.feature.project.model.dto;

import com.lab.labtimesheet.feature.project.model.TaskProgress;
import java.util.List;

/**
 * Authorized current Task list with aggregate progress and create capability.
 *
 * @param tasks non-deleted Tasks ordered by Task identifier
 * @param progress status counts derived from exactly {@code tasks}; empty lists produce N/A progress
 * @param canCreate true only for an active member while the Project is PLANNED or ACTIVE
 */
public record TaskListView(List<TaskView> tasks, TaskProgress progress, boolean canCreate) {

    /** Copies the Task list so view consumers cannot alter the authorized result. */
    public TaskListView {
        tasks = List.copyOf(tasks);
    }
}
