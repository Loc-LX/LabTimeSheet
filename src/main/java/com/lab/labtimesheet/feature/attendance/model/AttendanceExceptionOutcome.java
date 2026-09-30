package com.lab.labtimesheet.feature.attendance.model;

/** Effective outcome recorded by an exception decision. */
public enum AttendanceExceptionOutcome {
    /** The attendance violation is excused. */
    EXCUSED,
    /** The attendance violation remains applicable to compliance. */
    UNEXCUSED
}
