package com.lab.labtimesheet.feature.attendance.model.entity;

import java.time.Instant;

public final class CorrectionEntityFixtures {

    private CorrectionEntityFixtures() {}

    public static AttendanceCorrectionEntity approved(
            AttendanceRecordEntity record,
            Instant requestedCheckoutAt,
            Instant submittedAt,
            Instant submissionDeadline,
            Instant decisionDeadline,
            long decidedByMentorUserId,
            Instant decidedAt) {
        AttendanceCorrectionEntity correction = new AttendanceCorrectionEntity(
                record,
                requestedCheckoutAt,
                "Attendance integration fixture",
                submittedAt,
                submissionDeadline,
                decisionDeadline);
        correction.approve(decidedByMentorUserId, decidedAt, null);
        return correction;
    }
}