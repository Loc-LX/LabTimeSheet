package com.lab.labtimesheet.feature.attendance.model.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Immutable bounded Attendance report result for one Intern and inclusive local-date range.
 *
 * <p>Expected workdays exclude imported/custom off-days and approved leave. Attendance rate is present divided by
 * expected workdays, and compliance is the average daily score over those expected days, including absent days as
 * zero. Both aggregate percentages use two-decimal {@link RoundingMode#HALF_UP} rounding. An empty optional and its
 * {@code N/A} display are the explicit result when the expected denominator is zero.</p>
 *
 * @param internId target Intern account identifier
 * @param from inclusive first local date
 * @param to inclusive last local date
 * @param days daily rows in ascending local-date order, including eligible off-days and leave
 * @param presentWorkdays number of PRESENT expected days
 * @param expectedWorkdays expected denominator after off-day and leave exclusion
 * @param attendanceRatePercent attendance percentage, or empty when expected is zero
 * @param compliancePercent average compliance percentage, or empty when expected is zero
 */
public record AttendanceReport(
        long internId,
        LocalDate from,
        LocalDate to,
        List<AttendanceReportDay> days,
        int presentWorkdays,
        int expectedWorkdays,
        Optional<BigDecimal> attendanceRatePercent,
        Optional<BigDecimal> compliancePercent) {

    /** Validates range, counts, and optional percentage values while defensively copying daily rows. */
    public AttendanceReport {
        if (internId <= 0) {
            throw new IllegalArgumentException("Intern user ID must be positive");
        }
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from must not be after to");
        }
        days = List.copyOf(Objects.requireNonNull(days, "days"));
        if (presentWorkdays < 0 || expectedWorkdays < 0 || presentWorkdays > expectedWorkdays) {
            throw new IllegalArgumentException("Report counts are inconsistent");
        }
        attendanceRatePercent = Objects.requireNonNull(attendanceRatePercent, "attendanceRatePercent");
        compliancePercent = Objects.requireNonNull(compliancePercent, "compliancePercent");
    }

    /**
     * Returns the exact two-decimal attendance display or {@code N/A} when no expected workday exists.
     *
     * @return percentage display with a percent sign, or N/A
     */
    public String attendanceRateDisplay() {
        return display(attendanceRatePercent);
    }

    /**
     * Returns the exact two-decimal compliance display or {@code N/A} when no expected workday exists.
     *
     * @return percentage display with a percent sign, or N/A
     */
    public String complianceDisplay() {
        return display(compliancePercent);
    }

    private static String display(Optional<BigDecimal> value) {
        return value.map(percent -> percent.setScale(2, RoundingMode.HALF_UP).toPlainString() + "%")
                .orElse("N/A");
    }
}
