package com.lab.labtimesheet.feature.attendance.model.dto;

import java.util.Objects;

/**
 * Admin's explicit decision for one preview candidate.
 *
 * @param candidate candidate to copy locally
 * @param dayOff local authoritative day-off decision, independent of public status
 */
public record CalendarImportSelection(HolidayCandidate candidate, boolean dayOff) {

    /** Rejects a missing candidate before persistence. */
    public CalendarImportSelection {
        Objects.requireNonNull(candidate, "candidate");
    }
}
