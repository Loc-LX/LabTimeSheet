package com.lab.labtimesheet.feature.attendance.model.dto;

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

    /** Validates required values before the application service reaches persistence. */
    public AttendancePolicyCommand {
        Objects.requireNonNull(effectiveFrom, "effectiveFrom");
        Objects.requireNonNull(zoneId, "zoneId");
        Objects.requireNonNull(scheduledStart, "scheduledStart");
        Objects.requireNonNull(scheduledEnd, "scheduledEnd");
        Objects.requireNonNull(violationPenalty, "violationPenalty");
        Objects.requireNonNull(workdays, "workdays");
        if (monthlyLeaveQuota < 0 || monthlyLeaveQuota > 31) {
            throw new IllegalArgumentException("monthlyLeaveQuota must be between 0 and 31");
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
