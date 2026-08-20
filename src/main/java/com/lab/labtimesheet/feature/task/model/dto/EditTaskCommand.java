package com.lab.labtimesheet.feature.task.model.dto;

import java.time.LocalDate;

/**
 * Authenticated request to edit the definition of one Task.
 *
 * @param projectId owning Project aggregate identifier
 * @param taskId Task identifier within that Project
 * @param title required normalized Task title
 * @param description optional Task description
 * @param dueDate optional business date constrained by Project dates and the current global calendar
 */
public record EditTaskCommand(
        long projectId,
        long taskId,
        String title,
        String description,
        LocalDate dueDate) {}
