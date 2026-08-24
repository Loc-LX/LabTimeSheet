package com.lab.labtimesheet.feature.project.model;

/**
 * Retained reason explaining why a Project invitation left the pending state.
 */
// Lý do chi tiết kết thúc invitation để phần History giải thích vì sao invitation không còn PENDING.
public enum InvitationResolutionCode {
    /** The intended Intern accepted and became a member. */
    INVITEE_ACCEPTED,
    /** The intended Intern declined the invitation. */
    INVITEE_DECLINED,
    /** The issuing Leader revoked the pending invitation. */
    INVITER_REVOKED,
    /** The owning Mentor revoked the pending invitation. */
    MENTOR_REVOKED,
    /** The issuing leadership term ended before response. */
    LEADER_CHANGED,
    /** Project completion superseded the pending invitation. */
    PROJECT_COMPLETED,
    /** The intended Intern no longer satisfied membership eligibility. */
    INVITEE_INELIGIBLE,
    /** Mentor direct-add superseded the matching pending invitation. */
    MENTOR_DIRECT_ADD
}
