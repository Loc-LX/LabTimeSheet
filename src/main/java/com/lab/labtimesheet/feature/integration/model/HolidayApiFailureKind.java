package com.lab.labtimesheet.feature.integration.model;

/** Safe provider/configuration outcome exposed without raw credentials or transport diagnostics. */
public enum HolidayApiFailureKind {
    NOT_CONFIGURED,
    INVALID_KEY,
    RATE_LIMITED,
    UNAVAILABLE
}
