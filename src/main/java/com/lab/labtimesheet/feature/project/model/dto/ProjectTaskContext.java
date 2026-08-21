package com.lab.labtimesheet.feature.project.model.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * DTO-only Project authorization and lifecycle context consumed by the Task feature.
 *
 * @param projectId Project identifier
 * @param mentorUserId owning Mentor user identifier
 * @param status lifecycle status
 * @param startDate inclusive Project start date
 * @param endDate inclusive Project end date
 * @param currentLeaderMembershipId current Leader membership, or null after completion
 * @param activeMembers eligible current memberships, empty after completion
 * @param pendingExitMembershipIds current membership identifiers with a pending exit request;
 *        existing Task rights remain active, but new/self-assignment must exclude them
 */
public record ProjectTaskContext(
        long projectId,
        long mentorUserId,
        String status,
        LocalDate startDate,
        LocalDate endDate,
        Long currentLeaderMembershipId,
        List<ProjectTaskMemberView> activeMembers,
        Set<Long> pendingExitMembershipIds) {
    /**
     * Defensively snapshots member context so consumers cannot change authorization facts after
     * they were read.
     *
     * @param projectId Project identifier
     * @param mentorUserId owning Mentor user identifier
     * @param status lifecycle status
     * @param startDate inclusive Project start date
     * @param endDate inclusive Project end date
     * @param currentLeaderMembershipId current Leader membership, or null after completion
     * @param activeMembers eligible current memberships, copied and never null
     * @param pendingExitMembershipIds membership identifiers with pending exit requests, copied
     *        and never null
     */
    public ProjectTaskContext {
        activeMembers = List.copyOf(activeMembers);
        pendingExitMembershipIds = Set.copyOf(pendingExitMembershipIds);
    }
}
