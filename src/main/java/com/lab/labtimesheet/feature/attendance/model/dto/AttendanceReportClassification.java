package com.lab.labtimesheet.feature.attendance.model.dto;

/**
 * Daily Attendance classification in the precedence used by the report query.
 */
public enum AttendanceReportClassification {
    /** Imported day-off event applies to the local date. */
    HOLIDAY,
    /** A configured or locally recorded non-imported day-off applies. */
    OFF_DAY,
    /** An approved frozen leave allocation applies. */
    APPROVED_LEAVE,
    /** A raw attendance row exists for an applicable workday. */
    PRESENT,
    /** No attendance row exists for an applicable workday. */
    ABSENT
}
