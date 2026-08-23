package com.lab.labtimesheet.feature.project.model.dto;

import com.lab.labtimesheet.feature.project.model.InvitationResolutionCode;
import com.lab.labtimesheet.feature.project.model.InvitationStatus;
import java.time.Instant;

/**
 * Immutable retained invitation facts exposed by an authorized Project History read.
 *
 * <p>The view reports the stored issuing-term and response attribution only. It does not infer
 * an invitation timeline from current account or leadership state.</p>
 *
 * @param id retained invitation identifier
 * @param invitedInternUserId intended Intern account identifier
 * @param issuingLeadershipTermId stored issuing leadership-term identifier
 * @param status terminal or pending invitation state
 * @param acceptedMembershipId membership created by acceptance, or null otherwise
 * @param resolvedAt terminal resolution instant, or null while pending
 * @param resolvedByUserId response/management actor, or null for automatic resolution
 * @param resolutionCode retained terminal reason, or null while pending
 * @param createdAt immutable invitation creation instant
 * @param updatedAt latest stored invitation update instant
 */
public record ProjectInvitationHistoryView(
        long id,
        long invitedInternUserId,
        long issuingLeadershipTermId,
        InvitationStatus status,
        Long acceptedMembershipId,
        Instant resolvedAt,
        Long resolvedByUserId,
        InvitationResolutionCode resolutionCode,
        Instant createdAt,
        Instant updatedAt) {
}
