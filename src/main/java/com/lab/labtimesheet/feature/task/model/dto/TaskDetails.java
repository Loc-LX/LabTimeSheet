package com.lab.labtimesheet.feature.task.model.dto;

import java.util.List;

/**
 * Authorized Task detail view and server-derived action capabilities.
 *
 * @param task visible non-deleted Task
 * @param comments append-only comment history in creation order
 * @param workLogs dated effort history in work-date order
 * @param canChangeStatus true only for the current assignee of an ACTIVE Project
 * @param canComment true only for an eligible active member or owning Mentor before completion
 * @param canLogWork true only for the current assignee of an ACTIVE Project
 * @param canReassign true only for the current Leader of an ACTIVE Project with an unfinished Task
 * @param canEdit true only for a Leader or self-Task creator of an unfinished non-deleted Task
 * @param canDelete true only for a Leader or self-Task creator of an unfinished non-deleted Task
 * @param deleted true when the Task row is soft-deleted and this is a historical inspection
 * @param actorMembershipId current active membership of the actor, or null when not a member
 */
public record TaskDetails(
        TaskView task,
        List<TaskCommentView> comments,
        List<WorkLogView> workLogs,
        boolean canChangeStatus,
        boolean canComment,
        boolean canLogWork,
        boolean canReassign,
        boolean canEdit,
        boolean canDelete,
        boolean deleted,
        Long actorMembershipId) {

    /**
     * Copies both history lists so view output cannot be modified by a consumer.
     *
     * @param task visible or historical Task
     * @param comments append-only comment history in creation order
     * @param workLogs dated effort history in work-date order
     * @param canChangeStatus current-assignee ACTIVE status capability
     * @param canComment active-member or Mentor comment capability
     * @param canLogWork current-assignee ACTIVE work-log capability
     * @param canReassign current-Leader ACTIVE unfinished-Task reassignment capability
     * @param canEdit Leader or self-Task creator definition edit capability
     * @param canDelete Leader or self-Task creator soft-delete capability
     * @param deleted historical-inspection indicator for a soft-deleted Task
     * @param actorMembershipId current active membership of the actor, or null
     */
    public TaskDetails {
        comments = List.copyOf(comments);
        workLogs = List.copyOf(workLogs);
    }
}
