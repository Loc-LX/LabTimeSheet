package com.lab.labtimesheet.feature.attendance.model.dto;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Non-secret Admin Calendar History projection retaining local decisions and imported provenance.
 *
 * @param id local event identifier
 * @param calendarDate local authoritative date
 * @param name display name
 * @param source local origin, either {@code CUSTOM} or {@code HOLIDAY_API}
 * @param sourceUuid upstream identifier, or {@code null} for custom events
 * @param actualDate imported actual holiday date
 * @param observedDate imported observed holiday date
 * @param publicHoliday upstream public marker
 * @param dayOff local authoritative day-off decision
 * @param importedAt upstream fetch instant
 * @param createdByUserId creator account identifier
 * @param updatedByUserId last editor account identifier
 * @param createdAt local creation instant
 * @param updatedAt local update instant
 * @param version optimistic version
 */
public record CalendarHistoryItem(
        long id,
        LocalDate calendarDate,
        String name,
        String source,
        String sourceUuid,
        LocalDate actualDate,
        LocalDate observedDate,
        Boolean publicHoliday,
        boolean dayOff,
        Instant importedAt,
        long createdByUserId,
        long updatedByUserId,
        Instant createdAt,
        Instant updatedAt,
        long version) {}
