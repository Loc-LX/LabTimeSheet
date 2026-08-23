package com.lab.labtimesheet.feature.reporting.model.dto;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;

/**
 * Hand-checkable attendance/compliance totals shared by HTML, Excel, and PDF output.
 *
 * <p>An empty rate or compliance value renders as {@code N/A} rather than a misleading zero
 * (ATT-014, ATT-017, RPT-009).
 *
 * @param expectedWorkdays present plus absent expected workdays
 * @param leaveDays expected workdays covered by approved leave
 * @param presentDays expected workdays with an attendance record
 * @param absentDays expected workdays with no attendance record
 * @param attendanceRate present divided by (present plus absent), or empty when the denominator is zero
 * @param periodCompliance average daily score over expected workdays, or empty when there are none
 */
public record AttendanceReportSummary(
        long expectedWorkdays,
        long leaveDays,
        long presentDays,
        long absentDays,
        Optional<BigDecimal> attendanceRate,
        Optional<BigDecimal> periodCompliance) {

    /**
     * Validates required summary fields.
     */
    public AttendanceReportSummary {
        Objects.requireNonNull(attendanceRate, "attendanceRate");
        Objects.requireNonNull(periodCompliance, "periodCompliance");
    }
}