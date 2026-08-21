package com.lab.labtimesheet.feature.project.model.dto;

import com.lab.labtimesheet.feature.project.model.ProjectExitRequestStatus;
import com.lab.labtimesheet.feature.project.model.ProjectExitRequestType;
import java.time.Instant;

/**
 * Immutable retained membership-exit request facts exposed by Project History.
 *
 * <p>Resolution and request attribution come directly from the retained request row. No synthetic
 * readiness or assignment-event timeline is added to this DTO.</p>
 *
 * @param id retained request identifier
 * @param targetMembershipId membership requested for closure
 * @param requesterMembershipId membership that created the request
 * @param requestType member-leave or leader-removal shape
 * @param reason retained nonblank request reason
 * @param status pending or terminal decision state
 * @param resolutionNote optional stored Mentor decision note
 * @param resolvedAt terminal resolution instant, or null while pending
 * @param resolvedByUserId resolving Mentor/requester, or null for automatic supersession
 * @param createdAt immutable request creation instant
 * @param updatedAt latest stored request update instant
 */
public record ProjectExitRequestHistoryView(
        long id,
        long targetMembershipId,
        long requesterMembershipId,
        ProjectExitRequestType requestType,
        String reason,
        ProjectExitRequestStatus status,
        String resolutionNote,
        Instant resolvedAt,
        Long resolvedByUserId,
        Instant createdAt,
        Instant updatedAt) {
}
