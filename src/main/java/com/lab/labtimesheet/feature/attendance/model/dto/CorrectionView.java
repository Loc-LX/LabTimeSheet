package com.lab.labtimesheet.feature.attendance.model.dto;

import com.lab.labtimesheet.feature.calendar.model.AttendancePolicy;
import com.lab.labtimesheet.feature.attendance.model.AttendanceViolations;
import com.lab.labtimesheet.feature.attendance.model.CorrectionStatus;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Correction view retaining raw-versus-effective checkout distinction and immutable event history.
 *
 * @param id correction identifier
 * @param attendanceRecordId attached attendance row identifier
 * @param internUserId owning Intern
 * @param rawCheckoutAt uneditable raw checkout, normally null for a correction
 * @param proposedCheckout local proposal rendered in the policy timezone
 * @param effectiveCheckoutAt approved effective checkout, without mutating raw data
 * @param reason submission reason
 * @param status current state
 * @param submittedAt submission instant
 * @param submissionDeadline inclusive submission deadline
 * @param decisionDeadline exclusive decision expiry boundary
 * @param lockedAt final lock instant, if expired
 * @param policy historical policy attached to the attendance row
 * @param violations effective violation flags
 * @param events append-only ordered transition events
 */
public record CorrectionView(
        long id,
        long attendanceRecordId,
        long internUserId,
        Instant rawCheckoutAt,
        LocalDateTime proposedCheckout,
        Instant effectiveCheckoutAt,
        String reason,
        CorrectionStatus status,
        Instant submittedAt,
        Instant submissionDeadline,
        Instant decisionDeadline,
        Instant lockedAt,
        AttendancePolicy policy,
        AttendanceViolations violations,
        List<CorrectionEventView> events) {}
