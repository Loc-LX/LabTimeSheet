package com.lab.labtimesheet.feature.attendance.exception;

/** Signals an invalid, unauthorized, overlapping, or expired leave operation. */
public final class LeaveException extends RuntimeException {

    /**
     * Creates a leave operation rejection with operator-safe context.
     *
     * @param message safe user-facing failure reason
     */
    public LeaveException(String message) {
        super(message);
    }

    /**
     * Creates a leave rejection while retaining the persistence cause for diagnostics.
     *
     * @param message safe user-facing failure reason
     * @param cause underlying persistence or concurrency failure
     */
    public LeaveException(String message, Throwable cause) {
        super(message, cause);
    }
}
