package com.lab.labtimesheet.feature.attendance.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduled deadline worker for leave first-counted-start expiry and correction decision-window expiry
 * (I2-ATT-07). Each run delegates to the bounded, idempotent {@link AttendanceDeadlineService} batches, so a late
 * or repeated invocation can never duplicate a state transition or notification. The {@code @Scheduled} trigger is
 * inert unless {@code @EnableScheduling} is active (see {@code SchedulingConfiguration}, disabled for the test
 * profile); tests invoke the public worker methods directly against committed rows.
 */
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class AttendanceScheduler {

    private static final int BATCH_SIZE = 100;

    private final AttendanceDeadlineService deadlines;

    /**
     * Runs every minute on the configured schedule, closing every expired pending leave and unlocked correction.
     */
    @Scheduled(cron = "0 */1 * * * *")
    public void runDeadlineWorkers() {
        expireLeaveDeadlines();
        expireCorrectionDeadlines();
    }

    /**
     * Runs the bounded pending-leave expiry batch once.
     */
    public void expireLeaveDeadlines() {
        deadlines.expirePendingLeaves(BATCH_SIZE);
    }

    /**
     * Runs the bounded correction decision-window expiry batch once.
     */
    public void expireCorrectionDeadlines() {
        deadlines.expireCorrections(BATCH_SIZE);
    }
}