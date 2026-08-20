package com.lab.labtimesheet.feature.attendance.model.dto;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Persistence-free global calendar event returned to controllers and feature consumers.
 *
 * @param id stable event identifier
 * @param date local business date of the event
 * @param name operator-provided display name
 * @param dayOff whether this event makes the date globally exempt
 * @param version optimistic version required by update requests
 * @param source origin of the event: {@code CUSTOM} or {@code HOLIDAY_API}
 * @param sourceUuid source-side identifier of an imported event, otherwise {@code null}
 * @param actualDate actual holiday date when imported, otherwise {@code null}
 * @param observedDate local observed holiday date when imported, otherwise {@code null}
 * @param publicHoliday source public-holiday marker when imported, otherwise {@code null}
 * @param importedAt server instant of the import, otherwise {@code null}
 */
public record GlobalCalendarEvent(
        long id,
        LocalDate date,
        String name,
        boolean dayOff,
        long version,
        String source,
        String sourceUuid,
        LocalDate actualDate,
        LocalDate observedDate,
        Boolean publicHoliday,
        Instant importedAt) {}