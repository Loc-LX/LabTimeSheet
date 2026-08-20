package com.lab.labtimesheet.feature.attendance.model.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Prior leave request summary with its materialized counted days and boundary-based actions, newest first.
 *
 * @param id request identifier
 * @param startDate inclusive first local date
 * @param endDate inclusive last local date
 * @param status current decision state
 * @param reason submitted reason
 * @param submittedAt server submission instant
 * @param firstCountedStartAt first counted workday's scheduled start, the edit/cancellation boundary
 * @param editable whether the Intern may edit this pending request before the boundary
 * @param cancellable whether the Intern may cancel this pending/approved request before the boundary
 * @param countedDays frozen eligible workdays in ascending date order
 */
public record PriorLeaveRequest(
        long id,
        LocalDate startDate,
        LocalDate endDate,
        String status,
        String reason,
        Instant submittedAt,
        Instant firstCountedStartAt,
        boolean editable,
        boolean cancellable,
        List<LocalDate> countedDays) {
}