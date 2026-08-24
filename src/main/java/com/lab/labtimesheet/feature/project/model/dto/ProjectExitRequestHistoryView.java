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
 * @param reasonText retained nonblank request reason text
 * @param status pending or terminal decision state
 * @param resolutionNote optional stored Mentor decision note
 * @param resolvedAt terminal resolution instant, or null while pending
 * @param resolvedByUserId resolving Mentor/requester, or null for automatic supersession
 * @param createdAt immutable request creation instant
 * @param updatedAt latest stored request update instant
 */
// DTO một dòng lịch sử yêu cầu exit, gồm người gửi/đối tượng, trạng thái và quyết định cuối.
// Dùng cho tab Exit decisions sau khi QueryService kiểm tra người xem có quyền xem Project.
public record ProjectExitRequestHistoryView(
        long id,
        long targetMembershipId,
        long requesterMembershipId,
        ProjectExitRequestType requestType,
        String reasonText,
        ProjectExitRequestStatus status,
        String resolutionNote,
        Instant resolvedAt,
        Long resolvedByUserId,
        Instant createdAt,
        Instant updatedAt) {
}
