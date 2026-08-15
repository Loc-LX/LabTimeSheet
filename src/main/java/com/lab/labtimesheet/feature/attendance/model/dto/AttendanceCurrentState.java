package com.lab.labtimesheet.feature.attendance.model.dto;

/**
 * Presentation-safe current business-date punch state exposed across feature boundaries.
 */
public enum AttendanceCurrentState {
    /** No attendance row exists for the current business date. */
    NOT_CHECKED_IN,
    /** A row exists without raw checkout. */
    CHECKED_IN,
    /** A row exists with its accepted raw checkout. */
    CHECKED_OUT
}
