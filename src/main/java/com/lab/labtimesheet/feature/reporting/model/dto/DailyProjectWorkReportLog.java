package com.lab.labtimesheet.feature.reporting.model.dto;

import java.time.LocalDate;

/**
 * One retained Task work log shown in its historical author group.
 *
 * @param id retained work-log identifier
 * @param workDate selected local Report date
 * @param description retained note, or {@code N/A} when no note was stored
 * @param minutes logged whole minutes
 */
public record DailyProjectWorkReportLog(
        long id,
        LocalDate workDate,
        String description,
        int minutes) {
}
