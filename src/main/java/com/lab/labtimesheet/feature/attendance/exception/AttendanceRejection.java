package com.lab.labtimesheet.feature.attendance.exception;

/**
 * Stable business outcomes for attendance operations, including idempotency and eligibility failures.
 */
public enum AttendanceRejection {
    /** The account or internship is not active for the work date. */
    INACTIVE_INTERN,
    /** The attached policy does not configure the date's weekday for attendance. */
    NON_WORKDAY,
    /** The authoritative global calendar exempts the date. */
    GLOBAL_DAY_OFF,
    /** An approved leave request has a frozen allocation for the exact date. */
    APPROVED_LEAVE,
    /** A row already exists for the Intern and work date. */
    ALREADY_CHECKED_IN,
    /** No row exists for the current work date. */
    NO_ATTENDANCE_RECORD,
    /** The row already contains its first raw checkout. */
    ALREADY_CHECKED_OUT,
    /** The attached-policy inclusive checkout cutoff has passed. */
    CHECKOUT_CUTOFF_PASSED
}
