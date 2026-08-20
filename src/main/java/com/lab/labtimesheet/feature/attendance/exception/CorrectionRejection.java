package com.lab.labtimesheet.feature.attendance.exception;

/**
 * Stable business outcomes for missed-checkout correction operations, including eligibility, record-state,
 * submission-window, and proposed-value failures.
 */
public enum CorrectionRejection {
    /** The owning Intern account is not active. */
    INACTIVE_INTERN,
    /** The command is missing required values or the reason is blank. */
    INVALID_REQUEST,
    /** No attendance record exists for the submitted work date. */
    NO_ATTENDANCE_RECORD,
    /** The attendance record already contains a raw checkout that can never be corrected. */
    HAS_RAW_CHECKOUT,
    /** At most one correction request may exist per attendance record. */
    ALREADY_SUBMITTED,
    /** Submission is only allowed strictly after the attached-policy inclusive checkout cutoff. */
    TOO_EARLY,
    /** Submission is only allowed through the inclusive scheduled-end-plus-24-hours deadline. */
    DEADLINE_PASSED,
    /** The proposed checkout must be strictly after the raw check-in. */
    PROPOSED_BEFORE_CHECKIN,
    /** The proposed checkout must not be in the future at submission. */
    PROPOSED_IN_FUTURE,
    /** No correction exists for the given identifier. */
    NOT_FOUND,
    /** The deciding account is not an active Mentor. */
    INACTIVE_MENTOR,
    /** The correction is not in a state the operation may transition from. */
    INVALID_STATE,
    /** The decision window has passed and no decision may be made or reverted. */
    DECISION_WINDOW_PASSED,
    /** The correction outcome is locked and can never be changed again. */
    LOCKED,
    /** Another Mentor decided the correction first; reload and re-decide. */
    CONCURRENT_DECISION
}