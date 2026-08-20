package com.lab.labtimesheet.feature.attendance.model;

/** Durable leave-request state stored in the V1 checked text column. */
public enum LeaveStatus {
    /** Intern submitted the request and a Mentor has not decided before its first counted start. */
    PENDING,
    /** Mentor approved the frozen eligible-day allocations. */
    APPROVED,
    /** Mentor rejected the request or an unresolved request crossed its first counted start. */
    REJECTED,
    /** Intern cancelled the request before its first counted start. */
    CANCELLED
}
