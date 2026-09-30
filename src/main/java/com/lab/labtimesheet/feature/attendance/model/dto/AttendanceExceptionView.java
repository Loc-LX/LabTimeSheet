package com.lab.labtimesheet.feature.attendance.model.dto;

import com.lab.labtimesheet.feature.attendance.model.AttendanceExceptionKind;
import com.lab.labtimesheet.feature.attendance.model.AttendanceExceptionSource;
import com.lab.labtimesheet.feature.attendance.model.AttendanceExceptionStatus;
import java.time.Instant;
import java.util.List;

/** Current attendance exception state and its retained decision history in occurrence order. */
public record AttendanceExceptionView(long id, long attendanceRecordId, AttendanceExceptionKind kind,
        AttendanceExceptionSource source, String reason, Instant submittedAt, Instant submissionDeadline,
        Instant decisionDeadline, AttendanceExceptionStatus status, Long decidedByMentorUserId, Instant decidedAt,
        String decisionNote, List<AttendanceExceptionDecisionView> decisions) {
    /** Copies history to keep the read model immutable. */
    public AttendanceExceptionView {
        decisions = List.copyOf(decisions);
    }
}
