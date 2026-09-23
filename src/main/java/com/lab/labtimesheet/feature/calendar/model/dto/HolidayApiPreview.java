package com.lab.labtimesheet.feature.calendar.model.dto;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable result of an optional HolidayAPI operation. Failure results contain no provider body or credential.
 *
 * @param status actionable operation outcome
 * @param candidates safe provider candidates, empty for failure outcomes
 * @param message fixed operator guidance without external diagnostics
 * @param retrievedAt server instant at which a successful provider response was received, or {@code null} when
 * the provider was not contacted or did not return usable candidates
 */
public record HolidayApiPreview(
        HolidayApiPreviewStatus status,
        List<HolidayApiCandidate> candidates,
        String message,
        Instant retrievedAt) {
    /** Normalizes the list boundary so callers cannot mutate the result. */
    public HolidayApiPreview {
        status = Objects.requireNonNull(status, "status");
        candidates = List.copyOf(candidates == null ? List.of() : candidates);
        message = message == null ? "" : message;
    }
}
