package com.lab.labtimesheet.feature.task.model.dto;

import java.util.List;

/**
 * Authorized Task detail view and server-derived action capabilities.
 *
 * @param task visible non-deleted Task
 * @param comments append-only comment history in creation order
 * @param canChangeStatus true only for the current assignee of an ACTIVE Project
 * @param canComment true only for an eligible active member or owning Mentor before completion
 */
public record TaskDetails(
        TaskView task,
        List<TaskCommentView> comments,
        boolean canChangeStatus,
        boolean canComment) {

    /** Copies the comment list so historical output cannot be modified by a view consumer. */
    public TaskDetails {
        comments = List.copyOf(comments);
    }
}
