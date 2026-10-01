package com.lab.labtimesheet.feature.attendance.service;

import com.lab.labtimesheet.feature.attendance.exception.AttendanceExceptionRequestException;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.AttendanceExceptionKind;
import com.lab.labtimesheet.feature.attendance.model.AttendanceExceptionSource;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRecord;
import com.lab.labtimesheet.feature.attendance.model.AttendanceViolations;
import com.lab.labtimesheet.feature.attendance.model.CorrectionStatus;
import com.lab.labtimesheet.feature.attendance.model.dto.AttendanceHistoryItem;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceCorrectionEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceExceptionEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceRecordEntity;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceCorrectionRepository;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceExceptionRepository;
import com.lab.labtimesheet.feature.attendance.repository.AttendancePeriodRepository;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceRecordRepository;
import com.lab.labtimesheet.feature.calendar.model.AttendancePolicy;
import com.lab.labtimesheet.feature.calendar.service.CalendarApplicationService;
import com.lab.labtimesheet.feature.identity.model.AccountStatus;
import com.lab.labtimesheet.feature.identity.model.dto.AccountIdentity;
import com.lab.labtimesheet.feature.identity.service.AccountService;
import com.lab.labtimesheet.feature.internship.service.InternshipService;
import com.lab.labtimesheet.feature.notification.model.NotificationType;
import com.lab.labtimesheet.feature.notification.model.dto.NotificationAction;
import com.lab.labtimesheet.feature.notification.model.dto.NotificationEvent;
import com.lab.labtimesheet.feature.notification.service.NotificationService;
import com.lab.labtimesheet.platform.model.GlobalRole;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** Owns Intern attendance-exception submissions, eligibility checks, deadlines, and submission notifications. */
@Service
public class AttendanceExceptionRequestService {

    private final Clock clock;
    private final AttendanceRecordRepository records;
    private final AttendanceExceptionRepository exceptions;
    private final AttendancePeriodRepository periods;
    private final AttendanceCorrectionRepository corrections;
    private final AttendanceExceptionService exceptionPersistence;
    private final CalendarApplicationService calendar;
    private final AccountService accounts;
    private final InternshipService internships;
    private final AttendanceExceptionNotificationRecipients recipients;
    private final NotificationService notifications;
    private final AttendanceApplicationService attendance;
    private final TransactionTemplate historyTransaction;

    AttendanceExceptionRequestService(Clock clock, AttendanceRecordRepository records,
            AttendanceExceptionRepository exceptions, AttendancePeriodRepository periods,
            AttendanceCorrectionRepository corrections, AttendanceExceptionService exceptionPersistence,
            CalendarApplicationService calendar, AccountService accounts, InternshipService internships,
            AttendanceExceptionNotificationRecipients recipients,
            NotificationService notifications, AttendanceApplicationService attendance,
            PlatformTransactionManager transactionManager) {
        this.clock = clock;
        this.records = records;
        this.exceptions = exceptions;
        this.periods = periods;
        this.corrections = corrections;
        this.exceptionPersistence = exceptionPersistence;
        this.calendar = calendar;
        this.accounts = accounts;
        this.internships = internships;
        this.recipients = recipients;
        this.notifications = notifications;
        this.attendance = attendance;
        this.historyTransaction = new TransactionTemplate(transactionManager);
        this.historyTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * Reads the one attendance history row an Intern may reference in an excuse request.
     *
     * @param actor authenticated Attendance actor
     * @param attendanceRecordId retained attendance row identifier
     * @return history projection for the requested owned row
     * @throws AccessDeniedException when the row is missing or belongs to another Intern
     */
    @Transactional(readOnly = true)
    public AttendanceHistoryItem requestableRow(AttendanceActor actor, long attendanceRecordId) {
        AttendanceRecordEntity row = records.findByIdAndInternUserId(attendanceRecordId, actor.userId())
                .orElseThrow(AttendanceExceptionRequestService::unavailableRecord);
        return historyTransaction.execute(status -> attendance.history(
                        actor, actor.userId(), row.workDate(), row.workDate()))
                .stream()
                .filter(item -> item.attendanceRecordId() == attendanceRecordId)
                .findFirst()
                .orElseThrow(AttendanceExceptionRequestService::unavailableRecord);
    }

    /**
     * Submits one reasoned request for the authenticated Intern's recorded late or early violation.
     * The request and its submission notification share one transaction; PostgreSQL's unique row/kind predicate
     * arbitrates concurrent duplicates.
     *
     * @param actor authenticated Attendance actor
     * @param attendanceRecordId retained attendance row
     * @param kind recorded violation kind being requested for excuse
     * @param reason nonblank Intern explanation
     * @return persisted attendance exception identifier
     * @throws AccessDeniedException when the actor is not an active Intern or does not own the row
     * @throws AttendanceExceptionRequestException when the violation, deadline, period, or uniqueness rule refuses it
     */
    @Transactional
    public long requestExcuse(AttendanceActor actor, long attendanceRecordId, AttendanceExceptionKind kind,
            String reason) {
        requireActiveIntern(actor);
        if (kind == null) {
            throw new IllegalArgumentException("An attendance exception kind is required");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("An exception reason is required");
        }

        AttendanceRecordEntity row = records.findByIdAndInternUserId(attendanceRecordId, actor.userId())
                .orElseThrow(AttendanceExceptionRequestService::unavailableRecord);
        AttendanceRecord record = toDomain(row);
        Instant now = clock.instant();
        Instant effectiveCheckout = record.checkOutAt();
        AttendanceCorrectionEntity correction = corrections.findByAttendanceRecordId(attendanceRecordId).orElse(null);
        if (correction != null && correction.status() == CorrectionStatus.APPROVED) {
            effectiveCheckout = correction.requestedCheckoutAt();
        }
        AttendanceViolations violations = record.violations(now, effectiveCheckout);
        boolean recordedViolation = kind == AttendanceExceptionKind.LATE_ARRIVAL
                ? violations.late()
                : violations.earlyDeparture();
        if (!recordedViolation) {
            throw new AttendanceExceptionRequestException("The attendance row does not record this violation");
        }

        Instant scheduledEnd = ZonedDateTime.of(record.workDate(), record.policy().scheduledEnd(),
                record.policy().zoneId()).toInstant();
        Instant submissionDeadline = AttendanceRequestWindows.submissionDeadline(scheduledEnd);
        if (now.isAfter(submissionDeadline)) {
            throw new AttendanceExceptionRequestException("The attendance exception submission deadline has passed");
        }
        LocalDate month = record.workDate().withDayOfMonth(1);
        if (periods.existsByInternUserIdAndPeriodMonthAndStatus(actor.userId(), month, "FINALIZED")) {
            throw new AttendanceExceptionRequestException("The attendance period is finalized");
        }
        if (exceptions.findByAttendanceRecordIdAndViolationKind(attendanceRecordId, kind.name()).isPresent()) {
            throw duplicateRequest();
        }

        long exceptionId;
        try {
            exceptionId = exceptionPersistence.open(attendanceRecordId, kind, AttendanceExceptionSource.REQUEST,
                    reason.strip(), submissionDeadline, AttendanceRequestWindows.decisionDeadline(now));
        } catch (DataIntegrityViolationException duplicate) {
            throw duplicateRequest(duplicate);
        }
        publishSubmission(exceptionId, actor.userId(), kind);
        return exceptionId;
    }

    private void requireActiveIntern(AttendanceActor actor) {
        if (actor == null || actor.role() != GlobalRole.INTERN
                || !internships.isEligibleIntern(actor.userId())) {
            throw new AccessDeniedException("An active Intern is required");
        }
        AccountIdentity identity = accounts.requireIdentityById(actor.userId());
        if (identity.role() != GlobalRole.INTERN || identity.status() != AccountStatus.ACTIVE) {
            throw new AccessDeniedException("An active Intern is required");
        }
    }

    private AttendanceRecord toDomain(AttendanceRecordEntity entity) {
        Map<Long, AttendancePolicy> policies = calendar.policiesByVersionIds(Set.of(entity.policyVersionId()));
        AttendancePolicy policy = policies.get(entity.policyVersionId());
        if (policy == null) {
            throw new IllegalStateException("Attendance policy version not found");
        }
        return entity.toDomain(policy);
    }

    private void publishSubmission(long exceptionId, long internId, AttendanceExceptionKind kind) {
        notifications.publish(
                new NotificationEvent(NotificationType.ATTENDANCE_EXCEPTION_SUBMITTED, "SUBMITTED",
                        "Attendance exception request submitted",
                        "An Intern submitted a " + kind.name().toLowerCase().replace('_', ' ')
                                + " exception request (" + exceptionId + ")."),
                new NotificationAction("/attendance", false, null), recipients.forIntern(internId));
    }

    private static AccessDeniedException unavailableRecord() {
        return new AccessDeniedException("Attendance record not found");
    }

    private static AttendanceExceptionRequestException duplicateRequest() {
        return new AttendanceExceptionRequestException("One exception request already exists for this attendance row");
    }

    private static AttendanceExceptionRequestException duplicateRequest(Throwable cause) {
        return new AttendanceExceptionRequestException(
                "One exception request already exists for this attendance row", cause);
    }
}
