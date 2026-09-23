package com.lab.labtimesheet.feature.calendar.model.dto;

import com.lab.labtimesheet.feature.calendar.model.dto.HolidayApiCandidate;
import java.time.Instant;

/**
 * Admin review row for a HolidayAPI candidate. Public status influences only the initial checkbox state.
 *
 * @param candidate non-secret Platform-owned upstream candidate and provenance
 * @param selectedByDefault initial local day-off choice
 * @param retrievedAt server instant at which Platform received the successful preview
 */
public record CalendarPreviewItem(
        HolidayApiCandidate candidate, boolean selectedByDefault, Instant retrievedAt) {}
