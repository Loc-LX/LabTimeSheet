package com.lab.labtimesheet.feature.attendance.exception;

/** Signals an invalid or unauthorized effective-dated attendance-policy operation. */
public final class PolicyException extends RuntimeException {

    /**
     * Creates a policy operation rejection.
     *
     * @param message operator-safe explanation
     */
    public PolicyException(String message) {
        super(message);
    }
}
