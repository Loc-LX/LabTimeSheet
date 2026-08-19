package com.lab.labtimesheet.feature.attendance.model.dto;

import java.time.LocalDate;

/**
 * One interpreted Vietnamese holiday candidate returned by the HolidayAPI client.
 * The transport (HTTP, credentials) is owned by the platform feature; attendance
 * interprets the result into this immutable value.
 *
 * @param uuid source-side stable identifier used for deduplication
 * @param name holiday name
 * @param actualDate actual calendar date of the holiday
 * @param observedDate local business date on which the holiday is observed
 * @param publicHoliday whether the source marks it as a public holiday
 */
public record HolidayCandidate(
        String uuid,
        String name,
        LocalDate actualDate,
        LocalDate observedDate,
        boolean publicHoliday) {}