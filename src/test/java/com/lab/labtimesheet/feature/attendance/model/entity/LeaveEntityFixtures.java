package com.lab.labtimesheet.feature.attendance.model.entity;

import java.time.Instant;
import java.time.LocalDate;

public final class LeaveEntityFixtures {

    private LeaveEntityFixtures() {}

    public static LeaveRequestEntity approvedRequest(
            long internUserId,
            LocalDate startDate,
            LocalDate endDate,
            Instant submittedAt,
            Instant firstCountedStartAt,
            long decidedByMentorUserId,
            Instant decidedAt) {
        return new LeaveRequestEntity(
                internUserId,
                startDate,
                endDate,
                "Attendance integration fixture",
                submittedAt,
                firstCountedStartAt,
                decidedByMentorUserId,
                decidedAt);
    }

    public static LeaveRequestDayEntity allocatedDay(
            LeaveRequestEntity request,
            LocalDate leaveDate,
            long policyVersionId,
            int monthlyQuotaSnapshot) {
        return new LeaveRequestDayEntity(request, leaveDate, policyVersionId, monthlyQuotaSnapshot);
    }
}
