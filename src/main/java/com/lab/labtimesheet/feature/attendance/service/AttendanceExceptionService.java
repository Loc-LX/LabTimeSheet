package com.lab.labtimesheet.feature.attendance.service;

import com.lab.labtimesheet.feature.attendance.model.AttendanceExceptionDecisionKind;
import com.lab.labtimesheet.feature.attendance.model.AttendanceExceptionKind;
import com.lab.labtimesheet.feature.attendance.model.AttendanceExceptionOutcome;
import com.lab.labtimesheet.feature.attendance.model.AttendanceExceptionSource;
import com.lab.labtimesheet.feature.attendance.model.dto.AttendanceExceptionView;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceExceptionDecisionEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceExceptionEntity;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceExceptionDecisionRepository;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceExceptionRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Package-scoped persistence operations used by Attendance workflows for exceptions and their decision history. */
@Service
public class AttendanceExceptionService {

    private final Clock clock;
    private final AttendanceExceptionRepository exceptions;
    private final AttendanceExceptionDecisionRepository decisions;

    /**
     * Creates the persistence boundary with the application clock and Attendance repositories.
     *
     * @param clock authoritative application clock
     * @param exceptions current-state exception repository
     * @param decisions append-only decision history repository
     */
    public AttendanceExceptionService(Clock clock, AttendanceExceptionRepository exceptions,
            AttendanceExceptionDecisionRepository decisions) {
        this.clock = clock;
        this.exceptions = exceptions;
        this.decisions = decisions;
    }

    /**
     * Opens a pending exception at the injected server clock instant.
     *
     * @param attendanceRecordId retained attendance row
     * @param kind recorded violation kind
     * @param source request or direct mark
     * @param reason nonblank explanation
     * @param submissionDeadline persisted submission boundary
     * @param decisionDeadline persisted decision boundary
     * @return new exception identifier
     */
    @Transactional
    long open(long attendanceRecordId, AttendanceExceptionKind kind, AttendanceExceptionSource source,
            String reason, Instant submissionDeadline, Instant decisionDeadline) {
        Instant submittedAt = clock.instant();
        AttendanceExceptionEntity row = new AttendanceExceptionEntity(attendanceRecordId, kind, source, reason,
                submittedAt, submissionDeadline, decisionDeadline);
        return exceptions.saveAndFlush(row).id();
    }

    /**
     * Appends one immutable decision and synchronizes the current exception row atomically.
     *
     * @param exceptionId exception to decide
     * @param kind decision, amendment, or reversal
     * @param outcome effective outcome represented by this entry
     * @param decisionNote optional current decision note
     * @param actorUserId actor already authorized by the calling Attendance workflow
     * @param reason required database explanation for amendments and reversals
     */
    @Transactional
    void appendDecision(long exceptionId, AttendanceExceptionDecisionKind kind, AttendanceExceptionOutcome outcome,
            String decisionNote, long actorUserId, String reason) {
        AttendanceExceptionEntity row = exceptions.findForUpdateById(exceptionId).orElseThrow();
        Instant occurredAt = clock.instant();
        decisions.saveAndFlush(new AttendanceExceptionDecisionEntity(
                exceptionId, kind, outcome, decisionNote, actorUserId, occurredAt, reason));
        row.applyDecision(outcome, actorUserId, occurredAt, decisionNote);
        exceptions.flush();
    }

    /**
     * Reads current exception state with immutable history ordered by occurrence time.
     *
     * @param exceptionId exception identifier
     * @return current state when it exists
     */
    @Transactional(readOnly = true)
    public Optional<AttendanceExceptionView> find(long exceptionId) {
        return exceptions.findById(exceptionId).map(row -> row.toView(decisions
                .findByAttendanceExceptionIdOrderByOccurredAtAscIdAsc(exceptionId).stream()
                .map(AttendanceExceptionDecisionEntity::toView)
                .toList()));
    }
}
