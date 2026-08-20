package com.lab.labtimesheet.feature.attendance.model.dto;

import java.util.Objects;

/**
 * Admin's explicit decision for one candidate in a server-provided HolidayAPI preview.
 *
 * <p>The request carries only the provider-stable identity and the local day-off decision. Candidate fields and
 * preview provenance are resolved from the server-trusted Platform snapshot before any local persistence.</p>
 *
 * @param sourceUuid provider-stable source identity selected by the Admin
 * @param dayOff local authoritative day-off decision, independent of public status
 */
public record CalendarImportSelection(String sourceUuid, boolean dayOff) {

    /** Rejects a missing or blank source identity at the request boundary. */
    public CalendarImportSelection {
        sourceUuid = Objects.requireNonNull(sourceUuid, "sourceUuid").strip();
        if (sourceUuid.isBlank()) {
            throw new IllegalArgumentException("sourceUuid must not be blank");
        }
    }
}
