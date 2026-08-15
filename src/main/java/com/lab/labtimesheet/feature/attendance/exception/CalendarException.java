package com.lab.labtimesheet.feature.attendance.exception;

/**
 * Signals a rejected global-calendar mutation, including immutable-history and optimistic conflicts.
 */
public final class CalendarException extends RuntimeException {

    /**
     * Creates a calendar rejection with operator-facing context.
     *
     * @param message explanation of the rejected mutation
     */
    public CalendarException(String message) {
        super(message);
    }
}
