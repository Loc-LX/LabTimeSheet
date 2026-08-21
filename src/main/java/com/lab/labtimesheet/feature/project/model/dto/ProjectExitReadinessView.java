package com.lab.labtimesheet.feature.project.model.dto;

/**
 * Immutable read-only readiness facts for one pending membership-exit request.
 *
 * <p>Counts are a current display snapshot only; Mentor approval repeats the locked request,
 * leadership, membership, and unfinished-Task checks in its mutation transaction.</p>
 *
 * @param requestId pending exit request identifier
 * @param targetMembershipId pending target membership
 * @param targetIsCurrentLeader whether replacement is required before approval
 * @param unfinishedTaskCount current unfinished non-deleted Task count for the target
 * @param readyForApproval true when the display snapshot has both approval guards satisfied
 */
public record ProjectExitReadinessView(
        long requestId,
        long targetMembershipId,
        boolean targetIsCurrentLeader,
        long unfinishedTaskCount,
        boolean readyForApproval) {
}
