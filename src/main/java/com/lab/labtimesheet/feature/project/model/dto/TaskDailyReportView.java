package com.lab.labtimesheet.feature.project.model.dto;

import com.lab.labtimesheet.feature.project.model.TaskStatus;
import com.lab.labtimesheet.feature.project.model.TaskVarianceState;
import java.time.Instant;
import java.util.List;

/**
 * Public Task producer projection for a one-date Project work report.
 *
 * <p>The projection includes retained logs for the selected local report date, including logs
 * belonging to a soft-deleted Task, and keeps current Task assignment/status facts separate from
 * each log's historical author. Lifetime actual effort is supplied by the producer's aggregate
 * query, so report consumers do not need to hydrate complete work history. This boundary does not
 * consult attendance and does not infer presence.</p>
 *
 * @param id Task identifier
 * @param projectId owning Project identifier
 * @param assigneeMembershipId current or final assignee membership identifier
 * @param title retained Task title
 * @param status current or final stored Task status
 * @param estimatedMinutes optional original whole-Task estimate in minutes
 * @param lifetimeActualMinutes sum of all retained work-log minutes across dates and authors
 * @param varianceState DONE value, unfinished/reopened pending state, or missing estimate state
 * @param varianceMinutes signed DONE actual-minus-estimate value, otherwise null
 * @param assignedAt current assignment instant
 * @param createdAt immutable creation instant
 * @param deletedAt soft-deletion instant, or null for a retained current Task
 * @param workLogs retained work logs for the selected report date, ordered by Task/log identifier
 * @param latestForecast latest applicable immutable forecast snapshot for the current assignment
 */
public record TaskDailyReportView(
        long id,
        long projectId,
        long assigneeMembershipId,
        String title,
        TaskStatus status,
        Integer estimatedMinutes,
        long lifetimeActualMinutes,
        TaskVarianceState varianceState,
        Long varianceMinutes,
        Instant assignedAt,
        Instant createdAt,
        Instant deletedAt,
        List<TaskWorkLogView> workLogs,
        TaskRemainingEffortForecastSummary latestForecast) {

    /** Defensive copy keeps retained producer rows immutable to report consumers. */
    public TaskDailyReportView {
        workLogs = List.copyOf(workLogs);
    }

    /**
     * Creates the public projection from selected-date logs and a producer-computed lifetime
     * aggregate while preserving the producer's planning semantics.
     *
     * @param id Task identifier
     * @param projectId owning Project identifier
     * @param assigneeMembershipId current or final assignee membership
     * @param title retained title
     * @param status current or final status
     * @param estimatedMinutes optional estimate
     * @param lifetimeActualMinutes aggregate of all retained work-log minutes for the Task
     * @param assignedAt current assignment instant
     * @param createdAt immutable creation instant
     * @param deletedAt soft-deletion instant
     * @param workLogs selected-date retained work logs
     * @param latestForecast latest applicable forecast snapshot
     * @return immutable report projection
     */
    public static TaskDailyReportView of(
            long id,
            long projectId,
            long assigneeMembershipId,
            String title,
            TaskStatus status,
            Integer estimatedMinutes,
            long lifetimeActualMinutes,
            Instant assignedAt,
            Instant createdAt,
            Instant deletedAt,
            List<TaskWorkLogView> workLogs,
            TaskRemainingEffortForecastSummary latestForecast) {
        TaskVarianceState varianceState = estimatedMinutes == null
                ? TaskVarianceState.NOT_ESTIMATED
                : status == TaskStatus.DONE ? TaskVarianceState.VALUE : TaskVarianceState.PENDING;
        Long variance = varianceState == TaskVarianceState.VALUE
                ? lifetimeActualMinutes - estimatedMinutes
                : null;
        return new TaskDailyReportView(
                id, projectId, assigneeMembershipId, title, status, estimatedMinutes,
                lifetimeActualMinutes,
                varianceState, variance, assignedAt, createdAt, deletedAt, workLogs, latestForecast);
    }

    /** @return true when retained history identifies this Task as soft-deleted */
    public boolean deleted() {
        return deletedAt != null;
    }
}
