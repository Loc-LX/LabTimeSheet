package com.lab.labtimesheet.feature.attendance.exception;

/** Signals an invalid, unauthorized, stale, or expired missed-checkout correction operation. */
public final class CorrectionException extends RuntimeException {

    /**
     * Creates a correction rejection with operator-safe context.
     *
     * @param message safe user-facing failure reason
     */
    public CorrectionException(String message) {
        super(message);
    }

    /**
     * Creates a correction rejection while retaining a persistence cause for diagnostics.
     *
     * @param message safe user-facing failure reason
     * @param cause underlying persistence or concurrency failure
     */
    public CorrectionException(String message, Throwable cause) {
        super(message, cause);
    }
}
