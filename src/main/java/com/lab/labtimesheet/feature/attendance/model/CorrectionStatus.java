package com.lab.labtimesheet.feature.attendance.model;

/** Durable missed-checkout correction state. */
public enum CorrectionStatus {
    /** Intern submitted a correction awaiting Mentor decision. */
    PENDING,
    /** Decision deadline elapsed before Mentor action; request remains actionable. */
    OVERDUE,
    /** Mentor accepted the proposed effective checkout. */
    APPROVED,
    /** Mentor or expiry worker rejected the proposal. */
    REJECTED
}
