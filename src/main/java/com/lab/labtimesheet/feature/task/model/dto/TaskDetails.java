package com.lab.labtimesheet.feature.task.model.dto;

import java.util.List;

/**
 * Authorized Task detail view and server-derived action capabilities.
 *
 * @param task visible non-deleted Task
 * @param comments append-only comment history in creation order
 * @param workLogs retained dated effort rows in ascending work-date order
 * @param actorMembershipId active viewer membership used only to expose author-owned corrections
 * @param canChangeStatus true for the owning Mentor or current assignee of an ACTIVE Project
 * @param canComment true only for an eligible active member or owning Mentor before completion
 * @param canEdit true for the current Leader or self-created current-assignee unfinished Task
 * @param canDelete true for the same definition owner as {@code canEdit}
 * @param canReassign true only for the current Leader on an unfinished Task
 * @param canLogWork true only for the current assignee on an ACTIVE Project
 */
public record TaskDetails(
        TaskView task,
        List<TaskCommentView> comments,
        List<TaskWorkLogView> workLogs,
        Long actorMembershipId,
        boolean canChangeStatus,
        boolean canComment,
        boolean canEdit,
        boolean canDelete,
        boolean canReassign,
        boolean canLogWork) {

    /**
     * Retains the Iteration 1 constructor for callers that do not render definition controls.
     *
     * @param task visible Task
     * @param comments append-only comments
     * @param canChangeStatus status capability
     * @param canComment comment capability
     */
    public TaskDetails(
            TaskView task,
            List<TaskCommentView> comments,
            boolean canChangeStatus,
            boolean canComment) {
        this(task, comments, List.of(), null, canChangeStatus, canComment, false, false, false, false);
    }

    /** Copies the comment list so historical output cannot be modified by a view consumer. */
    public TaskDetails {
        comments = List.copyOf(comments);
        workLogs = List.copyOf(workLogs);
    }
}
