package com.lab.labtimesheet.feature.attendance.model;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable effective-dated attendance rules interpreted in their configured business timezone.
 * Grace boundaries are inclusive and the checkout cutoff must remain before the next local midnight.
 *
 * @param id persistent policy version identifier attached permanently to attendance rows
 * @param effectiveFrom first local business date governed by this version
 * @param zoneId timezone used to derive work dates and schedule instants
 * @param scheduledStart expected local start time
 * @param scheduledEnd expected local end time
 * @param checkInGraceMinutes allowed minutes after scheduled start, from 0 through 720
 * @param checkoutGraceMinutes allowed minutes after scheduled end, from 0 through 720
 * @param monthlyLeaveQuota quota snapshot source for newly submitted leave allocations
 * @param violationPenalty penalty applied per applicable attendance violation
 * @param workdays configured ISO weekdays that normally require attendance
 */
public record AttendancePolicy(
        long id,
        LocalDate effectiveFrom,
        ZoneId zoneId,
        LocalTime scheduledStart,
        LocalTime scheduledEnd,
        int checkInGraceMinutes,
        int checkoutGraceMinutes,
        int monthlyLeaveQuota,
        BigDecimal violationPenalty,
        Set<DayOfWeek> workdays) {

    private static final int MAX_GRACE_MINUTES = 720;
    private static final int SECONDS_PER_DAY = 86_400;

    /**
     * Validates schedule and grace invariants and defensively snapshots the configured workdays.
     */
    public AttendancePolicy {
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        Objects.requireNonNull(zoneId, "zoneId");
        Objects.requireNonNull(scheduledStart, "scheduledStart");
        Objects.requireNonNull(scheduledEnd, "scheduledEnd");
        Objects.requireNonNull(violationPenalty, "violationPenalty");
        workdays = Set.copyOf(workdays);

        requireGraceInRange(checkInGraceMinutes, "checkInGraceMinutes");
        requireGraceInRange(checkoutGraceMinutes, "checkoutGraceMinutes");
        if (!scheduledEnd.isAfter(scheduledStart)) {
            throw new IllegalArgumentException("scheduledEnd must be after scheduledStart");
        }
        if (scheduledEnd.toSecondOfDay() + checkoutGraceMinutes * 60 >= SECONDS_PER_DAY) {
            throw new IllegalArgumentException("checkout cutoff must be before local midnight");
        }
    }

    /**
     * Reports whether the local date is a configured workday under this version.
     *
     * @param date local date interpreted by this policy
     * @return {@code true} when the weekday is configured for attendance
     */
    public boolean isWorkday(LocalDate date) {
        return workdays.contains(date.getDayOfWeek());
    }

    private static void requireGraceInRange(int value, String field) {
        if (value < 0 || value > MAX_GRACE_MINUTES) {
            throw new IllegalArgumentException(field + " must be between 0 and 720");
        }
    }
}
