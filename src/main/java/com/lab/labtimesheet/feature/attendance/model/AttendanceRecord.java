package com.lab.labtimesheet.feature.attendance.model;

import com.lab.labtimesheet.feature.attendance.exception.AttendanceException;
import com.lab.labtimesheet.feature.attendance.exception.AttendanceRejection;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * One Intern's immutable raw check-in and optional raw checkout for a local work date.
 * The attached policy version permanently determines schedule boundaries and violation interpretation.
 *
 * @param internId Intern account identifier
 * @param workDate policy-local date derived when check-in was accepted
 * @param policy historical policy version attached at check-in
 * @param checkInAt uneditable raw server check-in instant
 * @param checkOutAt uneditable raw server checkout instant, or {@code null} until accepted
 */
public record AttendanceRecord(
        long internId,
        LocalDate workDate,
        AttendancePolicy policy,
        Instant checkInAt,
        Instant checkOutAt) {

    /**
     * Validates required historical fields while preserving a nullable raw checkout.
     */
    public AttendanceRecord {
        Objects.requireNonNull(workDate, "workDate");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(checkInAt, "checkInAt");
    }

    /**
     * Returns a copy with the first raw checkout when it is at or before the attached-policy cutoff.
     * Repeated or post-cutoff attempts are rejected without changing the original record.
     *
     * @param at authoritative server instant
     * @return record containing the accepted raw checkout
     */
    public AttendanceRecord checkOut(Instant at) {
        Objects.requireNonNull(at, "at");
        if (checkOutAt != null) {
            throw new AttendanceException(AttendanceRejection.ALREADY_CHECKED_OUT);
        }
        if (at.isAfter(checkoutCutoff())) {
            throw new AttendanceException(AttendanceRejection.CHECKOUT_CUTOFF_PASSED);
        }
        return new AttendanceRecord(internId, workDate, policy, checkInAt, at);
    }

    /**
     * Classifies violations using the attached policy and an authoritative observation instant.
     * A missing checkout appears only after the inclusive cutoff and never implies early departure.
     *
     * @param observedAt instant at which missing-checkout status is evaluated
     * @return independent violation flags for presentation and reporting
     */
    public AttendanceViolations violations(Instant observedAt) {
        return violations(observedAt, checkOutAt);
    }

    /**
     * Classifies violations with a correction-derived effective checkout while retaining raw checkout separately.
     *
     * @param observedAt instant at which missing-checkout status is evaluated
     * @param effectiveCheckoutAt approved correction checkout, or raw checkout
     * @return effective violation flags
     */
    public AttendanceViolations violations(Instant observedAt, Instant effectiveCheckoutAt) {
        boolean late = checkInAt.isAfter(scheduledStart().plusSeconds(policy.checkInGraceMinutes() * 60L));
        boolean missingCheckout = effectiveCheckoutAt == null && observedAt.isAfter(checkoutCutoff());
        boolean earlyDeparture = effectiveCheckoutAt != null && effectiveCheckoutAt.isBefore(scheduledEnd());
        return new AttendanceViolations(late, earlyDeparture, missingCheckout);
    }

    private Instant scheduledStart() {
        return ZonedDateTime.of(workDate, policy.scheduledStart(), policy.zoneId()).toInstant();
    }

    private Instant scheduledEnd() {
        return ZonedDateTime.of(workDate, policy.scheduledEnd(), policy.zoneId()).toInstant();
    }

    private Instant checkoutCutoff() {
        return scheduledEnd().plusSeconds(policy.checkoutGraceMinutes() * 60L);
    }
}
