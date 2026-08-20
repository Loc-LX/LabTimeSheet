package com.lab.labtimesheet.feature.project.model.dto;

import com.lab.labtimesheet.feature.task.model.dto.TaskHistoryView;
import java.util.List;

/**
 * One authorized, read-only Project History snapshot assembled from retained feature-owned rows.
 *
 * <p>Memberships, leadership terms, invitations, exit decisions, and Task child history are
 * returned as stored facts. The snapshot deliberately has no fabricated previous-assignee,
 * status-event, or generic audit timeline.</p>
 *
 * @param projectId Project identifier
 * @param memberships retained membership intervals
 * @param leadership retained leadership terms
 * @param invitations retained invitation rows
 * @param exitRequests retained membership-exit request rows
 * @param tasks retained Task/comment/work-log rows supplied by the Task public DTO boundary
 */
public record ProjectHistoryView(
        long projectId,
        List<ProjectMemberView> memberships,
        List<ProjectLeadershipTermView> leadership,
        List<ProjectInvitationHistoryView> invitations,
        List<ProjectExitRequestHistoryView> exitRequests,
        List<TaskHistoryView> tasks) {

    /** Copies every child collection to keep the history response immutable. */
    public ProjectHistoryView {
        memberships = List.copyOf(memberships);
        leadership = List.copyOf(leadership);
        invitations = List.copyOf(invitations);
        exitRequests = List.copyOf(exitRequests);
        tasks = List.copyOf(tasks);
    }
}
