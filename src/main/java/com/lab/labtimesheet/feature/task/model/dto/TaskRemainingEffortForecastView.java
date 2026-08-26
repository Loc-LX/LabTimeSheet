package com.lab.labtimesheet.feature.task.model.dto;

import java.time.Instant;

/**
 * Read-only detail projection for one immutable initial Remaining effort forecast.
 *
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
 */
public record TaskRemainingEffortForecastView(
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
        Instant createdAt) {}
