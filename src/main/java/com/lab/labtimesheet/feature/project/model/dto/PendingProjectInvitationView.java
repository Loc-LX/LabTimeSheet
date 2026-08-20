package com.lab.labtimesheet.feature.project.model.dto;

import java.time.Instant;

/**
 * Actionable invitation addressed to the authenticated Intern.
 *
 * @param invitationId invitation identifier used for an authenticated response
 * @param projectId owning Project identifier
 * @param projectName safe Project display name
 * @param issuingLeaderName current display name of the retained issuing-term Intern
 * @param createdAt immutable invitation creation instant
 */
public record PendingProjectInvitationView(
        long invitationId,
        long projectId,
        String projectName,
        String issuingLeaderName,
        Instant createdAt) {
}
