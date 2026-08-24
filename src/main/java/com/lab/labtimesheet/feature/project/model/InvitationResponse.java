package com.lab.labtimesheet.feature.project.model;

/**
 * Authenticated response choices for a pending Project invitation.
 */
// Hai lựa chọn Intern có thể gửi khi phản hồi một lời mời đang PENDING.
public enum InvitationResponse {
    /** Accepts the invitation after the authenticated eligibility recheck. */
    ACCEPT,
    /** Declines the invitation without creating membership. */
    DECLINE
}
