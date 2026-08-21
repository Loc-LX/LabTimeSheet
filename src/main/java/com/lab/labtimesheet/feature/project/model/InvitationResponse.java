package com.lab.labtimesheet.feature.project.model;

/**
 * Authenticated response choices for a pending Project invitation.
 */
public enum InvitationResponse {
    /** Accepts the invitation after the authenticated eligibility recheck. */
    ACCEPT,
    /** Declines the invitation without creating membership. */
    DECLINE
}
