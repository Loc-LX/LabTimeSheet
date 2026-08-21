package com.lab.labtimesheet.feature.attendance.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;

class AttendanceDeadlineSchedulerTest {

    @Test
    void sweepsBothDeadlineBoundariesWithTheSameBoundedBatch() {
        LeaveApplicationService leave = mock(LeaveApplicationService.class);
        AttendanceCorrectionApplicationService corrections = mock(AttendanceCorrectionApplicationService.class);

        new AttendanceDeadlineScheduler(leave, corrections).sweep();

        verify(leave).expirePending(100);
        verify(corrections).expire(100);
    }
}
