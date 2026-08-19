package com.lab.labtimesheet.feature.integration.model.dto;

import java.time.LocalDate;

/** Provider holiday data safe for an explicit administrator preview. */
public record HolidayApiCandidate(
        String uuid,
        String name,
        LocalDate actualDate,
        LocalDate observedDate,
        boolean publicHoliday) {
}
