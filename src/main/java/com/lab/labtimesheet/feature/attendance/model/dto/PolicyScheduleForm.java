package com.lab.labtimesheet.feature.attendance.model.dto;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;

/**
 * Bindable Admin form scheduling or replacing one future attendance-policy version.
 * Workdays are collected from seven independent checkboxes and converted defensively
 * in {@link #toCommand()}; an absent timezone falls back to the Vietnam business zone.
 */
@Getter
@Setter
public class PolicyScheduleForm {

    private LocalDate effectiveFrom;
    private String zoneId = "Asia/Ho_Chi_Minh";
    private LocalTime scheduledStart;
    private LocalTime scheduledEnd;
    private int checkInGraceMinutes;
    private int checkoutGraceMinutes;
    private int monthlyLeaveQuota;
    private BigDecimal violationPenalty;
    private Set<DayOfWeek> workdays = new LinkedHashSet<>();

    /**
     * Converts the bound form into the immutable Admin scheduling command.
     *
     * @return scheduling command interpreted in the resolved business timezone
     */
    public SchedulePolicyCommand toCommand() {
        return new SchedulePolicyCommand(
                effectiveFrom,
                ZoneId.of(zoneId),
                scheduledStart,
                scheduledEnd,
                checkInGraceMinutes,
                checkoutGraceMinutes,
                monthlyLeaveQuota,
                violationPenalty,
                workdays);
    }
}