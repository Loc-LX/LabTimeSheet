package com.lab.labtimesheet.feature.reporting.model.dto;

import java.util.List;

/**
 * Historical retained membership author group for one Project's selected-date logs.
 *
 * @param membershipId retained author membership identifier from each work log
 * @param displayName author display identity resolved from Project membership history
 * @param tasks Tasks with at least one selected-date log by this author
 * @param totalMinutes selected-date subtotal in whole minutes
 */
public record DailyProjectWorkReportMember(
        long membershipId,
        String displayName,
        List<DailyProjectWorkReportTask> tasks,
        long totalMinutes) {

    /** Defensive copy keeps the immutable report tree safe for template iteration. */
    public DailyProjectWorkReportMember {
        tasks = List.copyOf(tasks);
    }
}
