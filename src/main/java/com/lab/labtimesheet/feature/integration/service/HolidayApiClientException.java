package com.lab.labtimesheet.feature.integration.service;

import com.lab.labtimesheet.feature.integration.model.dto.HolidayApiPreviewStatus;

/**
 * Secret-free provider failure classification used to keep external diagnostics out of application responses.
 */
public final class HolidayApiClientException extends RuntimeException {
    private final HolidayApiPreviewStatus status;

    private HolidayApiClientException(HolidayApiPreviewStatus status, String message) {
        super(message);
        this.status = status;
    }

    /** @return actionable safe failure category */
    public HolidayApiPreviewStatus status() {
        return status;
    }

    static HolidayApiClientException invalidKey() {
        return new HolidayApiClientException(HolidayApiPreviewStatus.INVALID_KEY,
                "HolidayAPI rejected the configured key.");
    }

    static HolidayApiClientException rateLimited() {
        return new HolidayApiClientException(HolidayApiPreviewStatus.RATE_LIMITED,
                "HolidayAPI rate limit reached; try again later.");
    }

    static HolidayApiClientException unavailable() {
        return new HolidayApiClientException(HolidayApiPreviewStatus.UNAVAILABLE,
                "HolidayAPI is unavailable; local calendar data remains usable.");
    }
}
