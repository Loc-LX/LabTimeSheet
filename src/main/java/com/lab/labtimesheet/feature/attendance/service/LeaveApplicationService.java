package com.lab.labtimesheet.feature.attendance.service;

import com.lab.labtimesheet.feature.identity.model.AccountStatus;
import com.lab.labtimesheet.feature.identity.model.InternshipStatus;
import com.lab.labtimesheet.feature.identity.model.dto.AccountIdentity;
import com.lab.labtimesheet.feature.identity.model.dto.InternWorkWindow;
import com.lab.labtimesheet.feature.identity.model.dto.LockedAccountMutationEligibility;
import com.lab.labtimesheet.feature.identity.service.AccountService;
import com.lab.labtimesheet.feature.attendance.exception.LeaveException;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.AttendancePolicy;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRole;
import com.lab.labtimesheet.feature.attendance.model.LeaveStatus;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveAllocation;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveBalance;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveRequestCommand;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveRequestSummary;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveRequestView;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendancePolicyEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.LeaveRequestDayEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.LeaveRequestEntity;
import com.lab.labtimesheet.feature.attendance.repository.AttendancePolicyRepository;
import com.lab.labtimesheet.feature.attendance.repository.LeaveRequestDayRepository;
import com.lab.labtimesheet.feature.attendance.repository.LeaveRequestRepository;
import com.lab.labtimesheet.feature.notification.model.NotificationType;
import com.lab.labtimesheet.feature.notification.model.dto.NotificationAction;
import com.lab.labtimesheet.feature.notification.model.dto.NotificationEvent;
import com.lab.labtimesheet.feature.notification.model.dto.NotificationRecipient;
import com.lab.labtimesheet.feature.notification.service.NotificationService;
import com.lab.labtimesheet.platform.model.GlobalRole;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Transactional boundary for full-day leave requests and their frozen policy/quota allocations.
 * Pending and approved rows reserve quota; rejected and cancelled rows retain history but release it logically.
 * Submission and decision notifications are published in the same transaction; cancellation deliberately remains
 * silent under the notification requirements.
 */
@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class LeaveApplicationService {

    private static final List<String> RESERVED = List.of(LeaveStatus.PENDING.name(), LeaveStatus.APPROVED.name());

    private final Clock clock;
    private final AttendancePolicyRepository policies;
    private final LeaveRequestRepository requests;
    private final LeaveRequestDayRepository days;
    private final AccountService accounts;
    private final CalendarApplicationService calendar;
    private final TransactionTemplate transactions;
    private final NotificationService notifications;

    /**
     * Lists retained leave requests visible to the authenticated Attendance actor.
     *
     * <p>Interns receive only their own rows. Active global Mentors and Admins receive the
     * decision/read-only queue respectively. Pending rows whose first counted start has
     * arrived are locked and auto-rejected before the actionable queue is built, so a late
     * scheduler cannot leave stale decision affordances on this first-access read path.</p>
     *
     * @param actor authenticated Attendance actor
     * @return actionable pending summaries first, followed by retained history in newest-first order
     */
    @Transactional
    public List<LeaveRequestSummary> list(AttendanceActor actor) {
        AccountIdentity identity = requireListActor(actor);
        if (identity.status() != AccountStatus.ACTIVE) {
            throw new AccessDeniedException("An active account is required");
        }
        List<LeaveRequestEntity> visible = actor.role() == AttendanceRole.INTERN
                ? requests.findByInternUserIdOrderBySubmittedAtDescIdDesc(actor.userId())
                : requests.findAllByOrderBySubmittedAtDescIdDesc();
        expireVisiblePending(actor, visible);
        return visible.stream()
                .map(request -> new LeaveRequestSummary(
                        request.id(), request.internUserId(), request.startDate(), request.endDate(),
                        request.reason(), request.status(), request.submittedAt()))
                .sorted(Comparator.comparing(
                                (LeaveRequestSummary row) -> row.status() != LeaveStatus.PENDING)
                        .thenComparing(LeaveRequestSummary::submittedAt,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(LeaveRequestSummary::id, Comparator.reverseOrder()))
                .toList();
    }

    private void expireVisiblePending(AttendanceActor actor, List<LeaveRequestEntity> visible) {
        Instant now = clock.instant();
        List<LeaveRequestEntity> due = visible.stream()
                .filter(request -> request.status() == LeaveStatus.PENDING)
                .filter(request -> request.firstCountedStartAt() != null)
                .filter(request -> !now.isBefore(request.firstCountedStartAt()))
                .sorted(Comparator.comparing(LeaveRequestEntity::id))
                .toList();
        if (due.isEmpty()) {
            return;
        }
        List<Long> ownerIds = due.stream()
                .map(LeaveRequestEntity::internUserId)
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
        for (LeaveRequestEntity candidate : due) {
            LeaveRequestEntity request = lockedRequest(candidate.id());
            requireReader(actor, request, lockedAccounts);
            expireIfNeeded(request, clock.instant(), ownerIdentities.get(request.internUserId()));
        }
    }

    /**
     * Returns the selected month's frozen Leave reservation balance for the
     * authenticated Intern.
     *
     * <p>The read boundary uses only Attendance-owned allocation rows and the
     * policy timeline. It does not mutate requests or recalculate historical
     * allocations when a later policy is scheduled.</p>
     *
     * @param actor authenticated active Intern
     * @param month selected local business month
     * @return reserved, applicable quota, remaining, and cross-month counts
     */
    @Transactional(readOnly = true)
    public LeaveBalance balance(AttendanceActor actor, YearMonth month) {
        requireIntern(actor);
        if (month == null) {
            throw new IllegalArgumentException("Leave balance month is required");
        }
        AccountIdentity identity = accounts.requireIdentityById(actor.userId());
        if (identity.status() != AccountStatus.ACTIVE
                || identity.role() != GlobalRole.INTERN) {
            throw new AccessDeniedException("An active Intern is required");
        }
        LocalDate quotaMonth = month.atDay(1);
        int quota = timeline().resolve(quotaMonth).monthlyLeaveQuota();
        long reserved = days.countReserved(actor.userId(), quotaMonth, RESERVED);
        long crossMonth = days.countReservedCrossMonth(
                actor.userId(), quotaMonth, quotaMonth.plusMonths(1), RESERVED);
        int reservedDays = Math.toIntExact(reserved);
        return new LeaveBalance(
                month,
                reservedDays,
                quota,
                Math.max(0, quota - reservedDays),
                Math.toIntExact(crossMonth));
    }

    /**
     * Submits one full-day inclusive request and materializes eligible workdays with policy/quota snapshots.
     * The account and Intern profile are pessimistically locked through the allocation and quota writes, while the
     * requested dates are checked against the account service's inclusive lifecycle window.
     *
     * <p>The submission notification is written in this transaction for every active global Mentor. SMTP absence is
     * represented by the notification boundary and does not roll back the leave request.</p>
     *
     * @param actor authenticated Intern owner
     * @param command requested range and reason
     * @return persisted request and frozen allocations
     */
    @Transactional
    public LeaveRequestView submit(AttendanceActor actor, LeaveRequestCommand command) {
        requireIntern(actor);
        Objects.requireNonNull(command, "command");
        List<Long> mentorIds = accounts.activeGlobalMentorIdentities().stream()
                .map(AccountIdentity::id)
                .toList();
        Map<Long, LockedAccountMutationEligibility> lockedAccounts = lockAccounts(
                accountIds(actor.userId(), mentorIds));
        List<AccountIdentity> mentorIdentities = mentorIds.stream()
                .map(accounts::requireIdentityById)
                .toList();
        InternWorkWindow window = lockEligibleIntern(
                actor.userId(), command.startDate(), command.endDate());
        Instant now = clock.instant();
        List<AllocatedDate> allocations = allocations(window, command.startDate(), command.endDate());
        if (allocations.isEmpty()) {
            throw new LeaveException("Leave must contain at least one eligible workday");
        }
        Instant firstCountedStart = allocations.getFirst().startAt();
        if (!now.isBefore(firstCountedStart)) {
            throw new LeaveException("Leave cannot be created after its first counted start");
        }
        LeaveRequestEntity request = new LeaveRequestEntity(
                actor.userId(),
                command.startDate(),
                command.endDate(),
                command.reason(),
                now,
                firstCountedStart);
        if (!requests.findOverlaps(
                actor.userId(), command.startDate(), command.endDate(), RESERVED, null).isEmpty()) {
            throw new LeaveException("Leave overlaps an existing active request");
        }
        validateQuota(actor.userId(), allocations, null);
        try {
            request = requests.saveAndFlush(request);
            persistAllocations(request, allocations);
            publishSubmissionNotification(activeMentorRecipients(mentorIdentities, lockedAccounts));
            return view(request);
        } catch (DataIntegrityViolationException conflict) {
            throw new LeaveException("Leave overlaps an existing active request", conflict);
        }
    }

    private AccountIdentity requireListActor(AttendanceActor actor) {
        if (actor == null) {
            throw new AccessDeniedException("An attendance actor is required");
        }
        AccountIdentity identity = accounts.requireIdentityById(actor.userId());
        if (!identity.role().name().equals(actor.role().name())
                || actor.role() != AttendanceRole.INTERN
                && actor.role() != AttendanceRole.MENTOR
                && actor.role() != AttendanceRole.ADMIN) {
            throw new AccessDeniedException("Leave request list is outside the requested scope");
        }
        return identity;
    }

    /**
     * Edits a pending request before its first counted start, replacing allocations atomically after revalidation.
     *
     * <p>Authorization, expiry, quota validation, and allocation replacement share one independent
     * {@code REQUIRES_NEW} transaction and row lock. The independent boundary does not join an ambient caller
     * transaction, so a late pending request returns an internal sentinel only after automatic rejection commits;
     * the post-lock server-time expiry check runs before replacement lifecycle/date validation, and the public method
     * then reports that editing is closed even when the owner has since become ineligible. For a valid replacement,
     * the Account service's account and Intern-profile locks remain held through frozen allocation and quota
     * persistence.</p>
     *
     * @param actor authenticated Intern owner
     * @param requestId request identifier
     * @param command replacement inclusive range and reason
     * @return updated request with freshly frozen allocations
     */
    public LeaveRequestView edit(AttendanceActor actor, long requestId, LeaveRequestCommand command) {
        requireIntern(actor);
        Objects.requireNonNull(command, "command");
        MutationOutcome outcome = independentTransactions().execute(status -> editInTransaction(actor, requestId, command));
        if (outcome.expired()) {
            throw new LeaveException("Only pending leave before its first counted start can be edited");
        }
        return outcome.view();
    }

    private MutationOutcome editInTransaction(
            AttendanceActor actor, long requestId, LeaveRequestCommand command) {
        long ownerId = requests.findInternUserIdById(requestId)
                .orElseThrow(() -> new LeaveException("Leave request not found"));
        lockAccounts(List.of(actor.userId(), ownerId));
        AccountIdentity ownerIdentity = accounts.requireIdentityById(ownerId);
        LeaveRequestEntity request = lockedRequest(requestId);
        requireOwner(request, actor.userId());
        if (request.status() != LeaveStatus.PENDING) {
            throw new LeaveException("Only pending leave before its first counted start can be edited");
        }
        InternWorkWindow window = lockIntern(actor.userId(), command.startDate());
        Instant now = clock.instant();
        if (request.status() == LeaveStatus.PENDING && !now.isBefore(request.firstCountedStartAt())) {
            request.autoReject(now);
            requests.saveAndFlush(request);
            publishDecisionNotification(ownerIdentity, "AUTO_REJECTED");
            return new MutationOutcome(null, true);
        }
        if (!now.isBefore(request.firstCountedStartAt())) {
            throw new LeaveException("Only pending leave before its first counted start can be edited");
        }
        requireEligibleIntern(window, command.startDate(), command.endDate());
        List<AllocatedDate> allocations = allocations(window, command.startDate(), command.endDate());
        if (allocations.isEmpty()) {
            throw new LeaveException("Leave must contain at least one eligible workday");
        }
        Instant firstStart = allocations.getFirst().startAt();
        if (!now.isBefore(firstStart)) {
            throw new LeaveException("Leave cannot be edited after its first counted start");
        }
        if (!requests.findOverlaps(
                actor.userId(), command.startDate(), command.endDate(), RESERVED, request.id()).isEmpty()) {
            throw new LeaveException("Leave overlaps an existing active request");
        }
        validateQuota(actor.userId(), allocations, request.id());
        days.deleteByRequestId(request.id());
        request.edit(command.startDate(), command.endDate(), command.reason(), firstStart);
        requests.saveAndFlush(request);
        persistAllocations(request, allocations);
        return new MutationOutcome(view(request), false);
    }

    /**
     * Cancels a pending or approved request before its first counted start.
     *
     * <p>Authorization, expiry, and cancellation share one independent {@code REQUIRES_NEW} transaction and row
     * lock. The independent boundary does not join an ambient caller transaction, so a late pending request returns
     * an internal sentinel only after automatic rejection commits; guessed IDs are authorized before expiry.</p>
     *
     * @param actor authenticated Intern owner
     * @param requestId request identifier
     * @return cancelled request projection retaining its allocations
     */
    public LeaveRequestView cancel(AttendanceActor actor, long requestId) {
        requireIntern(actor);
        MutationOutcome outcome = independentTransactions().execute(status -> cancelInTransaction(actor, requestId));
        if (outcome.expired()) {
            throw new LeaveException("Leave cannot be cancelled after its first counted start");
        }
        return outcome.view();
    }

    private MutationOutcome cancelInTransaction(AttendanceActor actor, long requestId) {
        long ownerId = requests.findInternUserIdById(requestId)
                .orElseThrow(() -> new LeaveException("Leave request not found"));
        lockAccounts(List.of(actor.userId(), ownerId));
        AccountIdentity ownerIdentity = accounts.requireIdentityById(ownerId);
        LeaveRequestEntity request = lockedRequest(requestId);
        requireOwner(request, actor.userId());
        Instant now = clock.instant();
        if (request.status() == LeaveStatus.PENDING && !now.isBefore(request.firstCountedStartAt())) {
            request.autoReject(now);
            requests.saveAndFlush(request);
            publishDecisionNotification(ownerIdentity, "AUTO_REJECTED");
            return new MutationOutcome(null, true);
        }
        if (!now.isBefore(request.firstCountedStartAt())) {
            throw new LeaveException("Leave cannot be cancelled after its first counted start");
        }
        request.cancel(now);
        return new MutationOutcome(view(requests.saveAndFlush(request)), false);
    }

    /**
     * Approves a pending request for any active Mentor before the same immutable boundary.
     *
     * <p>Active-Mentor authorization, request-time expiry, and approval share one independent {@code REQUIRES_NEW}
     * transaction and one target-row lock. The independent boundary does not join an ambient caller transaction; an
     * expired request returns an internal sentinel only after automatic rejection commits, then the public method
     * reports the closed decision window.</p>
     *
     * @param actor authenticated active Mentor
     * @param requestId request identifier
     * @return approved request projection
     */
    public LeaveRequestView approve(AttendanceActor actor, long requestId) {
        requireMentor(actor);
        DecisionOutcome outcome = independentTransactions().execute(status -> approveInTransaction(actor, requestId));
        if (outcome.expired()) {
            throw new LeaveException("Leave is no longer pending before its first counted start");
        }
        return outcome.view();
    }

    /**
     * Rejects a pending request for any active Mentor before its first counted start.
     *
     * <p>Active-Mentor authorization, request-time expiry, and rejection share one independent {@code REQUIRES_NEW}
     * transaction and one target-row lock. The independent boundary does not join an ambient caller transaction; an
     * expired request returns an internal sentinel only after automatic rejection commits, then the public method
     * reports the closed decision window.</p>
     *
     * @param actor authenticated active Mentor
     * @param requestId request identifier
     * @return rejected request projection
     */
    public LeaveRequestView reject(AttendanceActor actor, long requestId) {
        requireMentor(actor);
        DecisionOutcome outcome = independentTransactions().execute(status -> rejectInTransaction(actor, requestId));
        if (outcome.expired()) {
            throw new LeaveException("Leave is no longer pending before its first counted start");
        }
        return outcome.view();
    }

    /**
     * Applies the same request-time guard used by the scheduler to one authorized request view.
     *
     * @param actor owning Intern, active Mentor, or Admin reader
     * @param requestId request identifier
     * @return current request view
     */
    @Transactional
    public LeaveRequestView view(AttendanceActor actor, long requestId) {
        if (actor == null) {
            throw new AccessDeniedException("An attendance actor is required");
        }
        long ownerId = requests.findInternUserIdById(requestId)
                .orElseThrow(() -> new LeaveException("Leave request not found"));
        Map<Long, LockedAccountMutationEligibility> lockedAccounts = lockAccounts(
                accountIds(actor.userId(), ownerId));
        AccountIdentity ownerIdentity = accounts.requireIdentityById(ownerId);
        LeaveRequestEntity request = lockedRequest(requestId);
        requireReader(actor, request, lockedAccounts);
        expireIfNeeded(request, clock.instant(), ownerIdentity);
        return view(request);
    }

    /**
     * Processes a bounded set of pending requests at their first counted start.
     *
     * @param batchSize maximum requests transitioned in this transaction
     * @return count of newly auto-rejected requests
     */
    @Transactional
    public int expirePending(int batchSize) {
        if (batchSize <= 0) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        Instant selectionTime = clock.instant();
        List<LeaveRequestRepository.ExpiredRecipientRoute> expired = requests
                .findExpiredRecipientRoutes(LeaveStatus.PENDING.name(), selectionTime, PageRequest.of(0, batchSize));
        List<Long> ownerIds = expired.stream()
                .map(LeaveRequestRepository.ExpiredRecipientRoute::getInternUserId)
                .distinct()
                .sorted()
                .toList();
        lockAccounts(ownerIds);
        Map<Long, AccountIdentity> ownerIdentities = identities(ownerIds);
        int changed = 0;
        for (LeaveRequestRepository.ExpiredRecipientRoute candidate : expired) {
            LeaveRequestEntity request = lockedRequest(candidate.getRequestId());
            Instant now = clock.instant();
            if (request.status() == LeaveStatus.PENDING && !now.isBefore(request.firstCountedStartAt())) {
                request.autoReject(now);
                requests.saveAndFlush(request);
                publishDecisionNotification(ownerIdentities.get(request.internUserId()), "AUTO_REJECTED");
                changed++;
            }
        }
        return changed;
    }

    private List<AllocatedDate> allocations(InternWorkWindow window, LocalDate start, LocalDate end) {
        if (!window.eligibleOn(start) || !window.eligibleOn(end)) {
            throw new LeaveException("Leave range must lie within the Intern's internship interval");
        }
        AttendancePolicyTimeline timeline = timeline();
        List<AllocatedDate> result = new ArrayList<>();
        LocalDate date = start;
        while (!date.isAfter(end)) {
            AttendancePolicy policy = timeline.resolve(date);
            if (policy.isWorkday(date)
                    && !calendar.isGlobalDayOff(date)
                    && window.eligibleOn(date)) {
                result.add(new AllocatedDate(
                        date,
                        date.withDayOfMonth(1),
                        policy,
                        ZonedDateTime.of(date, policy.scheduledStart(), policy.zoneId()).toInstant()));
            }
            date = date.plusDays(1);
        }
        return result;
    }

    private InternWorkWindow lockEligibleIntern(long internId, LocalDate start, LocalDate end) {
        InternWorkWindow window = lockIntern(internId, start);
        requireEligibleIntern(window, start, end);
        return window;
    }

    private InternWorkWindow lockIntern(long internId, LocalDate requestedStart) {
        final InternWorkWindow window;
        try {
            window = accounts.lockedInternWorkWindow(internId, requestedStart);
        } catch (IllegalArgumentException exception) {
            throw new LeaveException("Leave requires an active Intern within the internship interval", exception);
        }
        return window;
    }

    private void requireEligibleIntern(InternWorkWindow window, LocalDate start, LocalDate end) {
        if (!window.eligibleOn(start) || !window.eligibleOn(end)) {
            throw new LeaveException("Leave requires an active Intern within the internship interval");
        }
    }

    private void validateQuota(long internId, List<AllocatedDate> allocations, Long excludeRequestId) {
        allocations.stream()
                .map(item -> item.policy().id())
                .distinct()
                .sorted()
                .forEach(policyId -> policies.findForUpdateById(policyId));
        Map<LocalDate, List<AllocatedDate>> byMonth = allocations.stream()
                .collect(Collectors.groupingBy(AllocatedDate::quotaMonth));
        byMonth.forEach((month, candidates) -> {
            int quota = candidates.getFirst().policy().monthlyLeaveQuota();
            long reserved = days.countReservedExcluding(internId, month, RESERVED, excludeRequestId);
            if (reserved + candidates.size() > quota) {
                throw new LeaveException("Monthly leave quota exceeded for " + month);
            }
        });
    }

    private void persistAllocations(LeaveRequestEntity request, List<AllocatedDate> allocations) {
        days.saveAllAndFlush(allocations.stream()
                .map(item -> new LeaveRequestDayEntity(
                        request,
                        item.date(),
                        policies.getReferenceById(item.policy().id()),
                        item.policy().monthlyLeaveQuota()))
                .toList());
    }

    private LeaveRequestView view(LeaveRequestEntity request) {
        List<LeaveAllocation> allocations = days.findByRequestIdOrderByLeaveDate(request.id()).stream()
                .map(day -> new LeaveAllocation(
                        day.leaveDate(), day.quotaMonth(), day.policyVersionId(), day.monthlyQuotaSnapshot()))
                .toList();
        return new LeaveRequestView(
                request.id(),
                request.internUserId(),
                request.startDate(),
                request.endDate(),
                request.reason(),
                request.status(),
                request.submittedAt(),
                request.firstCountedStartAt(),
                request.decidedByMentorUserId(),
                request.decidedAt(),
                request.cancelledAt(),
                allocations);
    }

    private LeaveRequestEntity lockedRequest(long requestId) {
        return requests.findForUpdateById(requestId)
                .orElseThrow(() -> new LeaveException("Leave request not found"));
    }

    private static void requireOwner(LeaveRequestEntity request, long actorId) {
        if (request.internUserId() != actorId) {
            throw new AccessDeniedException("Only the owning Intern may change leave");
        }
    }

    private void requireReader(
            AttendanceActor actor,
            LeaveRequestEntity request,
            Map<Long, LockedAccountMutationEligibility> lockedAccounts) {
        if (actor.role() == AttendanceRole.INTERN) {
            requireOwner(request, actor.userId());
            return;
        }
        if (actor.role() == AttendanceRole.MENTOR) {
            requireActiveMentor(actor.userId(), lockedAccounts);
            return;
        }
        if (actor.role() != AttendanceRole.ADMIN) {
            throw new AccessDeniedException("Leave is outside the requested scope");
        }
    }

    private void requireActiveExpiryActor(
            AttendanceActor actor, Map<Long, LockedAccountMutationEligibility> lockedAccounts) {
        switch (actor.role()) {
            case INTERN -> requireActiveIntern(actor.userId(), lockedAccounts);
            case MENTOR -> requireActiveMentor(actor.userId(), lockedAccounts);
            case ADMIN -> requireActiveAdmin(actor.userId(), lockedAccounts);
            default -> throw new AccessDeniedException("Leave is outside the requested scope");
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

    private static void requireActiveAdmin(
            long userId, Map<Long, LockedAccountMutationEligibility> lockedAccounts) {
        LockedAccountMutationEligibility locked = lockedAccounts.get(userId);
        if (locked == null
                || locked.role() != GlobalRole.ADMIN
                || locked.accountStatus() != AccountStatus.ACTIVE) {
            throw new AccessDeniedException("An active Admin is required");
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
        if (actor == null || actor.role() != AttendanceRole.INTERN) {
            throw new AccessDeniedException("Only Interns may submit or cancel leave");
        }
    }

    private static void requireMentor(AttendanceActor actor) {
        if (actor == null || actor.role() != AttendanceRole.MENTOR) {
            throw new AccessDeniedException("Only Mentors may decide leave");
        }
    }

    private void expireIfNeeded(LeaveRequestEntity request, Instant now, AccountIdentity ownerIdentity) {
        if (request.status() == LeaveStatus.PENDING && !now.isBefore(request.firstCountedStartAt())) {
            request.autoReject(now);
            requests.saveAndFlush(request);
            publishDecisionNotification(ownerIdentity, "AUTO_REJECTED");
        }
    }

    private void publishSubmissionNotification(List<NotificationRecipient> recipients) {
        notifications.publish(
                new NotificationEvent(
                        NotificationType.LEAVE_SUBMITTED,
                        "SUBMITTED",
                        "Leave submitted",
                        "A leave request is awaiting Mentor review."),
                new NotificationAction("/attendance", false),
                recipients);
    }

    private void publishDecisionNotification(AccountIdentity identity, String transition) {
        notifications.publish(
                new NotificationEvent(
                        NotificationType.LEAVE_DECIDED,
                        transition,
                        "Leave decision",
                        "Your leave request has a new decision."),
                new NotificationAction("/attendance", false),
                List.of(new NotificationRecipient(identity.id(), identity.email())));
    }

    private Map<Long, LockedAccountMutationEligibility> lockAccounts(Collection<Long> accountIds) {
        try {
            return accounts.lockedAccountMutationEligibility(accountIds).stream()
                    .collect(Collectors.toMap(LockedAccountMutationEligibility::userId, eligibility -> eligibility));
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

    private static List<NotificationRecipient> activeMentorRecipients(
            List<AccountIdentity> candidates,
            Map<Long, LockedAccountMutationEligibility> lockedAccounts) {
        return candidates.stream()
                .filter(identity -> {
                    LockedAccountMutationEligibility locked = lockedAccounts.get(identity.id());
                    return locked != null
                            && locked.role() == GlobalRole.MENTOR
                            && locked.accountStatus() == AccountStatus.ACTIVE;
                })
                .map(identity -> new NotificationRecipient(identity.id(), identity.email()))
                .toList();
    }

    private DecisionOutcome approveInTransaction(AttendanceActor actor, long requestId) {
        long ownerId = requests.findInternUserIdById(requestId)
                .orElseThrow(() -> new LeaveException("Leave request not found"));
        Map<Long, LockedAccountMutationEligibility> lockedAccounts = lockAccounts(
                List.of(actor.userId(), ownerId));
        AccountIdentity ownerIdentity = accounts.requireIdentityById(ownerId);
        requireActiveMentor(actor.userId(), lockedAccounts);
        LeaveRequestEntity request = lockedRequest(requestId);
        Instant now = clock.instant();
        if (request.status() == LeaveStatus.PENDING && !now.isBefore(request.firstCountedStartAt())) {
            request.autoReject(now);
            requests.saveAndFlush(request);
            publishDecisionNotification(ownerIdentity, "AUTO_REJECTED");
            return new DecisionOutcome(null, true);
        }
        if (request.status() != LeaveStatus.PENDING || !now.isBefore(request.firstCountedStartAt())) {
            throw new LeaveException("Leave is no longer pending before its first counted start");
        }
        try {
            request.approve(actor.userId(), now);
            requests.saveAndFlush(request);
            publishDecisionNotification(ownerIdentity, "APPROVED");
            return new DecisionOutcome(view(request), false);
        } catch (ObjectOptimisticLockingFailureException conflict) {
            throw new LeaveException("Leave changed concurrently; reload before deciding", conflict);
        }
    }

    private DecisionOutcome rejectInTransaction(AttendanceActor actor, long requestId) {
        long ownerId = requests.findInternUserIdById(requestId)
                .orElseThrow(() -> new LeaveException("Leave request not found"));
        Map<Long, LockedAccountMutationEligibility> lockedAccounts = lockAccounts(
                List.of(actor.userId(), ownerId));
        AccountIdentity ownerIdentity = accounts.requireIdentityById(ownerId);
        requireActiveMentor(actor.userId(), lockedAccounts);
        LeaveRequestEntity request = lockedRequest(requestId);
        Instant now = clock.instant();
        if (request.status() == LeaveStatus.PENDING && !now.isBefore(request.firstCountedStartAt())) {
            request.autoReject(now);
            requests.saveAndFlush(request);
            publishDecisionNotification(ownerIdentity, "AUTO_REJECTED");
            return new DecisionOutcome(null, true);
        }
        if (request.status() != LeaveStatus.PENDING || !now.isBefore(request.firstCountedStartAt())) {
            throw new LeaveException("Leave is no longer pending before its first counted start");
        }
        request.reject(actor.userId(), now);
        requests.saveAndFlush(request);
        publishDecisionNotification(ownerIdentity, "REJECTED");
        return new DecisionOutcome(view(request), false);
    }

    private TransactionTemplate independentTransactions() {
        TransactionTemplate independent = new TransactionTemplate(transactions.getTransactionManager());
        independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return independent;
    }

    private AttendancePolicyTimeline timeline() {
        return new AttendancePolicyTimeline(policies.findAllByOrderByEffectiveFromAsc().stream()
                .map(AttendancePolicyEntity::toDomain)
                .toList());
    }

    private record AllocatedDate(
            LocalDate date, LocalDate quotaMonth, AttendancePolicy policy, Instant startAt) {}

    private record MutationOutcome(LeaveRequestView view, boolean expired) {}

    private record DecisionOutcome(LeaveRequestView view, boolean expired) {}
}
