package com.lab.labtimesheet.feature.task.model.dto;

import java.time.Instant;

/**
 * Read-only detail projection for one immutable initial or correction Remaining effort forecast.
 *
 * @param id persisted forecast identifier; zero is retained for compatibility-only test fixtures
 * @param projectId owning Project identifier
 * @param taskId Task identifier
 * @param incomingMembershipId membership that received the worked Task
 * @param incomingMemberName current or historical display name when available
 * @param forecastingLeaderMembershipId Leader membership that authored the forecast
 * @param forecastingLeaderName current or historical display name when available
 * @param assignmentStartedAt exact assignment instant shared with the Task reassignment
 * @param remainingMinutes predicted remaining effort in minutes
 * @param actualMinutesSnapshot retained lifetime actual effort at assignment time in minutes
 * @param initialNote optional normalized initial forecast note
 * @param createdAt forecast creation instant
 * @param correctionReason normalized mandatory reason for a correction, otherwise {@code null}
 * @param supersedesForecastId predecessor identifier for a correction, otherwise {@code null}
 * @param superseded whether this row has a retained successor
 */
public record TaskRemainingEffortForecastView(
        long id,
        long projectId,
        long taskId,
        long incomingMembershipId,
        String incomingMemberName,
        long forecastingLeaderMembershipId,
        String forecastingLeaderName,
        Instant assignmentStartedAt,
        int remainingMinutes,
        long actualMinutesSnapshot,
        String initialNote,
        Instant createdAt,
        String correctionReason,
        Long supersedesForecastId,
        boolean superseded) {

    /**
     * Compatibility constructor for the original initial-forecast projection.
     *
     * <p>It is intentionally retained so existing read fixtures do not fabricate correction
     * metadata. Persisted service projections always use the full constructor.</p>
     */
    public TaskRemainingEffortForecastView(
            long projectId,
            long taskId,
            long incomingMembershipId,
            String incomingMemberName,
            long forecastingLeaderMembershipId,
            String forecastingLeaderName,
            Instant assignmentStartedAt,
            int remainingMinutes,
            long actualMinutesSnapshot,
            String initialNote,
            Instant createdAt) {
        this(0L, projectId, taskId, incomingMembershipId, incomingMemberName,
                forecastingLeaderMembershipId, forecastingLeaderName, assignmentStartedAt,
                remainingMinutes, actualMinutesSnapshot, initialNote, createdAt,
                null, null, false);
    }

    /** Returns the derived lifetime actual plus remaining forecast total. */
    public long forecastTotalMinutes() {
        return actualMinutesSnapshot + remainingMinutes;
    }

    /** Returns whether this row is a correction successor rather than an initial forecast. */
    public boolean correction() {
        return supersedesForecastId != null;
    }
}
