package com.lab.labtimesheet.feature.task.model.dto;

import com.lab.labtimesheet.feature.task.model.TaskStatus;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Authorized current Task projection with immutable creator and assignment attribution.
 *
 * @param id Task identifier
 * @param projectId owning Project identifier
 * @param assigneeMembershipId current same-Project assignee membership identifier
 * @param assigneeName current or historical assignee name supplied by the Project feature
 * @param title Task title
 * @param description optional description
 * @param status current fixed workflow status
 * @param dueDate optional due date
 * @param creatorMembershipId immutable creating membership identifier
 * @param assignerMembershipId membership identifier responsible for the current assignment
 * @param assignedAt instant the current assignment was established
 * @param createdAt immutable Task creation instant
 */
public record TaskView(
        long id,
        long projectId,
        long assigneeMembershipId,
        String assigneeName,
        String title,
        String description,
        TaskStatus status,
        LocalDate dueDate,
        long creatorMembershipId,
        long assignerMembershipId,
        Instant assignedAt,
        Instant createdAt) {}
