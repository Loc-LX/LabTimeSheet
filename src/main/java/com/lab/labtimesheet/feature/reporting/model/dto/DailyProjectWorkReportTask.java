package com.lab.labtimesheet.feature.reporting.model.dto;

import com.lab.labtimesheet.feature.project.model.TaskStatus;
import com.lab.labtimesheet.feature.project.model.TaskVarianceState;
import com.lab.labtimesheet.feature.project.model.dto.TaskRemainingEffortForecastSummary;
import java.time.Instant;
import java.util.List;

/**
 * One Task row repeated under each retained work-log author group for a selected date.
 *
 * <p>Selected-date minutes describe only the displayed logs. Lifetime actual, estimate, forecast,
 * and variance are separate planning facts from the Task producer boundary.</p>
 *
 * @param taskId Task identifier
 * @param title retained Task title
 * @param currentAssigneeMembershipId current Task assignee, not necessarily the displayed author
 * @param status current persisted Task status
 * @param selectedDateMinutes subtotal of this author's retained logs for the selected date
 * @param lifetimeActualMinutes all retained work-log minutes for the Task
 * @param estimatedMinutes optional original Task estimate in minutes
 * @param varianceState DONE value, unfinished/reopened pending state, or missing estimate state
 * @param varianceMinutes signed DONE actual-minus-estimate value, otherwise null
 * @param assignedAt current assignment instant
 * @param createdAt immutable Task creation instant
 * @param deleted whether the Task is soft-deleted while its log is retained
 * @param logs selected-date retained logs, including repeated descriptions
 * @param latestForecast latest applicable current-assignment planning snapshot, or null
 */
public record DailyProjectWorkReportTask(
        long taskId,
        String title,
        long currentAssigneeMembershipId,
        TaskStatus status,
        long selectedDateMinutes,
        long lifetimeActualMinutes,
        Integer estimatedMinutes,
        TaskVarianceState varianceState,
        Long varianceMinutes,
        Instant assignedAt,
        Instant createdAt,
        boolean deleted,
        List<DailyProjectWorkReportLog> logs,
        TaskRemainingEffortForecastSummary latestForecast) {

    /** Defensive copy prevents view rendering from mutating the report dataset. */
    public DailyProjectWorkReportTask {
        logs = List.copyOf(logs);
    }

    /** @return selected-date minutes as a display-safe value */
    public String selectedDateMinutesDisplay() {
        return Long.toString(selectedDateMinutes);
    }

    /** @return optional original estimate, or {@code N/A} when absent */
    public String estimateDisplay() {
        return estimatedMinutes == null ? "N/A" : Integer.toString(estimatedMinutes);
    }

    /** @return latest applicable remaining effort, or {@code N/A} when no snapshot exists */
    public String forecastRemainingDisplay() {
        return latestForecast == null ? "N/A" : Integer.toString(latestForecast.remainingMinutes());
    }

    /** @return latest applicable forecast total, or {@code N/A} when no snapshot exists */
    public String forecastTotalDisplay() {
        return latestForecast == null ? "N/A" : Long.toString(latestForecast.forecastTotalMinutes());
    }

    /**
     * Returns neutral variance copy: missing estimate is N/A, unfinished/reopened is Pending,
     * and DONE uses a signed actual-minus-estimate value.
     *
     * @return presentation-safe variance text
     */
    public String varianceDisplay() {
        return switch (varianceState) {
            case NOT_ESTIMATED -> "N/A";
            case PENDING -> "Pending";
            case VALUE -> String.format(java.util.Locale.ROOT, "%+d", varianceMinutes);
        };
    }
}
