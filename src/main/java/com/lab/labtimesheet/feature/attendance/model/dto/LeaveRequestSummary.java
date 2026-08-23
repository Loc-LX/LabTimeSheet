package com.lab.labtimesheet.feature.attendance.model.dto;

import com.lab.labtimesheet.feature.attendance.model.LeaveStatus;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Bounded list row for a discoverable leave workflow.
 *
 * @param id request identifier
 * @param internUserId owning Intern account
 * @param startDate inclusive requested start
 * @param endDate inclusive requested end
 * @param reason normalized retained reason
 * @param status current request state
 * @param submittedAt server submission instant
 */
public record LeaveRequestSummary(
        long id,
        long internUserId,
        LocalDate startDate,
        LocalDate endDate,
        String reason,
        LeaveStatus status,
        Instant submittedAt) {
}
