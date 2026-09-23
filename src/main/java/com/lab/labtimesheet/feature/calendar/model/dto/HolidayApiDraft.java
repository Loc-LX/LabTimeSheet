package com.lab.labtimesheet.feature.calendar.model.dto;

/**
 * Request-local HolidayAPI draft input. The key is accepted only for encryption and is never a response field.
 * Country is deliberately absent because the integration is fixed to Vietnam (`VN`).
 *
 * @param apiKey new provider key
 */
public record HolidayApiDraft(String apiKey) {
    /** Validates and trims the request-local provider key. */
    public HolidayApiDraft {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("HolidayAPI key must not be blank");
        }
        apiKey = apiKey.trim();
    }
}
