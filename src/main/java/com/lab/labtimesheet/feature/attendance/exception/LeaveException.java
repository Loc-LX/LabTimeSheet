package com.lab.labtimesheet.feature.attendance.exception;

/**
 * Signals a rejected leave request or state lookup with a stable domain reason.
 */
public final class LeaveException extends RuntimeException {

    /** Stable reason preserved for controller and service consumers. */
    private final LeaveRejection rejection;

    /**
     * Creates an exception for the rejection that callers may safely translate to UI feedback.
     *
     * @param rejection stable reason for refusing the leave operation
     */
    public LeaveException(LeaveRejection rejection) {
        super(rejection.name());
        this.rejection = rejection;
    }

    /**
     * Returns the stable rejection reason without exposing persistence failures.
     *
     * @return leave rejection reason
     */
    public LeaveRejection rejection() {
        return rejection;
    }
}
