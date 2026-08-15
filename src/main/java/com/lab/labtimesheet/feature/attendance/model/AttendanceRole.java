package com.lab.labtimesheet.feature.attendance.model;

/**
 * Global account roles recognized by attendance authorization rules.
 */
public enum AttendanceRole {
    /** Global system administrator. */
    ADMIN,
    /** Global laboratory Mentor. */
    MENTOR,
    /** Internship participant. */
    INTERN
}
