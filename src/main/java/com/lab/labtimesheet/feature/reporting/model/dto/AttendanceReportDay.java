package com.lab.labtimesheet.feature.reporting.model.dto;

import com.lab.labtimesheet.feature.calendar.model.AttendancePolicy;
import com.lab.labtimesheet.feature.attendance.model.AttendanceViolations;

import java.time.LocalDate;
import java.util.Objects;

/**
 * One classified calendar date feeding the shared attendance/compliance report dataset.
 *
 * <p>The attached historical policy preserves each day's penalty and schedule meaning even after a
 * later policy or calendar change (ATT-004, ATT-006). Violations are empty for non-present days.
 *
 * @param date immutable local business date
 * @param kind report classification of the date
 * @param policy policy version effective on the date
 * @param violations applicable attendance violations, or empty flags when the day has no record
 */
public record AttendanceReportDay(
        LocalDate date,
        DayKind kind,
        AttendancePolicy policy,
        AttendanceViolations violations) {

    /**
     * Validates required report fields.
     */
    public AttendanceReportDay {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(policy, "policy");
        Objects.requireNonNull(violations, "violations");
    }
}