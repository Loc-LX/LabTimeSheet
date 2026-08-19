package com.lab.labtimesheet.feature.attendance.exception;

/**
 * Stable business outcomes for full-day leave operations, including materialization and quota failures.
 */
public enum LeaveRejection {
    /** The request range or reason does not satisfy the leave form contract. */
    INVALID_REQUEST,
    /** The account or internship is not active. */
    INACTIVE_INTERN,
    /** No eligible workday remains after excluding non-workdays, day-offs, and out-of-internship dates. */
    NO_COUNTED_DAYS,
    /** The pending/approved reservation for an affected month would exceed its monthly quota snapshot. */
    QUOTA_EXCEEDED
}
