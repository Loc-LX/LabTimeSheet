package com.lab.labtimesheet.feature.attendance.service;

import com.lab.labtimesheet.feature.attendance.model.AttendanceExceptionDecisionKind;
import com.lab.labtimesheet.feature.attendance.model.AttendanceExceptionKind;
import com.lab.labtimesheet.feature.attendance.model.AttendanceExceptionOutcome;
import com.lab.labtimesheet.feature.attendance.model.AttendanceExceptionSource;
import com.lab.labtimesheet.feature.attendance.model.dto.AttendanceExceptionView;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceExceptionDecisionEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceExceptionEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceRecordEntity;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceExceptionDecisionRepository;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceExceptionRepository;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceRecordRepository;
import com.lab.labtimesheet.feature.notification.model.NotificationType;
import com.lab.labtimesheet.feature.notification.model.dto.NotificationAction;
import com.lab.labtimesheet.feature.notification.model.dto.NotificationEvent;
import com.lab.labtimesheet.feature.notification.service.NotificationService;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Package-scoped persistence operations used by Attendance workflows for exceptions and their decision history. */
@Service
public class AttendanceExceptionService {

    private final Clock clock;
    private final AttendanceExceptionRepository exceptions;
    private final AttendanceExceptionDecisionRepository decisions;
    private final AttendanceRecordRepository records;
    private final AttendanceExceptionNotificationRecipients recipients;
    private final NotificationService notifications;

    /**
     * Creates the persistence boundary with the application clock and Attendance repositories.
     *
     * @param clock authoritative application clock
     * @param exceptions current-state exception repository
     * @param decisions append-only decision history repository
     * @param records attendance records used to resolve each request owner
     * @param recipients shared D47 recipient resolver
     * @param notifications in-app notification publisher
     */
    public AttendanceExceptionService(Clock clock, AttendanceExceptionRepository exceptions,
            AttendanceExceptionDecisionRepository decisions, AttendanceRecordRepository records,
            AttendanceExceptionNotificationRecipients recipients, NotificationService notifications) {
        this.clock = clock;
        this.exceptions = exceptions;
        this.decisions = decisions;
        this.records = records;
        this.recipients = recipients;
        this.notifications = notifications;
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
     * Transitions one bounded batch of submitted requests whose decision deadline has elapsed.
     *
     * @param batchSize maximum candidate requests to lock and process
     * @return number of requests newly transitioned to OVERDUE
     * @throws IllegalArgumentException when the batch size is not positive
     */
    @Transactional
    int expireOverdue(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        List<Long> candidates = exceptions.findOverdueIds(clock.instant(), PageRequest.of(0, batchSize));
        int changed = 0;
        for (long exceptionId : candidates) {
            AttendanceExceptionEntity row = exceptions.findForUpdateById(exceptionId).orElse(null);
            if (row != null && transitionIfDue(row)) {
                changed++;
            }
        }
        return changed;
    }

    /**
     * Applies the overdue transition before a read-time decision workflow proceeds.
     *
     * @param exceptionId exception whose decision deadline may have elapsed
     */
    @Transactional
    void expireIfDue(long exceptionId) {
        exceptions.findForUpdateById(exceptionId).ifPresent(this::transitionIfDue);
    }

    /**
     * Reports whether the Intern's work month contains a pending or overdue submitted exception request.
     *
     * @param internUserId Intern whose period is being checked
     * @param month calendar work month
     * @return {@code true} when an unresolved request is attached to an attendance row in the month
     */
    @Transactional(readOnly = true)
    public boolean hasUnresolvedExceptionRequest(long internUserId, YearMonth month) {
        LocalDate monthStart = month.atDay(1);
        return exceptions.existsUnresolvedRequestForInternAndWorkDate(
                internUserId, monthStart, monthStart.plusMonths(1));
    }

    private boolean transitionIfDue(AttendanceExceptionEntity row) {
        Instant now = clock.instant();
        if (!row.isOverdueAt(now)) {
            return false;
        }
        row.markOverdue();
        exceptions.saveAndFlush(row);
        AttendanceRecordEntity record = records.findById(row.attendanceRecordId()).orElseThrow();
        notifications.publish(
                new NotificationEvent(NotificationType.SYSTEM, "OVERDUE",
                        "Attendance exception request overdue",
                        "Attendance exception request (" + row.id() + ") is awaiting a Mentor decision."),
                new NotificationAction("/attendance", false, null),
                recipients.forIntern(record.internUserId()));
        return true;
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
