package com.lab.labtimesheet.feature.project.model.dto;

import com.lab.labtimesheet.feature.project.model.TaskStatus;
import java.time.LocalDate;

/**
 * Task-owned facts needed to disclose which current Tasks are affected by a later global day off.
 *
 * @param taskId affected Task identifier
 * @param projectId owning Project identifier
 * @param title retained Task title
 * @param dueDate unchanged stored due date matching the impact date
 * @param status current Task status
 * @param assigneeMembershipId current same-Project assignee membership identifier
 */
public record TaskDueDateImpactView(
        long taskId,
        long projectId,
        String title,
        LocalDate dueDate,
        TaskStatus status,
        long assigneeMembershipId) {}
