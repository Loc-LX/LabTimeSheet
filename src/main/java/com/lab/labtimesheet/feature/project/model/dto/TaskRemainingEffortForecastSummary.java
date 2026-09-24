package com.lab.labtimesheet.feature.project.model.dto;

import java.time.Instant;

/**
 * Latest applicable Remaining effort planning snapshot for a Task report.
 *
 * <p>Forecast notes and correction reasons are intentionally not part of this compact producer
 * projection. They remain available through the full Task-detail history boundary, while report
 * consumers receive only hand-checkable planning facts.</p>
 *
 * @param remainingMinutes predicted effort still required at the snapshot
 * @param actualMinutesSnapshot lifetime retained effort at the snapshot
 * @param assignmentStartedAt exact assignment context represented by the snapshot
 */
public record TaskRemainingEffortForecastSummary(
        int remainingMinutes,
        long actualMinutesSnapshot,
        Instant assignmentStartedAt) {

    /**
     * Returns the derived forecast total in minutes; no duplicate total is persisted.
     *
     * @return actual-at-snapshot plus remaining effort
     */
    public long forecastTotalMinutes() {
        return actualMinutesSnapshot + remainingMinutes;
    }
}
