package com.lab.labtimesheet.feature.reporting.model.dto;

import com.lab.labtimesheet.feature.project.model.TaskStatus;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Render-ready current Task row with immutable creator and assignment attribution.
 *
 * @param projectId owning Project identifier
 * @param projectName authorized Project display name
 * @param taskId Task identifier
 * @param title Task title
 * @param assigneeName current same-Project assignee name
 * @param assigneeMembershipId current assignee membership identifier
 * @param status fixed Task workflow status
 * @param dueDate optional due date
 * @param creatorMembershipId immutable creator membership identifier
 * @param assignerMembershipId current assignment actor membership identifier
 * @param assignedAt current assignment instant
 * @param createdAt immutable creation instant
 * @param loggedMinutes retained Task work-log minutes matching the report work-date filter
 */
public record ProjectTaskReportRow(
        long projectId,
        String projectName,
        long taskId,
        String title,
        String assigneeName,
        long assigneeMembershipId,
        TaskStatus status,
        LocalDate dueDate,
        long creatorMembershipId,
        long assignerMembershipId,
        Instant assignedAt,
        Instant createdAt,
        long loggedMinutes) {

    /**
     * Retains the original current-task row contract for callers that do not supply work-log
     * minutes.
     *
     * @param projectId owning Project identifier
     * @param projectName authorized Project display name
     * @param taskId Task identifier
     * @param title Task title
     * @param assigneeName current same-Project assignee name
     * @param assigneeMembershipId current assignee membership identifier
     * @param status fixed workflow status
     * @param dueDate optional due date
     * @param creatorMembershipId immutable creator membership identifier
     * @param assignerMembershipId current assignment actor membership identifier
     * @param assignedAt current assignment instant
     * @param createdAt immutable creation instant
     */
    public ProjectTaskReportRow(
            long projectId,
            String projectName,
            long taskId,
            String title,
            String assigneeName,
            long assigneeMembershipId,
            TaskStatus status,
            LocalDate dueDate,
            long creatorMembershipId,
            long assignerMembershipId,
            Instant assignedAt,
            Instant createdAt) {
        this(projectId, projectName, taskId, title, assigneeName, assigneeMembershipId, status,
                dueDate, creatorMembershipId, assignerMembershipId, assignedAt, createdAt, 0L);
    }
}
