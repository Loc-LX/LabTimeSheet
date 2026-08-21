package com.lab.labtimesheet.feature.attendance.model.dto;

import java.time.Instant;

/**
 * Bounded list row for a discoverable missed-checkout correction workflow.
 *
 * @param id correction identifier
 * @param attendanceRecordId attached raw attendance row
 * @param internUserId owning Intern account
 * @param requestedCheckoutAt proposed effective checkout instant
 * @param reason normalized retained reason
 * @param status current correction state name
 * @param submittedAt server submission instant
 * @param decisionDeadline exclusive Mentor decision deadline
 */
public record CorrectionSummary(
        long id,
        long attendanceRecordId,
        long internUserId,
        Instant requestedCheckoutAt,
        String reason,
        String status,
        Instant submittedAt,
        Instant decisionDeadline) {
}
