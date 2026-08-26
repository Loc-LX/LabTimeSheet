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
 * @param effortPlanning estimate, lifetime actual, DONE-only signed variance, and estimate capability
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
        boolean canLogWork,
        TaskEffortPlanningView effortPlanning) {

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
        this(task, comments, List.of(), null, canChangeStatus, canComment, false, false, false, false,
                new TaskEffortPlanningView(null, 0,
                        com.lab.labtimesheet.feature.task.model.TaskVarianceState.NOT_ESTIMATED,
                        null, false));
    }

    /** Compatibility constructor for callers that do not yet supply planning facts.
     * @param task visible Task
     * @param comments comment history
     * @param workLogs retained work logs
     * @param actorMembershipId viewer membership, when active
     * @param canChangeStatus status capability
     * @param canComment comment capability
     * @param canEdit definition-edit capability
     * @param canDelete deletion capability
     * @param canReassign reassignment capability
     * @param canLogWork work-log capability
     */
    public TaskDetails(TaskView task, List<TaskCommentView> comments, List<TaskWorkLogView> workLogs,
            Long actorMembershipId, boolean canChangeStatus, boolean canComment, boolean canEdit,
            boolean canDelete, boolean canReassign, boolean canLogWork) {
        this(task, comments, workLogs, actorMembershipId, canChangeStatus, canComment, canEdit,
                canDelete, canReassign, canLogWork,
                new TaskEffortPlanningView(null, 0,
                        com.lab.labtimesheet.feature.task.model.TaskVarianceState.NOT_ESTIMATED,
                        null, false));
    }

    /** Copies the comment list so historical output cannot be modified by a view consumer. */
    public TaskDetails {
        comments = List.copyOf(comments);
        workLogs = List.copyOf(workLogs);
    }
}
