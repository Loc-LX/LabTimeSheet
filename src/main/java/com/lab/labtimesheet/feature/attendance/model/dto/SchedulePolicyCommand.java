package com.lab.labtimesheet.feature.attendance.model.dto;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;

/**
 * Immutable Admin command scheduling or replacing one future attendance-policy version.
 *
 * @param effectiveFrom first day of the future calendar month the version governs
 * @param zoneId business timezone used to derive work dates and schedule instants
 * @param scheduledStart expected local start time
 * @param scheduledEnd expected local end time
 * @param checkInGraceMinutes allowed minutes after scheduled start, from 0 through 720
 * @param checkoutGraceMinutes allowed minutes after scheduled end, from 0 through 720
 * @param monthlyLeaveQuota quota snapshot source for newly submitted leave allocations
 * @param violationPenalty penalty applied per applicable attendance violation
 * @param workdays configured ISO weekdays that normally require attendance
 */
public record SchedulePolicyCommand(
        LocalDate effectiveFrom,
        ZoneId zoneId,
        LocalTime scheduledStart,
        LocalTime scheduledEnd,
        int checkInGraceMinutes,
        int checkoutGraceMinutes,
        int monthlyLeaveQuota,
        BigDecimal violationPenalty,
        Set<DayOfWeek> workdays) {}