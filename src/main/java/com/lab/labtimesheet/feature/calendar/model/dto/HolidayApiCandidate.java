package com.lab.labtimesheet.feature.calendar.model.dto;

import java.time.LocalDate;

/**
 * Immutable, non-secret HolidayAPI candidate handed to the attendance/calendar producer.
 * The consumer may preselect public holidays, but local Admin decisions remain authoritative.
 *
 * @param uuid provider-stable source identifier
 * @param name provider display name
 * @param actualDate date on which the holiday occurs
 * @param observedDate date on which the holiday is observed
 * @param publicHoliday whether the provider marks this event public
 */
public record HolidayApiCandidate(
        String uuid,
        String name,
        LocalDate actualDate,
        LocalDate observedDate,
        boolean publicHoliday) {
}
