package com.lab.labtimesheet.feature.attendance.model.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable daily Attendance report projection.
 *
 * <p>The policy and schedule fields are the version applied to this local date. Raw checkout is the persisted
 * punch; effective checkout is the approved correction value when present and never replaces that raw punch.
 * Violation flags are mutually exclusive for early departure and missing checkout, while late arrival may coexist
 * with either. A daily score is present only for expected workdays and is a unitless value from zero through one.</p>
 *
 * @param workDate local business date
 * @param classification precedence result for the date
 * @param policyId applied historical policy identifier
 * @param policyEffectiveFrom applied policy effective date
 * @param policyZoneId applied policy timezone
 * @param scheduledStart applied local scheduled start
 * @param scheduledEnd applied local scheduled end
 * @param checkInGraceMinutes applied inclusive late-arrival grace
 * @param checkoutGraceMinutes applied inclusive checkout grace
 * @param violationPenalty applied historical per-violation penalty
 * @param checkInAt raw server check-in, or {@code null} when no row exists
 * @param rawCheckoutAt persisted raw checkout, or {@code null}
 * @param effectiveCheckoutAt approved correction checkout or raw checkout, or {@code null}
 * @param late whether the raw check-in is late under the applied policy
 * @param earlyDeparture whether effective checkout is before scheduled end
 * @param missingCheckout whether the effective checkout is missing after cutoff
 * @param dailyComplianceScore expected-day score, or empty for off-days and leave
 */
public record AttendanceReportDay(
        LocalDate workDate,
        AttendanceReportClassification classification,
        long policyId,
        LocalDate policyEffectiveFrom,
        ZoneId policyZoneId,
        LocalTime scheduledStart,
        LocalTime scheduledEnd,
        int checkInGraceMinutes,
        int checkoutGraceMinutes,
        BigDecimal violationPenalty,
        Instant checkInAt,
        Instant rawCheckoutAt,
        Instant effectiveCheckoutAt,
        boolean late,
        boolean earlyDeparture,
        boolean missingCheckout,
        Optional<BigDecimal> dailyComplianceScore) {

    /** Validates the immutable report boundary and snapshots the optional score. */
    public AttendanceReportDay {
        Objects.requireNonNull(workDate, "workDate");
        Objects.requireNonNull(classification, "classification");
        Objects.requireNonNull(policyEffectiveFrom, "policyEffectiveFrom");
        Objects.requireNonNull(policyZoneId, "policyZoneId");
        Objects.requireNonNull(scheduledStart, "scheduledStart");
        Objects.requireNonNull(scheduledEnd, "scheduledEnd");
        Objects.requireNonNull(violationPenalty, "violationPenalty");
        dailyComplianceScore = Objects.requireNonNull(dailyComplianceScore, "dailyComplianceScore");
        if (earlyDeparture && missingCheckout) {
            throw new IllegalArgumentException("Early departure and missing checkout are mutually exclusive");
        }
        dailyComplianceScore.ifPresent(score -> {
            if (score.signum() < 0 || score.compareTo(BigDecimal.ONE) > 0) {
                throw new IllegalArgumentException("Daily compliance score must be between zero and one");
            }
        });
    }
}
