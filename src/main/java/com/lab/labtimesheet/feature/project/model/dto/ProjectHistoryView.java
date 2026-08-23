package com.lab.labtimesheet.feature.project.model.dto;

import com.lab.labtimesheet.feature.task.model.dto.TaskHistoryView;
import java.util.List;
import java.util.Map;

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
 * @param usernamesByUserId Project-only presentation map for retained account identifiers; Intern values include
 *                          the Student Code in {@code Username (Student Code)} form
 * @param usernamesByMembershipId Project-only presentation map for retained membership identifiers
 * @param usernamesByLeadershipTermId Project-only presentation map for retained leadership-term identifiers
 */
public record ProjectHistoryView(
        long projectId,
        List<ProjectMemberView> memberships,
        List<ProjectLeadershipTermView> leadership,
        List<ProjectInvitationHistoryView> invitations,
        List<ProjectExitRequestHistoryView> exitRequests,
        List<TaskHistoryView> tasks,
        Map<Long, String> usernamesByUserId,
        Map<Long, String> usernamesByMembershipId,
        Map<Long, String> usernamesByLeadershipTermId) {

    /** Keeps the original DTO construction shape for non-MVC consumers. */
    public ProjectHistoryView(
            long projectId,
            List<ProjectMemberView> memberships,
            List<ProjectLeadershipTermView> leadership,
            List<ProjectInvitationHistoryView> invitations,
            List<ProjectExitRequestHistoryView> exitRequests,
            List<TaskHistoryView> tasks) {
        this(projectId, memberships, leadership, invitations, exitRequests, tasks,
                Map.of(), Map.of(), Map.of());
    }

    /** Copies every child collection to keep the history response immutable. */
    public ProjectHistoryView {
        memberships = List.copyOf(memberships);
        leadership = List.copyOf(leadership);
        invitations = List.copyOf(invitations);
        exitRequests = List.copyOf(exitRequests);
        tasks = List.copyOf(tasks);
        usernamesByUserId = Map.copyOf(usernamesByUserId);
        usernamesByMembershipId = Map.copyOf(usernamesByMembershipId);
        usernamesByLeadershipTermId = Map.copyOf(usernamesByLeadershipTermId);
    }
}
