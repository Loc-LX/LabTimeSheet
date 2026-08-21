package com.lab.labtimesheet.feature.project.model;

/**
 * Persisted lifecycle of a Project membership-exit request.
 */
public enum ProjectExitRequestStatus {
    /** Request awaits redistribution and owning-Mentor decision. */
    PENDING,
    /** Owning Mentor approved and the membership interval was closed. */
    APPROVED,
    /** Owning Mentor rejected the request without closing membership. */
    REJECTED,
    /** Original requester cancelled the request. */
    CANCELLED,
    /** Project completion superseded the request. */
    SUPERSEDED
}
