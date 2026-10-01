package com.lab.labtimesheet.feature.attendance.model;

/** Origin of an attendance exception. */
public enum AttendanceExceptionSource {
    /** Intern submitted an excuse request. */
    REQUEST,
    /** Mentor recorded a direct excuse mark. */
    MENTOR_MARK
}
