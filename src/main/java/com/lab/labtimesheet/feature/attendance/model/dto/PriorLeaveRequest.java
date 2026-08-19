package com.lab.labtimesheet.feature.attendance.model.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Prior leave request summary with its materialized counted days, newest first.
 *
 * @param id request identifier
 * @param startDate inclusive first local date
 * @param endDate inclusive last local date
 * @param status current decision state
 * @param reason submitted reason
 * @param submittedAt server submission instant
 * @param countedDays frozen eligible workdays in ascending date order
 */
public record PriorLeaveRequest(
        long id,
        LocalDate startDate,
        LocalDate endDate,
        String status,
        String reason,
        Instant submittedAt,
        List<LocalDate> countedDays) {
}