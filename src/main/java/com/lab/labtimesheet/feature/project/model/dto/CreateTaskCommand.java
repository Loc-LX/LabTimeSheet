package com.lab.labtimesheet.feature.project.model.dto;

import com.lab.labtimesheet.feature.project.model.TaskStatus;
import java.time.LocalDate;

/**
 * Authenticated request to create one Task in a Project.
 *
 * @param projectId Project aggregate identifier
 * @param assigneeMembershipId same-Project active membership identifier, not a user identifier
 * @param title required Task title
 * @param description optional Task description
 * @param dueDate optional business date constrained by Project dates and the current global calendar
 * @param estimatedMinutes optional Leader-owned whole-Task estimate in minutes
 * @param initialStatus optional requested initial status, accepted only when absent or TODO
 */
public record CreateTaskCommand(
        long projectId,
        long assigneeMembershipId,
        String title,
        String description,
        LocalDate dueDate,
        Integer estimatedMinutes,
        TaskStatus initialStatus) {

    public CreateTaskCommand(long projectId, long assigneeMembershipId, String title,
            String description, LocalDate dueDate, Integer estimatedMinutes) {
        this(projectId, assigneeMembershipId, title, description, dueDate, estimatedMinutes, null);
    }

    public CreateTaskCommand(long projectId, long assigneeMembershipId, String title,
            String description, LocalDate dueDate) {
        this(projectId, assigneeMembershipId, title, description, dueDate, null, null);
    }
}
