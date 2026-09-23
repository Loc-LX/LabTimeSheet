package com.lab.labtimesheet.feature.calendar.model.dto;

/** Actionable, secret-free outcomes of a HolidayAPI preview or draft test. */
public enum HolidayApiPreviewStatus {
    SUCCESS,
    ABSENT_KEY,
    INVALID_KEY,
    RATE_LIMITED,
    UNAVAILABLE
}
