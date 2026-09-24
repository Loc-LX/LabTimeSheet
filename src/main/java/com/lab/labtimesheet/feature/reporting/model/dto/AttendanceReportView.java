package com.lab.labtimesheet.feature.reporting.model.dto;

import com.lab.labtimesheet.feature.internship.model.dto.EligibleInternOption;
import java.time.LocalDate;
import java.util.List;

/**
 * Authorized attendance/compliance dataset consumed by the server-rendered report page.
 *
 * <p>The Reporting feature owns only presentation formatting. Attendance authorization, attached
 * historical policy values, classifications, and aggregate formulas remain in Attendance.</p>
 *
 * @param targetInternId authorized target Intern account identifier
 * @param targetName target display name
 * @param from inclusive local-date filter
 * @param to inclusive local-date filter
 * @param ownScope true when the authenticated Intern is viewing only their own rows
 * @param rows render-ready historical attendance rows
 * @param expectedWorkdays expected-day denominator after leave and day-off exclusion
 * @param presentWorkdays expected days with an attendance row
 * @param absentWorkdays expected days without an attendance row
 * @param attendanceRate formatted present/expected percentage, or {@code N/A}
 * @param complianceRate formatted historical-policy score percentage, or {@code N/A}
 * @param targetOptions authorized target choices for Mentor/Admin detail scope
 * @param trend equivalent data points for the optional chart enhancement
 */
public record AttendanceReportView(
        long targetInternId,
        String targetName,
        LocalDate from,
        LocalDate to,
        boolean ownScope,
        List<AttendanceReportRow> rows,
        long expectedWorkdays,
        long presentWorkdays,
        long absentWorkdays,
        String attendanceRate,
        String complianceRate,
        List<EligibleInternOption> targetOptions,
        List<ReportTrendPoint> trend) {

    /** Copies nested collections so a template cannot mutate the authorized dataset. */
    public AttendanceReportView {
        rows = List.copyOf(rows);
        targetOptions = List.copyOf(targetOptions);
        trend = List.copyOf(trend);
    }
}
