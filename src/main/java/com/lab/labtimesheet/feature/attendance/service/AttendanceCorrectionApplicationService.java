package com.lab.labtimesheet.feature.attendance.service;

import com.lab.labtimesheet.feature.calendar.service.CalendarApplicationService;

import com.lab.labtimesheet.feature.identity.model.AccountStatus;
import com.lab.labtimesheet.feature.internship.model.InternshipStatus;
import com.lab.labtimesheet.feature.identity.model.dto.AccountIdentity;
import com.lab.labtimesheet.feature.internship.model.dto.LockedAccountMutationEligibility;
import com.lab.labtimesheet.feature.identity.service.AccountService;
import com.lab.labtimesheet.feature.internship.service.InternshipService;
import com.lab.labtimesheet.feature.attendance.exception.CorrectionException;
import com.lab.labtimesheet.feature.attendance.exception.AttendanceRecordNotFoundException;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.calendar.model.AttendancePolicy;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRecord;
import com.lab.labtimesheet.platform.model.GlobalRole;
import com.lab.labtimesheet.platform.authorization.AuthorizationCapability;
import com.lab.labtimesheet.platform.authorization.AuthorizationPolicy;
import com.lab.labtimesheet.platform.authorization.AuthorizationRequest;
import com.lab.labtimesheet.feature.attendance.model.AttendanceViolations;
import com.lab.labtimesheet.feature.attendance.model.CorrectionEventType;
import com.lab.labtimesheet.feature.attendance.model.CorrectionStatus;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionDecision;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionEventView;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionRequestCommand;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionSummary;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionView;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceCorrectionEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceCorrectionEventEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceRecordEntity;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceCorrectionEventRepository;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceCorrectionRepository;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceRecordRepository;
import com.lab.labtimesheet.feature.notification.model.NotificationType;
import com.lab.labtimesheet.feature.notification.model.dto.NotificationAction;
import com.lab.labtimesheet.feature.notification.model.dto.NotificationEvent;
import com.lab.labtimesheet.feature.notification.model.dto.NotificationRecipient;
import com.lab.labtimesheet.feature.notification.service.NotificationService;
import com.lab.labtimesheet.platform.model.GlobalRole;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Transactional boundary for missed-checkout correction submission, Mentor decisions, and overdue transitions.
 * Raw attendance punches remain unchanged; an approved proposal is used only as effective checkout. Submission,
 * decisions, and overdue reminders publish notifications in the same transaction.
 */
@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class AttendanceCorrectionApplicationService {

    private final Clock clock;
    private final AttendanceRecordRepository records;
    private final AttendanceCorrectionRepository corrections;
    private final AttendanceCorrectionEventRepository events;
    private final AccountService accounts;
    private final InternshipService internships;
    private final CalendarApplicationService calendar;
    private final TransactionTemplate transactions;
    private final NotificationService notifications;
    private final AuthorizationPolicy authorizationPolicy;
    private final AttendanceExceptionNotificationRecipients recipients;

    /**
     * Lists retained correction requests visible to an owning Intern or active global Mentor.
     *
     * <p>Pending rows at or beyond their decision deadline are transitioned to overdue before
     * the actionable queue is built. Detail and decision methods still repeat ownership,
     * active-role, and deadline checks before returning or mutating state.</p>
     *
     * @param actor authenticated Attendance actor
     * @return actionable pending and overdue summaries first, followed by retained decision history in newest-first order
     */
    @Transactional
    public List<CorrectionSummary> list(AttendanceActor actor) {
        if (actor == null) {
            throw new AccessDeniedException("An attendance actor is required");
        }
        AccountIdentity identity = accounts.requireIdentityById(actor.userId());
        if (!identity.role().name().equals(actor.role().name())) {
            throw new AccessDeniedException("An active matching account is required");
        }
        AuthorizationRequest policyRequest = AttendanceAuthorizationRequests.request(
                actor, identity.status() == AccountStatus.ACTIVE, actor.userId(), null);
        AttendanceAuthorizationRequests.requireAllowed(
                authorizationPolicy, AuthorizationCapability.VIEW_INTERN_ATTENDANCE, policyRequest);
        List<CorrectionSummary> visible = switch (actor.role()) {
            case INTERN -> corrections.findSummariesByInternUserId(actor.userId());
            case MENTOR -> corrections.findAllSummaries();
            case ADMIN -> corrections.findAllSummaries();
        };
        if (actor.role() != GlobalRole.ADMIN) {
            expireVisiblePending(actor, visible);
            visible = switch (actor.role()) {
                case INTERN -> corrections.findSummariesByInternUserId(actor.userId());
                case MENTOR -> corrections.findAllSummaries();
                case ADMIN -> corrections.findAllSummaries();
            };
        }
        return visible.stream()
                .sorted(Comparator.comparing(
                                (CorrectionSummary row) -> !CorrectionStatus.PENDING.name().equals(row.status())
                                        && !CorrectionStatus.OVERDUE.name().equals(row.status()))
                        .thenComparing(CorrectionSummary::submittedAt,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(CorrectionSummary::id, Comparator.reverseOrder()))
                .toList();
    }

    private void expireVisiblePending(AttendanceActor actor, List<CorrectionSummary> visible) {
        Instant now = clock.instant();
        List<CorrectionSummary> due = visible.stream()
                .filter(summary -> CorrectionStatus.PENDING.name().equals(summary.status()))
                .filter(summary -> summary.decisionDeadline() != null)
                .filter(summary -> !now.isBefore(summary.decisionDeadline()))
                .sorted(Comparator.comparing(CorrectionSummary::id))
                .toList();
        if (due.isEmpty()) {
            return;
        }
        List<Long> ownerIds = due.stream()
                .map(CorrectionSummary::internUserId)
                .distinct()
                .sorted()
                .toList();
        Map<Long, LockedAccountMutationEligibility> lockedAccounts = lockAccounts(
                java.util.stream.Stream.concat(java.util.stream.Stream.of(actor.userId()), ownerIds.stream())
                        .distinct()
                        .sorted()
                        .toList());
        requireActiveExpiryActor(actor, lockedAccounts);
        Map<Long, AccountIdentity> ownerIdentities = identities(ownerIds);
        for (CorrectionSummary candidate : due) {
            AttendanceCorrectionEntity correction = lockedCorrection(candidate.id());
            expireIfNeeded(correction, clock.instant(), ownerIdentities.get(candidate.internUserId()));
        }
    }

    private void requireActiveExpiryActor(
            AttendanceActor actor, Map<Long, LockedAccountMutationEligibility> lockedAccounts) {
        if (actor.role() == GlobalRole.MENTOR) {
            requireActiveMentor(actor.userId(), lockedAccounts);
        } else if (actor.role() == GlobalRole.INTERN) {
            requireActiveIntern(actor.userId(), lockedAccounts);
        } else {
            throw new AccessDeniedException("Correction list is outside the requested scope");
        }
    }

    private static void requireActiveIntern(
            long userId, Map<Long, LockedAccountMutationEligibility> lockedAccounts) {
        LockedAccountMutationEligibility locked = lockedAccounts.get(userId);
        if (locked == null
                || locked.role() != GlobalRole.INTERN
                || locked.accountStatus() != AccountStatus.ACTIVE
                || locked.internshipStatus().orElse(null) != InternshipStatus.ACTIVE) {
            throw new AccessDeniedException("An active Intern is required");
        }
    }

    /**
     * Submits one correction through the inclusive scheduled-end-plus-48-hour deadline.
     *
     * <p>Submission publishes to the active responsible Mentor or fallback Admins under D47 after the correction
     * row and immutable submitted event are flushed. SMTP absence is retained as {@code UNAVAILABLE} by the
     * notification boundary without rolling back the correction.</p>
     *
     * @param actor owning Intern
     * @param attendanceRecordId missing-checkout attendance row
     * @param command same-local-date proposal and reason
     * @return correction view with raw/effective distinction
     */
    @Transactional
    public CorrectionView submit(
            AttendanceActor actor, long attendanceRecordId, CorrectionRequestCommand command) {
        if (actor == null) {
            throw new AccessDeniedException("An attendance actor is required");
        }
        if (command == null) {
            throw new IllegalArgumentException("Correction command is required");
        }
        lockAccounts(List.of(actor.userId()));
        AttendanceRecordEntity entity = records.findById(attendanceRecordId)
                .orElseThrow(AttendanceRecordNotFoundException::new);
        AttendanceRecord record = recordFrom(entity);
        AccountIdentity actorIdentity = accounts.requireIdentityById(actor.userId());
        AccountIdentity targetIdentity = accounts.requireIdentityById(record.internId());
        if (targetIdentity.role() != GlobalRole.INTERN || actor.role() != GlobalRole.INTERN
                || record.internId() != actor.userId()) {
            if (actor.role() == GlobalRole.INTERN) {
                throw new AttendanceRecordNotFoundException();
            }
            throw new AccessDeniedException("Only an Intern may submit a correction");
        }
        boolean activeActor = actorIdentity.status() == AccountStatus.ACTIVE
                && actorIdentity.role() == actor.role();
        AuthorizationRequest policyRequest = AttendanceAuthorizationRequests.request(
                actor, activeActor, record.internId(), actorIdentity.status().name());
        boolean submissionAllowed = authorizationPolicy.allows(
                AuthorizationCapability.SUBMIT_ATTENDANCE_REQUEST, policyRequest);
        if (!submissionAllowed && activeActor && actor.role() == GlobalRole.INTERN) {
            throw new AttendanceRecordNotFoundException();
        }
        if (!submissionAllowed) {
            throw new AccessDeniedException("Attendance request submission is not permitted");
        }
        if (record.checkOutAt() != null) {
            throw new CorrectionException("Corrections require a missing raw checkout");
        }
        Instant now = clock.instant();
        Instant scheduledEnd = scheduledEnd(record);
        Instant checkoutCutoff = scheduledEnd.plusSeconds(record.policy().checkoutGraceMinutes() * 60L);
        Instant submissionDeadline = AttendanceRequestWindows.submissionDeadline(scheduledEnd);
        if (!now.isAfter(checkoutCutoff)) {
            throw new CorrectionException("Correction opens after the attached checkout cutoff");
        }
        if (now.isAfter(submissionDeadline)) {
            throw new CorrectionException("Correction submission deadline has passed");
        }
        Instant proposal = command.proposedCheckout().atZone(record.policy().zoneId()).toInstant();
        if (!proposal.isAfter(record.checkInAt())
                || proposal.isAfter(now)
                || !command.proposedCheckout().toLocalDate().equals(record.workDate())) {
            throw new CorrectionException("Proposed checkout must be after check-in, same date, and not future");
        }
        if (corrections.findByAttendanceRecordId(attendanceRecordId).isPresent()) {
            throw new CorrectionException("One correction request already exists for this attendance row");
        }
        AttendanceCorrectionEntity correction = new AttendanceCorrectionEntity(
                attendanceRecordId,
                proposal,
                command.reason(),
                now,
                submissionDeadline,
                AttendanceRequestWindows.decisionDeadline(now));
        try {
            correction = corrections.saveAndFlush(correction);
            append(correction, CorrectionEventType.SUBMITTED, null, CorrectionStatus.PENDING, actor.userId(), null, now);
            publishSubmissionNotification(recipients.forIntern(actor.userId()));
            return view(actor, correction, record);
        } catch (DataIntegrityViolationException conflict) {
            throw new CorrectionException("One correction request already exists for this attendance row", conflict);
        }
    }

    /**
     * Applies an approve, reject, amend, or reverse transition for the active responsible Mentor.
     *
     * <p>Authorization, request-time expiry for pending rows, and the state transition share one independent
     * {@code REQUIRES_NEW} transaction and one target-row lock.</p>
     *
     * @param actor authenticated responsible Mentor
     * @param correctionId correction identifier to lock and transition
     * @param decision requested state transition
     * @param note optional decision note retained in the event history
     * @param reason mandatory justification for amend and reverse transitions
     * @return corrected attendance view with the raw/effective distinction
     */
    public CorrectionView decide(
            AttendanceActor actor, long correctionId, CorrectionDecision decision, String note, String reason) {
        if (actor == null) {
            throw new AccessDeniedException("An attendance actor is required");
        }
        if (decision == null) {
            throw new IllegalArgumentException("Correction decision is required");
        }
        return independentTransactions()
                .execute(status -> decideInTransaction(actor, correctionId, decision, note, reason));
    }

    /**
     * Reports whether the Intern's work month contains a pending or overdue correction request.
     * Period finalization must consult this property before concluding monthly attendance.
     *
     * @param internUserId Intern whose period is being checked
     * @param month calendar work month
     * @return {@code true} when an unresolved correction is attached to an attendance row in the month
     */
    @Transactional(readOnly = true)
    public boolean hasUnresolvedCorrection(long internUserId, YearMonth month) {
        if (internUserId <= 0 || month == null) {
            throw new IllegalArgumentException("Intern and business month are required");
        }
        LocalDate monthStart = month.atDay(1);
        return corrections.existsUnresolvedCorrectionForInternAndWorkDate(
                internUserId, monthStart, monthStart.plusMonths(1));
    }

    /**
     * Returns one authorized correction view and applies the request-time expiry guard.
     *
     * @param actor owning Intern or active Mentor reader
     * @param correctionId correction identifier
     * @return current correction state and immutable events
     */
    @Transactional
    public CorrectionView view(AttendanceActor actor, long correctionId) {
        if (actor == null) {
            throw new AccessDeniedException("An attendance actor is required");
        }
        long ownerId = corrections.findInternUserIdById(correctionId)
                .orElseThrow(AttendanceRecordNotFoundException::new);
        Map<Long, LockedAccountMutationEligibility> lockedAccounts = lockAccounts(
                accountIds(actor.userId(), ownerId));
        AccountIdentity ownerIdentity = accounts.requireIdentityById(ownerId);
        AttendanceCorrectionEntity correction = lockedCorrection(correctionId);
        AttendanceRecord record = recordFor(correction);
        if (ownerIdentity.role() != GlobalRole.INTERN
                || actor.role() == GlobalRole.INTERN && ownerId != actor.userId()) {
            throw new AttendanceRecordNotFoundException();
        }
        LockedAccountMutationEligibility lockedActor = lockedAccounts.get(actor.userId());
        boolean activeActor = lockedActor != null
                && lockedActor.accountStatus() == AccountStatus.ACTIVE
                && lockedActor.role() == actor.role();
        AuthorizationRequest policyRequest = AttendanceAuthorizationRequests.request(
                actor, activeActor, record.internId(), null);
        boolean viewAllowed = authorizationPolicy.allows(
                AuthorizationCapability.VIEW_INTERN_ATTENDANCE, policyRequest);
        if (!viewAllowed && activeActor && actor.role() == GlobalRole.INTERN) {
            throw new AttendanceRecordNotFoundException();
        }
        if (!viewAllowed) {
            throw new AccessDeniedException("Attendance request is outside the requested scope");
        }
        expireIfNeeded(correction, clock.instant(), ownerIdentity);
        return view(actor, correction, record);
    }

    /**
     * Locks and evaluates all corrections for an already loaded attendance history in one transaction boundary.
     * The bulk lock is acquired before sampling the server clock, so first history access cannot miss a deadline
     * while waiting on another decision. Approved proposals become effective values; raw attendance rows remain
     * unchanged. Pending or unlocked expired rows are transitioned and event history is appended before the map is
     * returned.
     *
     * @param historyRows attendance rows already loaded by the caller's history query
     * @return effective checkout by raw attendance identifier, including {@code null} values
     */
    @Transactional
    public Map<Long, Instant> prepareHistory(List<AttendanceRecordEntity> historyRows) {
        if (historyRows.isEmpty()) {
            return Map.of();
        }
        List<Long> attendanceRecordIds = historyRows.stream()
                .map(AttendanceRecordEntity::id)
                .toList();
        Map<Long, AttendancePolicy> policiesByVersionId = calendar.policiesByVersionIds(historyRows.stream()
                .map(AttendanceRecordEntity::policyVersionId)
                .collect(java.util.stream.Collectors.toSet()));
        Map<Long, AttendanceRecord> recordsById = historyRows.stream()
                .collect(java.util.stream.Collectors.toMap(
                        AttendanceRecordEntity::id, row -> toDomain(row, policiesByVersionId)));
        List<Long> internIds = recordsById.values().stream()
                .map(AttendanceRecord::internId)
                .distinct()
                .sorted()
                .toList();
        Map<Long, Long> internByRecordId = new HashMap<>();
        recordsById.forEach((recordId, record) -> internByRecordId.put(recordId, record.internId()));
        lockAccounts(internIds);
        Map<Long, AccountIdentity> ownerIdentities = identities(internIds);
        List<AttendanceCorrectionEntity> correctionRows = corrections
                .findByAttendanceRecordIdInForUpdate(attendanceRecordIds);
        Map<Long, AttendanceCorrectionEntity> byAttendanceRecord = new HashMap<>();
        correctionRows.forEach(correction -> byAttendanceRecord.put(correction.attendanceRecordId(), correction));
        Instant now = clock.instant();
        correctionRows.forEach(correction -> expireIfNeeded(
                correction, now, ownerIdentities.get(internByRecordId.get(correction.attendanceRecordId()))));

        Map<Long, Instant> effectiveCheckouts = new HashMap<>();
        for (AttendanceRecordEntity historyRow : historyRows) {
            AttendanceRecord raw = recordsById.get(historyRow.id());
            AttendanceCorrectionEntity correction = byAttendanceRecord.get(historyRow.id());
            effectiveCheckouts.put(
                    historyRow.id(),
                    correction != null && correction.status() == CorrectionStatus.APPROVED
                            ? correction.requestedCheckoutAt()
                            : raw.checkOutAt());
        }
        return effectiveCheckouts;
    }

    /**
     * Processes a bounded batch of expired corrections idempotently.
     *
     * @param batchSize maximum number of rows to lock in this transaction
     * @return number of newly overdue correction requests
     */
    @Transactional
    public int expire(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        Instant selectionTime = clock.instant();
        List<AttendanceCorrectionRepository.ExpiredRecipientRoute> candidates = corrections
                .findExpiredRecipientRoutes(selectionTime, PageRequest.of(0, batchSize));
        List<Long> internIds = candidates.stream()
                .map(AttendanceCorrectionRepository.ExpiredRecipientRoute::getInternUserId)
                .distinct()
                .sorted()
                .toList();
        lockAccounts(internIds);
        int changed = 0;
        for (AttendanceCorrectionRepository.ExpiredRecipientRoute candidate : candidates) {
            AttendanceCorrectionEntity correction = lockedCorrection(candidate.getCorrectionId());
            Instant now = clock.instant();
            if (correction.status() != CorrectionStatus.PENDING || now.isBefore(correction.decisionDeadline())) {
                continue;
            }
            CorrectionStatus from = correction.status();
            correction.markOverdue(now);
            corrections.saveAndFlush(correction);
            append(correction, CorrectionEventType.OVERDUE, from, CorrectionStatus.OVERDUE, null, null, now);
            publishOverdueReminder(correction, candidate.getInternUserId());
            changed++;
        }
        return changed;
    }

    private CorrectionView view(AttendanceActor actor, AttendanceCorrectionEntity correction, AttendanceRecord record) {
        Instant effective = correction.status() == CorrectionStatus.APPROVED
                ? correction.requestedCheckoutAt()
                : record.checkOutAt();
        List<CorrectionEventView> eventViews = events.findByCorrectionIdOrderByOccurredAtAscIdAsc(correction.id()).stream()
                .map(AttendanceCorrectionEventEntity::toView)
                .toList();
        return new CorrectionView(
                correction.id(),
                correction.attendanceRecordId(),
                record.internId(),
                record.checkOutAt(),
                LocalDateTime.ofInstant(correction.requestedCheckoutAt(), record.policy().zoneId()),
                effective,
                correction.reason(),
                correction.status(),
                correction.submittedAt(),
                correction.submissionDeadline(),
                correction.decisionDeadline(),
                correction.lockedAt(),
                record.policy(),
                record.violations(clock.instant(), effective),
                eventViews);
    }

    private void append(
            AttendanceCorrectionEntity correction,
            CorrectionEventType type,
            CorrectionStatus from,
            CorrectionStatus to,
            Long actorUserId,
            String note,
            Instant occurredAt) {
        events.saveAndFlush(new AttendanceCorrectionEventEntity(
                correction.id(), type, from, to, actorUserId, note, occurredAt));
    }

    private AttendanceCorrectionEntity lockedCorrection(long correctionId) {
        return corrections.findForUpdateById(correctionId)
                .orElseThrow(AttendanceRecordNotFoundException::new);
    }

    private AttendanceRecord recordFor(AttendanceCorrectionEntity correction) {
        return records.findById(correction.attendanceRecordId())
                .map(this::recordFrom)
                .orElseThrow(AttendanceRecordNotFoundException::new);
    }

    private AttendanceRecord recordFrom(AttendanceRecordEntity entity) {
        return toDomain(entity, calendar.policiesByVersionIds(Set.of(entity.policyVersionId())));
    }

    private static AttendanceRecord toDomain(
            AttendanceRecordEntity entity, Map<Long, AttendancePolicy> policiesByVersionId) {
        AttendancePolicy policy = policiesByVersionId.get(entity.policyVersionId());
        if (policy == null) {
            throw new IllegalStateException("Attendance policy version not found");
        }
        return entity.toDomain(policy);
    }

    private void expireIfNeeded(
            AttendanceCorrectionEntity correction, Instant now, AccountIdentity ownerIdentity) {
        if (correction.status() == CorrectionStatus.PENDING && !now.isBefore(correction.decisionDeadline())) {
            CorrectionStatus from = correction.status();
            correction.markOverdue(now);
            corrections.saveAndFlush(correction);
            append(correction, CorrectionEventType.OVERDUE, from, CorrectionStatus.OVERDUE, null, null, now);
            publishOverdueReminder(correction, ownerIdentity.id());
        }
    }

    private void publishOverdueReminder(AttendanceCorrectionEntity correction, long internId) {
        notifications.publish(
                new NotificationEvent(
                        NotificationType.SYSTEM,
                        "OVERDUE",
                        "Correction request overdue",
                        "An undecided missed-checkout correction is overdue."),
                new NotificationAction("/attendance/corrections", false, null),
                recipients.forIntern(internId));
    }

    private void publishSubmissionNotification(List<NotificationRecipient> recipients) {
        notifications.publish(
                new NotificationEvent(
                        NotificationType.CORRECTION_SUBMITTED,
                        "SUBMITTED",
                        "Correction submitted",
                        "A missed-checkout correction is awaiting Mentor review."),
                new NotificationAction("/attendance", false),
                recipients);
    }

    private void publishDecisionNotification(AccountIdentity identity, String transition) {
        notifications.publish(
                new NotificationEvent(
                        NotificationType.CORRECTION_DECIDED,
                        transition,
                        "Correction decision",
                        "Your missed-checkout correction has a new decision."),
                new NotificationAction("/attendance", false),
                List.of(new NotificationRecipient(identity.id(), identity.email())));
    }

    private Map<Long, LockedAccountMutationEligibility> lockAccounts(Collection<Long> accountIds) {
        try {
            return internships.lockedAccountMutationEligibility(accountIds).stream()
                    .collect(java.util.stream.Collectors.toMap(
                            LockedAccountMutationEligibility::userId, eligibility -> eligibility));
        } catch (IllegalArgumentException missingAccount) {
            throw new AccessDeniedException("Attendance account is not available", missingAccount);
        }
    }

    private Map<Long, AccountIdentity> identities(Collection<Long> accountIds) {
        Map<Long, AccountIdentity> result = new HashMap<>();
        accountIds.forEach(id -> result.put(id, accounts.requireIdentityById(id)));
        return result;
    }

    private static List<Long> accountIds(long actorId, List<Long> mentorIds) {
        return java.util.stream.Stream.concat(
                        java.util.stream.Stream.of(actorId),
                        mentorIds.stream())
                .distinct()
                .sorted()
                .toList();
    }

    private static List<Long> accountIds(long firstId, long secondId) {
        return java.util.stream.Stream.of(firstId, secondId).distinct().sorted().toList();
    }

    private CorrectionView decideInTransaction(
            AttendanceActor actor, long correctionId, CorrectionDecision decision, String note, String reason) {
        long ownerId = corrections.findInternUserIdById(correctionId)
                .orElseThrow(AttendanceRecordNotFoundException::new);
        Map<Long, LockedAccountMutationEligibility> lockedAccounts = lockAccounts(
                accountIds(actor.userId(), ownerId));
        AccountIdentity ownerIdentity = accounts.requireIdentityById(ownerId);
        LockedAccountMutationEligibility lockedActor = lockedAccounts.get(actor.userId());
        boolean activeAccount = lockedActor != null
                && lockedActor.accountStatus() == AccountStatus.ACTIVE
                && lockedActor.role() == actor.role();
        boolean responsible = internships.responsibleMentorUserId(ownerId)
                .map(id -> id.equals(actor.userId()))
                .orElse(false);
        AttendanceAuthorizationRequests.requireAllowed(
                authorizationPolicy,
                AuthorizationCapability.DECIDE_ATTENDANCE_REQUEST,
                AttendanceAuthorizationRequests.decisionRequest(actor, activeAccount, responsible));

        AttendanceCorrectionEntity correction = lockedCorrection(correctionId);
        Instant now = clock.instant();

        if (decision == CorrectionDecision.APPROVE || decision == CorrectionDecision.REJECT) {
            if (correction.status() != CorrectionStatus.PENDING && correction.status() != CorrectionStatus.OVERDUE) {
                throw new CorrectionException("Only pending or overdue correction can be approved or rejected");
            }
            if (correction.status() == CorrectionStatus.PENDING && !now.isBefore(correction.decisionDeadline())) {
                expireIfNeeded(correction, now, ownerIdentity);
            }
        } else if (decision == CorrectionDecision.AMEND || decision == CorrectionDecision.REVERSE) {
            if (correction.status() != CorrectionStatus.APPROVED && correction.status() != CorrectionStatus.REJECTED) {
                throw new CorrectionException("Only approved or rejected correction can be amended or reversed");
            }
            if (reason == null || reason.isBlank()) {
                throw new CorrectionException("A reason is required to amend or reverse a correction decision");
            }
        }

        AttendanceRecord record = recordFor(correction);
        CorrectionStatus from = correction.status();
        String normalizedNote = normalizeNote(note);
        String normalizedReason = normalizeNote(reason);
        try {
            switch (decision) {
                case APPROVE -> correction.approve(actor.userId(), now, normalizedNote);
                case REJECT -> correction.reject(actor.userId(), now, normalizedNote);
                case AMEND -> correction.amend(actor.userId(), now, normalizedNote);
                case REVERSE -> correction.reverse(actor.userId(), now);
            }
            corrections.saveAndFlush(correction);
            CorrectionStatus to = correction.status();
            String eventNote = switch (decision) {
                case APPROVE, REJECT -> normalizedNote;
                case AMEND, REVERSE -> normalizedReason;
            };
            append(correction, eventType(decision), from, to, actor.userId(), eventNote, now);
            publishDecisionNotification(ownerIdentity, transition(decision));
            return view(actor, correction, record);
        } catch (ObjectOptimisticLockingFailureException conflict) {
            throw new CorrectionException("Correction changed concurrently; reload before deciding", conflict);
        } catch (IllegalStateException | IllegalArgumentException invalid) {
            throw new CorrectionException(invalid.getMessage(), invalid);
        }
    }

    private void requireActiveMentor(
            long userId, Map<Long, LockedAccountMutationEligibility> lockedAccounts) {
        LockedAccountMutationEligibility locked = lockedAccounts.get(userId);
        if (locked == null
                || locked.role() != GlobalRole.MENTOR
                || locked.accountStatus() != AccountStatus.ACTIVE) {
            throw new AccessDeniedException("An active Mentor is required");
        }
    }

    private static void requireIntern(AttendanceActor actor) {
        if (actor == null || actor.role() != GlobalRole.INTERN) {
            throw new AccessDeniedException("Only the owning Intern may request corrections");
        }
    }

    private static Instant scheduledEnd(AttendanceRecord record) {
        return ZonedDateTime.of(record.workDate(), record.policy().scheduledEnd(), record.policy().zoneId()).toInstant();
    }

    private static CorrectionEventType eventType(CorrectionDecision decision) {
        return switch (decision) {
            case APPROVE -> CorrectionEventType.APPROVED;
            case REJECT -> CorrectionEventType.REJECTED;
            case AMEND -> CorrectionEventType.AMENDED;
            case REVERSE -> CorrectionEventType.REVERSED;
        };
    }

    private static String transition(CorrectionDecision decision) {
        return switch (decision) {
            case APPROVE -> "APPROVED";
            case REJECT -> "REJECTED";
            case AMEND -> "AMENDED";
            case REVERSE -> "REVERSED";
        };
    }

    private static String normalizeNote(String note) {
        return note == null || note.isBlank() ? null : note.strip();
    }

    private TransactionTemplate independentTransactions() {
        TransactionTemplate independent = new TransactionTemplate(transactions.getTransactionManager());
        independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return independent;
    }
}
