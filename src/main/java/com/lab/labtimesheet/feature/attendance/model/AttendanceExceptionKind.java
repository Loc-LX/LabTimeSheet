package com.lab.labtimesheet.feature.attendance.model;

/** Recorded attendance violation covered by an exception. */
public enum AttendanceExceptionKind {
    /** Arrival occurred after the applicable start. */
    LATE_ARRIVAL,
    /** Departure occurred before the applicable end. */
    EARLY_DEPARTURE
}
