package com.lab.labtimesheet.feature.project.model.dto;

import java.time.Instant;

/**
 * Current or historical Project membership for authorized server-rendered pages.
 *
 * @param membershipId stable membership-interval identifier
 * @param internUserId participating Intern user identifier
 * @param displayName current Account display name
 * @param joinedAt inclusive membership start instant
 * @param leftAt membership end instant, or null while current
 * @param currentLeader true only for the current open-Project Leader membership
 */
public record ProjectMemberView(
        long membershipId,
        long internUserId,
        String displayName,
        Instant joinedAt,
        Instant leftAt,
        boolean currentLeader) {
}
