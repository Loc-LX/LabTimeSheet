package com.lab.labtimesheet.feature.attendance.model;

/** Durable leave-request state stored in the V1 checked text column. */
public enum LeaveStatus {
    /** Intern submitted the request and a Mentor has not decided before its first counted start. */
    PENDING,
    /** The first counted start passed without a decision; the request remains reserved and decidable. */
    OVERDUE,
    /** Mentor approved the frozen eligible-day allocations. */
    APPROVED,
    /** Mentor rejected the request. */
    REJECTED,
    /** Intern withdrew an undecided request before or after its first counted start. */
    WITHDRAWN,
    /** Intern cancelled an approved request before its first counted start. */
    CANCELLED
}
