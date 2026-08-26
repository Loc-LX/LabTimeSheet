package com.lab.labtimesheet.feature.reporting.model.dto;

import java.util.List;

/**
 * Project group containing retained selected-date work-log authors and subtotals.
 *
 * @param projectId Project identifier
 * @param projectName Project display name
 * @param members historical author groups with selected-date work
 * @param totalMinutes selected-date Project subtotal in whole minutes
 */
public record DailyProjectWorkReportProject(
        long projectId,
        String projectName,
        List<DailyProjectWorkReportMember> members,
        long totalMinutes) {

    /** Defensive copy keeps the immutable report tree safe for template iteration. */
    public DailyProjectWorkReportProject {
        members = List.copyOf(members);
    }
}
