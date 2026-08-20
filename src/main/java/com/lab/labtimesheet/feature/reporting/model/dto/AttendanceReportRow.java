package com.lab.labtimesheet.feature.reporting.model.dto;

/**
 * Render-ready attendance/compliance row for the HTML report.
 *
 * @param workDate policy-local work date
 * @param checkIn local check-in display
 * @param checkOut local checkout display, or {@code Missing}
 * @param schedule attached policy schedule display
 * @param result stable violation summary
 * @param workedMinutes elapsed attendance minutes, or {@code N/A} without checkout
 * @param compliant true when no attendance violation applies
 */
public record AttendanceReportRow(
        String workDate,
        String checkIn,
        String checkOut,
        String schedule,
        String result,
        String workedMinutes,
        boolean compliant) {
}
