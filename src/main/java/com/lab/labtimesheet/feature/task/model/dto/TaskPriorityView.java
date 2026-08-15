package com.lab.labtimesheet.feature.task.model.dto;

import com.lab.labtimesheet.feature.task.model.TaskStatus;
import java.time.LocalDate;

/**
 * Compact current assignment rendered on an Intern dashboard.
 *
 * @param title Task title
 * @param projectName Project display name from the Project service boundary
 * @param status current fixed workflow status
 * @param dueDate optional Task due date
 */
public record TaskPriorityView(
        String title,
        String projectName,
        TaskStatus status,
        LocalDate dueDate) {}
