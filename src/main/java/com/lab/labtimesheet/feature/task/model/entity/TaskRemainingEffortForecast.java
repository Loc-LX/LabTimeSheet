package com.lab.labtimesheet.feature.task.model.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Column;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Immutable append-only initial or correction Remaining effort forecast.
 *
 * <p>An initial row describes the incoming assignment. A correction is a new row linked to its
 * predecessor; neither row is ever updated or deleted. The forecast total is deliberately derived
 * from the immutable actual snapshot and remaining minutes rather than persisted separately.</p>
 */
@Entity
@Table(name = "task_remaining_effort_forecasts")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaskRemainingEffortForecast {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "project_id", nullable = false) private long projectId;
    @Column(name = "task_id", nullable = false) private long taskId;
    @Column(name = "incoming_membership_id", nullable = false) private long incomingMembershipId;
    @Column(name = "forecasting_leader_membership_id", nullable = false) private long forecastingLeaderMembershipId;
    @Column(name = "assignment_started_at", nullable = false) private Instant assignmentStartedAt;
    @Column(name = "remaining_minutes", nullable = false) private int remainingMinutes;
    @Column(name = "actual_minutes_snapshot", nullable = false) private long actualMinutesSnapshot;
    @Column(name = "initial_note") private String initialNote;
    @Column(name = "created_at", nullable = false) private Instant createdAt;
    @Column(name = "correction_reason") private String correctionReason;
    @Column(name = "supersedes_forecast_id") private Long supersedesForecastId;

    public TaskRemainingEffortForecast(long projectId, long taskId, long incomingMembershipId,
            long forecastingLeaderMembershipId, Instant assignmentStartedAt, int remainingMinutes,
            long actualMinutesSnapshot, String initialNote, Instant createdAt) {
        this.projectId = projectId;
        this.taskId = taskId;
        this.incomingMembershipId = incomingMembershipId;
        this.forecastingLeaderMembershipId = forecastingLeaderMembershipId;
        this.assignmentStartedAt = assignmentStartedAt;
        this.remainingMinutes = remainingMinutes;
        this.actualMinutesSnapshot = actualMinutesSnapshot;
        this.initialNote = initialNote;
        this.createdAt = createdAt;
    }

    /**
     * Creates an immutable correction successor preserving assignment facts from its predecessor.
     *
     * @param predecessor predecessor row that remains unchanged
     * @param forecastingLeaderMembershipId current Leader membership authoring the correction
     * @param remainingMinutes replacement remaining effort
     * @param actualMinutesSnapshot current lifetime actual snapshot
     * @param correctionReason normalized mandatory reason
     * @param createdAt server-controlled correction instant
     * @return unsaved append-only successor row
     */
    public static TaskRemainingEffortForecast correction(TaskRemainingEffortForecast predecessor,
            long forecastingLeaderMembershipId, int remainingMinutes, long actualMinutesSnapshot,
            String correctionReason, Instant createdAt) {
        TaskRemainingEffortForecast correction = new TaskRemainingEffortForecast(
                predecessor.getProjectId(), predecessor.getTaskId(), predecessor.getIncomingMembershipId(),
                forecastingLeaderMembershipId, predecessor.getAssignmentStartedAt(), remainingMinutes,
                actualMinutesSnapshot, null, createdAt);
        correction.correctionReason = correctionReason;
        correction.supersedesForecastId = predecessor.getId();
        return correction;
    }

    /**
     * Retains the historical factory shape for callers that do not have a refreshed snapshot.
     * Production correction paths use the overload carrying the current lifetime actual value.
     */
    @Deprecated
    public static TaskRemainingEffortForecast correction(TaskRemainingEffortForecast predecessor,
            long forecastingLeaderMembershipId, int remainingMinutes, String correctionReason, Instant createdAt) {
        return correction(predecessor, forecastingLeaderMembershipId, remainingMinutes,
                predecessor.getActualMinutesSnapshot(), correctionReason, createdAt);
    }

    /** Returns the derived forecast total without storing a duplicate value. */
    public long forecastTotalMinutes() {
        return actualMinutesSnapshot + remainingMinutes;
    }
}
