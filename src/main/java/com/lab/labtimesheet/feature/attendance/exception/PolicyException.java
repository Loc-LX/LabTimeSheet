package com.lab.labtimesheet.feature.attendance.exception;

/**
 * Signals a rejected attendance-policy scheduling or replacement attempt.
 */
public class PolicyException extends RuntimeException {

    /**
     * Creates a policy rejection with a human-readable reason.
     *
     * @param message stable business message for the rejected attempt
     */
    public PolicyException(String message) {
        super(message);
    }
}