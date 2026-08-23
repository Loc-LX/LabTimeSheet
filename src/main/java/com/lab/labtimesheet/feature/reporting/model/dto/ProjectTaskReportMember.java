package com.lab.labtimesheet.feature.reporting.model.dto;

import java.util.Objects;

/**
 * One Project member's logged-work totals for the per-member report section and chart.
 *
 * @param memberId account identifier of the member
 * @param displayName user-facing name of the member
 * @param loggedMinutes sum of logged work minutes within the filtered work-date range
 * @param taskCount number of non-deleted Tasks assigned to the member within the range
 */
public record ProjectTaskReportMember(long memberId, String displayName, long loggedMinutes, long taskCount) {

    /**
     * Validates required member fields.
     */
    public ProjectTaskReportMember {
        Objects.requireNonNull(displayName, "displayName");
    }

    /**
     * Returns logged work in hours for chart and table presentation.
     *
     * @return logged minutes converted to hours
     */
    public double loggedHours() {
        return loggedMinutes / 60.0;
    }
}