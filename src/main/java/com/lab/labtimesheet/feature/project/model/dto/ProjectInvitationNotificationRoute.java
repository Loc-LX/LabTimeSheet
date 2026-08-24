package com.lab.labtimesheet.feature.project.model.dto;

/**
 * Immutable scalar recipient route for invitation lifecycle notifications.
 *
 * <p>This projection is intentionally separate from the invitation mutation route. It reads
 * only the Account identifiers that may be referenced by a notification, so callers can retain
 * every Account/profile lock before acquiring the Project lock without hydrating an invitation
 * or partially initializing the Project aggregate.</p>
 *
 * @param projectId owning Project identifier
 * @param invitedInternUserId invited Intern account identifier
 * @param issuingLeaderUserId Leader account that issued the invitation
 * @param mentorUserId owning Mentor account identifier
 */
// DTO nội bộ xác định các account cần nhận notification liên quan đến một invitation.
// Service dùng ID này để gửi thông báo sau khi trạng thái invitation đã được thay đổi thành công.
public record ProjectInvitationNotificationRoute(
        long projectId,
        long invitedInternUserId,
        long issuingLeaderUserId,
        long mentorUserId) {
}
