package com.lab.labtimesheet.feature.attendance.exception;

/**
 * Actionable holiday preview/import failure. The message always states the reason
 * and the available manual fallback so an Admin can recover without a live API.
 */
public class HolidayCalendarException extends RuntimeException {

    public HolidayCalendarException(String message) {
        super(message);
    }
}