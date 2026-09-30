package com.lab.labtimesheet.feature.attendance.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Laboratory policy {@code D23}: the 48-hour submission and decision windows shared by {@code COR-003}/{@code COR-004}
 * and {@code EXC-002}/{@code EXC-003}.
 */
final class AttendanceRequestWindows {

    static final Duration REQUEST_WINDOW = Duration.ofHours(48);

    private AttendanceRequestWindows() {}

    static Instant submissionDeadline(Instant scheduledEnd) {
        return Objects.requireNonNull(scheduledEnd, "scheduledEnd is required").plus(REQUEST_WINDOW);
    }

    static Instant decisionDeadline(Instant submittedAt) {
        return Objects.requireNonNull(submittedAt, "submittedAt is required").plus(REQUEST_WINDOW);
    }
}
