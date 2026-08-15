package com.lab.labtimesheet.feature.attendance.exception;

/**
 * Signals a rejected attendance punch or state lookup with a stable domain reason.
 */
public final class AttendanceException extends RuntimeException {

    /** Stable reason preserved for controller and service consumers. */
    private final AttendanceRejection rejection;

    /**
     * Creates an exception for the rejection that callers may safely translate to UI feedback.
     *
     * @param rejection stable reason for refusing the attendance operation
     */
    public AttendanceException(AttendanceRejection rejection) {
        super(rejection.name());
        this.rejection = rejection;
    }

    /**
     * Returns the stable rejection reason without exposing persistence failures.
     *
     * @return attendance rejection reason
     */
    public AttendanceRejection rejection() {
        return rejection;
    }
}
