package com.lab.labtimesheet.feature.task.model.dto;

import com.lab.labtimesheet.feature.task.model.TaskStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Retained Task history projection assembled only from stored Task, comment, and work-log rows.
 *
 * <p>The projection intentionally contains no previous-assignee, status-event, or edit-event
 * timeline because the schema does not retain those facts.
 *
 * @param id Task identifier
 * @param projectId owning Project identifier
 * @param assigneeMembershipId current or final assignee membership identifier
 * @param title retained Task title
 * @param description retained Task description
 * @param status final stored Task status
 * @param dueDate retained optional due date
 * @param creatorMembershipId immutable creator membership identifier
 * @param assignerMembershipId current assignment actor membership identifier
 * @param assignedAt current assignment instant
 * @param createdAt immutable creation instant
 * @param updatedAt latest stored update instant
 * @param deletedAt soft-deletion instant, or null while retained as current
 * @param deletedByMembershipId soft-deletion actor membership, or null while current
 * @param comments retained append-only comments
 * @param workLogs retained dated effort rows
 */
public record TaskHistoryView(
        long id,
        long projectId,
        long assigneeMembershipId,
        String title,
        String description,
        TaskStatus status,
        LocalDate dueDate,
        long creatorMembershipId,
        long assignerMembershipId,
        Instant assignedAt,
        Instant createdAt,
        Instant updatedAt,
        Instant deletedAt,
        Long deletedByMembershipId,
        List<TaskCommentView> comments,
        List<TaskWorkLogView> workLogs) {

    /** Copies retained child rows so history consumers cannot mutate the projection. */
    public TaskHistoryView {
        comments = List.copyOf(comments);
        workLogs = List.copyOf(workLogs);
    }
}
