package com.lab.labtimesheet.feature.reporting.model.dto;

import com.lab.labtimesheet.feature.account.model.dto.EligibleInternOption;
import java.time.LocalDate;
import java.util.List;

/**
 * Authorized attendance/compliance dataset consumed by the server-rendered report page.
 *
 * <p>The Reporting feature owns only presentation aggregation. Attendance authorization, attached
 * historical policy values, and violation classification remain in the Attendance service.</p>
 *
 * @param targetInternId authorized target Intern account identifier
 * @param targetName target display name
 * @param from inclusive local-date filter
 * @param to inclusive local-date filter
 * @param ownScope true when the authenticated Intern is viewing only their own rows
 * @param rows render-ready historical attendance rows
 * @param recordedDays number of returned rows
 * @param compliantDays rows with no violation
 * @param violationDays rows with one or more violations
 * @param complianceRate formatted percentage, or {@code N/A} when no rows exist
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
        long recordedDays,
        long compliantDays,
        long violationDays,
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
