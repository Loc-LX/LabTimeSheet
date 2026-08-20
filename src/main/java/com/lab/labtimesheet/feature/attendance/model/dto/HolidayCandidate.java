package com.lab.labtimesheet.feature.attendance.model.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Non-secret HolidayAPI candidate supplied by the Platform-owned integration client.
 *
 * @param sourceUuid stable upstream identifier used for deduplication
 * @param name upstream holiday name
 * @param actualDate upstream actual holiday date
 * @param observedDate local date shown to operators and used for import
 * @param publicHoliday upstream public-holiday marker, used only as a default selection
 * @param importedAt instant at which the candidate was fetched
 */
public record HolidayCandidate(
        String sourceUuid,
        String name,
        LocalDate actualDate,
        LocalDate observedDate,
        boolean publicHoliday,
        Instant importedAt) {

    /** Validates the provenance required by the local calendar schema. */
    public HolidayCandidate {
        if (sourceUuid == null || sourceUuid.isBlank()) {
            throw new IllegalArgumentException("sourceUuid is required");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        Objects.requireNonNull(actualDate, "actualDate");
        Objects.requireNonNull(observedDate, "observedDate");
        Objects.requireNonNull(importedAt, "importedAt");
        sourceUuid = sourceUuid.strip();
        name = name.strip();
    }
}
