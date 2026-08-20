package com.lab.labtimesheet.feature.attendance.model.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * One leave request shown on the Mentor decisions page with its decision boundary.
 *
 * @param id request identifier
 * @param internUserId owning Intern account identifier
 * @param internDisplayName non-secret Intern display name
 * @param startDate inclusive first local date
 * @param endDate inclusive last local date
 * @param reason submitted reason
 * @param status current decision state
 * @param firstCountedStartAt first counted workday's scheduled start, the decision/cancellation boundary
 * @param countedDays frozen eligible workdays in ascending date order
 */
public record LeaveDecisionRow(
        long id,
        long internUserId,
        String internDisplayName,
        LocalDate startDate,
        LocalDate endDate,
        String reason,
        String status,
        Instant firstCountedStartAt,
        List<LocalDate> countedDays) {
}