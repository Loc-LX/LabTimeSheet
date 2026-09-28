package com.lab.labtimesheet.feature.attendance.model.dto;

import com.lab.labtimesheet.feature.attendance.model.AttendanceExceptionDecisionKind;
import com.lab.labtimesheet.feature.attendance.model.AttendanceExceptionOutcome;
import java.time.Instant;

/** Immutable projection of one attendance exception decision-history entry. */
public record AttendanceExceptionDecisionView(long id, AttendanceExceptionDecisionKind decisionKind,
        AttendanceExceptionOutcome outcome, String decisionNote, long actorUserId, Instant occurredAt, String reason) { }
