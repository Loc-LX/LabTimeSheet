package com.lab.labtimesheet.feature.project.model;

/**
 * Persisted lifecycle of a Project invitation.
 */
public enum InvitationStatus {
    /** Invitation is awaiting the intended Intern's response. */
    PENDING,
    /** Invitation was accepted and linked to the created membership. */
    ACCEPTED,
    /** Invitation was declined by the intended Intern. */
    DECLINED,
    /** Invitation was revoked by an authorized Project actor or lifecycle transition. */
    REVOKED,
    /** Invitation was superseded by a direct Mentor membership addition. */
    SUPERSEDED
}
