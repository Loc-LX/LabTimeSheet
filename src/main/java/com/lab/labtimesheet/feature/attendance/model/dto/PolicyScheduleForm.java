package com.lab.labtimesheet.feature.attendance.model.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.DateTimeException;
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
 * Field constraints mirror the domain policy boundaries so unsafe input re-renders with
 * retained values instead of reaching the service; the timezone must resolve and the
 * schedule must be ordered. Workdays are collected from seven independent checkboxes and
 * converted defensively in {@link #toCommand()}.
 */
@Getter
@Setter
public class PolicyScheduleForm {

    @NotNull(message = "Effective from is required")
    private LocalDate effectiveFrom;

    @NotBlank(message = "Timezone is required")
    private String zoneId = "Asia/Ho_Chi_Minh";

    @NotNull(message = "Scheduled start is required")
    private LocalTime scheduledStart;

    @NotNull(message = "Scheduled end is required")
    private LocalTime scheduledEnd;

    @Min(value = 0, message = "Check-in grace must be between 0 and 720")
    @Max(value = 720, message = "Check-in grace must be between 0 and 720")
    private int checkInGraceMinutes;

    @Min(value = 0, message = "Checkout grace must be between 0 and 720")
    @Max(value = 720, message = "Checkout grace must be between 0 and 720")
    private int checkoutGraceMinutes;

    @Min(value = 0, message = "Monthly leave quota must be between 0 and 31")
    @Max(value = 31, message = "Monthly leave quota must be between 0 and 31")
    private int monthlyLeaveQuota;

    @NotNull(message = "Violation penalty is required")
    @DecimalMin(value = "0.0", message = "Violation penalty must be between 0 and 1")
    @DecimalMax(value = "1.0", message = "Violation penalty must be between 0 and 1")
    private BigDecimal violationPenalty;

    @NotEmpty(message = "At least one workday is required")
    private Set<DayOfWeek> workdays = new LinkedHashSet<>();

    /**
     * Reports whether the submitted timezone name resolves to a real IANA zone.
     * A blank value is left to the {@code @NotBlank} constraint so only one message renders.
     *
     * @return {@code true} when the zone is blank or resolvable
     */
    @AssertTrue(message = "Timezone must be a valid zone")
    public boolean isZoneValid() {
        if (zoneId == null || zoneId.isBlank()) {
            return true;
        }
        try {
            ZoneId.of(zoneId);
            return true;
        } catch (DateTimeException exception) {
            return false;
        }
    }

    /**
     * Reports whether the schedule end falls after the start. Missing times are left to the
     * {@code @NotNull} constraints so only one message renders.
     *
     * @return {@code true} when either time is missing or the order is correct
     */
    @AssertTrue(message = "Scheduled end must be after scheduled start")
    public boolean isScheduleOrdered() {
        return scheduledStart == null || scheduledEnd == null || scheduledEnd.isAfter(scheduledStart);
    }

    /**
     * Converts the bound form into the immutable Admin scheduling command, surfacing an
     * unresolvable timezone as a plain validation failure instead of a server error.
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