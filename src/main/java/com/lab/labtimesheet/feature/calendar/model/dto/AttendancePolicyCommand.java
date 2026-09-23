package com.lab.labtimesheet.feature.calendar.model.dto;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Objects;
import java.util.Set;

/**
 * Admin input for one immutable-on-effective attendance-policy version.
 *
 * @param effectiveFrom first local date governed by the version
 * @param zoneId business timezone used for schedule interpretation
 * @param scheduledStart scheduled local workday start
 * @param scheduledEnd scheduled local workday end
 * @param checkInGraceMinutes inclusive late boundary extension
 * @param checkoutGraceMinutes inclusive checkout boundary extension
 * @param monthlyLeaveQuota maximum quota-consuming leave days per month
 * @param violationPenalty penalty per applicable violation
 * @param workdays ISO weekdays that create attendance obligations
 */
public record AttendancePolicyCommand(
        LocalDate effectiveFrom,
        ZoneId zoneId,
        LocalTime scheduledStart,
        LocalTime scheduledEnd,
        int checkInGraceMinutes,
        int checkoutGraceMinutes,
        int monthlyLeaveQuota,
        BigDecimal violationPenalty,
        Set<DayOfWeek> workdays) {

    /**
     * Largest monthly leave quota an Admin may configure, fixed by {@code ATT-003}.
     *
     * <p>Four is the largest whole number that stays within a fifth of the shortest
     * twenty-workday month. The database column still permits 0 through 31; that wider bound is
     * a type constraint, and this is the business limit.
     */
    public static final int MAX_MONTHLY_LEAVE_QUOTA = 4;

    /** Validates required values before the application service reaches persistence. */
    public AttendancePolicyCommand {
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        Objects.requireNonNull(zoneId, "zoneId");
        Objects.requireNonNull(scheduledStart, "scheduledStart");
        Objects.requireNonNull(scheduledEnd, "scheduledEnd");
        Objects.requireNonNull(violationPenalty, "violationPenalty");
        Objects.requireNonNull(workdays, "workdays");
        if (monthlyLeaveQuota < 0 || monthlyLeaveQuota > MAX_MONTHLY_LEAVE_QUOTA) {
            throw new IllegalArgumentException(
                    "monthlyLeaveQuota must be between 0 and " + MAX_MONTHLY_LEAVE_QUOTA);
        }
        if (violationPenalty.signum() < 0 || violationPenalty.compareTo(BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException("violationPenalty must be between 0 and 1");
        }
        if (workdays.isEmpty()) {
            throw new IllegalArgumentException("At least one workday is required");
        }
        workdays = Set.copyOf(workdays);
    }
}
