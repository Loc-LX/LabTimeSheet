package com.lab.labtimesheet.feature.project.model.dto;

import java.time.LocalDate;
import java.util.List;

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
 */
public record ProjectTaskContext(
        long projectId,
        long mentorUserId,
        String status,
        LocalDate startDate,
        LocalDate endDate,
        Long currentLeaderMembershipId,
        List<ProjectTaskMemberView> activeMembers) {

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
     */
    public ProjectTaskContext {
        activeMembers = List.copyOf(activeMembers);
    }
}
