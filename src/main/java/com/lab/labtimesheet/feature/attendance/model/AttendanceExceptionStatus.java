package com.lab.labtimesheet.feature.attendance.model;

/** Current persisted state of an attendance exception. */
public enum AttendanceExceptionStatus {
    /** Awaiting a Mentor decision. */
    PENDING,
    /** Decision deadline elapsed; still awaiting a Mentor decision. */
    OVERDUE,
    /** Current effective decision excuses the violation. */
    EXCUSED,
    /** Current effective decision does not excuse the violation. */
    UNEXCUSED
}
