package com.lab.labtimesheet.feature.attendance.service;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Runs bounded, repeatable deadline sweeps. Request-time guards remain authoritative when the worker is delayed.
 */
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class AttendanceDeadlineScheduler {

    private static final int BATCH_SIZE = 100;

    private final LeaveApplicationService leave;
    private final AttendanceCorrectionApplicationService corrections;

    /** Processes at most one bounded batch of each deadline type per invocation. */
    @Scheduled(fixedDelayString = "${lab.attendance.deadline-sweep-ms:60000}")
    public void sweep() {
        leave.expirePending(BATCH_SIZE);
        corrections.expire(BATCH_SIZE);
    }
}
