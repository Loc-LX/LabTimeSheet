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
 * @param version current optimistic-concurrency version for current Task controls
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
        List<TaskWorkLogView> workLogs,
        long version) {

    /**
     * Retains the pre-version constructor for historical report fixtures.
     *
     * @param id Task identifier
     * @param projectId owning Project identifier
     * @param assigneeMembershipId current or final assignee membership
     * @param title retained title
     * @param description retained description
     * @param status final status
     * @param dueDate retained due date
     * @param creatorMembershipId immutable creator membership
     * @param assignerMembershipId current assignment actor membership
     * @param assignedAt current assignment instant
     * @param createdAt creation instant
     * @param updatedAt latest update instant
     * @param deletedAt soft-deletion instant
     * @param deletedByMembershipId soft-deletion actor membership
     * @param comments retained comments
     * @param workLogs retained work logs
     */
    public TaskHistoryView(
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
        this(id, projectId, assigneeMembershipId, title, description, status, dueDate,
                creatorMembershipId, assignerMembershipId, assignedAt, createdAt, updatedAt,
                deletedAt, deletedByMembershipId, comments, workLogs, 0L);
    }

    /** Copies retained child rows so history consumers cannot mutate the projection. */
    public TaskHistoryView {
        comments = List.copyOf(comments);
        workLogs = List.copyOf(workLogs);
    }
}
