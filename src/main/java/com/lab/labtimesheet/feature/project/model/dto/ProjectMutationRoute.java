package com.lab.labtimesheet.feature.project.model.dto;

/**
 * Immutable routing facts used before a Project mutation acquires its aggregate lock.
 *
 * <p>The route contains scalar identifiers only. It must never hydrate a filtered Project
 * aggregate before Account-owned lifecycle locks are retained.</p>
 *
 * @param projectId Project identifier
 * @param mentorUserId owning Mentor account identifier
 * @param currentLeaderUserId current Leader account identifier, or null for completed history
 */
public record ProjectMutationRoute(
        long projectId,
        long mentorUserId,
        Long currentLeaderUserId) {
}
