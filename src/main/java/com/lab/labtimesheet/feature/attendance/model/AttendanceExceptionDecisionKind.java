package com.lab.labtimesheet.feature.attendance.model;

/** Kind of append-only attendance exception history entry. */
public enum AttendanceExceptionDecisionKind {
    /** Initial decision. */
    DECISION,
    /** Permitted note or direct-mark reason amendment. */
    AMENDMENT,
    /** Change to the effective outcome. */
    REVERSAL
}
