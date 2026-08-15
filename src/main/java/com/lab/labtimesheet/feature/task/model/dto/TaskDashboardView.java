package com.lab.labtimesheet.feature.task.model.dto;

import java.util.List;

/**
 * Role-scoped Task contribution to the shared dashboard.
 *
 * @param blockedTaskCount blocked Tasks in active Projects owned by a Mentor; otherwise zero
 * @param assignedTaskCount current non-deleted Tasks assigned to an Intern; otherwise zero
 * @param priorityTasks at most five Intern assignments ordered by due date, null last, then Task ID
 */
public record TaskDashboardView(
        long blockedTaskCount,
        long assignedTaskCount,
        List<TaskPriorityView> priorityTasks) {

    /** Copies the priority list so downstream UI code cannot mutate the service result. */
    public TaskDashboardView {
        priorityTasks = List.copyOf(priorityTasks);
    }
}
