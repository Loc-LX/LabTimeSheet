package com.lab.labtimesheet.feature.attendance.service;

import com.lab.labtimesheet.feature.calendar.service.CalendarApplicationService;
import com.lab.labtimesheet.feature.calendar.service.AttendancePolicyTimeline;

import com.lab.labtimesheet.feature.identity.model.AccountStatus;
import com.lab.labtimesheet.feature.internship.model.InternshipStatus;
import com.lab.labtimesheet.feature.identity.model.dto.AccountIdentity;
import com.lab.labtimesheet.feature.internship.model.dto.InternWorkWindow;
import com.lab.labtimesheet.feature.internship.model.dto.LockedAccountMutationEligibility;
import com.lab.labtimesheet.feature.identity.service.AccountService;
import com.lab.labtimesheet.feature.internship.service.InternshipService;
import com.lab.labtimesheet.feature.attendance.exception.LeaveException;
import com.lab.labtimesheet.feature.attendance.exception.AttendanceRecordNotFoundException;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.calendar.model.AttendancePolicy;
import com.lab.labtimesheet.platform.model.GlobalRole;
import com.lab.labtimesheet.platform.authorization.AuthorizationCapability;
import com.lab.labtimesheet.platform.authorization.AuthorizationPolicy;
import com.lab.labtimesheet.platform.authorization.AuthorizationRequest;
import com.lab.labtimesheet.feature.attendance.model.LeaveStatus;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveAllocation;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveBalance;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveRequestCommand;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveRequestSummary;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveRequestView;
import com.lab.labtimesheet.feature.attendance.model.entity.LeaveRequestDayEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.LeaveRequestDecisionEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.LeaveRequestEntity;
import com.lab.labtimesheet.feature.attendance.repository.LeaveRequestDayRepository;
import com.lab.labtimesheet.feature.attendance.repository.LeaveRequestDecisionRepository;
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
 * Pending, overdue, and approved rows reserve quota; rejected, withdrawn, and cancelled rows retain history but release it logically.
 * Submission and decision notifications are published in the same transaction; cancellation deliberately remains
 * silent under the notification requirements.
 */
@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class LeaveApplicationService {

    private static final List<String> RESERVED = List.of(
            LeaveStatus.PENDING.name(), LeaveStatus.OVERDUE.name(), LeaveStatus.APPROVED.name());

    private final Clock clock;
    private final LeaveRequestRepository requests;
    private final LeaveRequestDayRepository days;
    private final LeaveRequestDecisionRepository decisions;
    private final AccountService accounts;
    private final InternshipService internships;
    private final CalendarApplicationService calendar;
    private final TransactionTemplate transactions;
    private final NotificationService notifications;
    private final AuthorizationPolicy authorizationPolicy;
    private final AttendanceExceptionNotificationRecipients recipients;

    /**
     * Lists retained leave requests visible to the authenticated Attendance actor.
     *
     * <p>Interns receive only their own rows. Active global Mentors and Admins receive the
     * decision/read-only queue respectively. Pending rows whose first counted start has
     * arrived are locked and marked overdue before the actionable queue is built, so a late
     * scheduler cannot leave stale decision affordances on this first-access read path.</p>
     *
     * @param actor authenticated Attendance actor
     * @return actionable pending/overdue summaries first, followed by retained history in newest-first order
     */
    @Transactional
    public List<LeaveRequestSummary> list(AttendanceActor actor) {
        AccountIdentity identity = requireListActor(actor);
        AuthorizationRequest policyRequest = AttendanceAuthorizationRequests.request(
                actor, identity.status() == AccountStatus.ACTIVE, actor.userId(), identity.status().name());
        AttendanceAuthorizationRequests.requireAllowed(
                authorizationPolicy, AuthorizationCapability.VIEW_INTERN_ATTENDANCE, policyRequest);
        List<LeaveRequestEntity> visible = actor.role() == GlobalRole.INTERN
                ? requests.findByInternUserIdOrderBySubmittedAtDescIdDesc(actor.userId())
                : requests.findAllByOrderBySubmittedAtDescIdDesc();
        expireVisiblePending(actor, visible);
        return visible.stream()
                .map(request -> new LeaveRequestSummary(
                        request.id(), request.internUserId(), request.startDate(), request.endDate(),
                        request.reason(), request.status(), request.submittedAt()))
                .sorted(Comparator.comparing(
                                (LeaveRequestSummary row) -> row.status() != LeaveStatus.PENDING
                                        && row.status() != LeaveStatus.OVERDUE)
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
        for (LeaveRequestEntity candidate : due) {
            LeaveRequestEntity request = lockedRequest(candidate.id());
            requireReader(actor, request, lockedAccounts);
            expireIfNeeded(request, clock.instant());
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
        if (actor == null) {
            throw new AccessDeniedException("An attendance actor is required");
        }
        AccountIdentity identity = accounts.requireIdentityById(actor.userId());
        if (identity.role() != GlobalRole.INTERN || identity.role() != actor.role()) {
            throw new AccessDeniedException("Leave balance target must be the authenticated Intern");
        }
        AuthorizationRequest policyRequest = AttendanceAuthorizationRequests.request(
                actor, identity.status() == AccountStatus.ACTIVE, actor.userId(), identity.status().name());
        AttendanceAuthorizationRequests.requireAllowed(
                authorizationPolicy, AuthorizationCapability.VIEW_INTERN_ATTENDANCE, policyRequest);
        if (month == null) {
            throw new IllegalArgumentException("Leave balance month is required");
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
     * Reports whether an Intern has a pending or overdue request touching a business month.
     *
     * <p>ATT-019 through ATT-021 must consult this property before finalizing a period; AC-ATT-009 for Leave is
     * proven when period finalization is implemented.</p>
     *
     * @param internUserId Intern account identifier
     * @param month business month to check
     * @return {@code true} when at least one unresolved request overlaps the month
     */
    @Transactional(readOnly = true)
    public boolean hasUnresolvedLeaveRequest(long internUserId, YearMonth month) {
        if (internUserId <= 0 || month == null) {
            throw new IllegalArgumentException("Intern and business month are required");
        }
        List<String> unresolved = List.of(LeaveStatus.PENDING.name(), LeaveStatus.OVERDUE.name());
        return requests.existsUnresolvedRequestForInternAndMonth(
                internUserId, unresolved, month.atDay(1), month.atEndOfMonth());
    }

    /**
     * Submits one full-day inclusive request and materializes eligible workdays with policy/quota snapshots.
     * The account and Intern profile are pessimistically locked through the allocation and quota writes, while the
     * requested dates are checked against the account service's inclusive lifecycle window.
     *
     * <p>The submission notification is written in this transaction for the responsible Mentor or fallback Admins. SMTP absence is
     * represented by the notification boundary and does not roll back the leave request.</p>
     *
     * @param actor authenticated Intern owner
     * @param command requested range and reason
     * @return persisted request and frozen allocations
     */
    @Transactional
    public LeaveRequestView submit(AttendanceActor actor, LeaveRequestCommand command) {
        if (actor == null) {
            throw new AccessDeniedException("An attendance actor is required");
        }
        Objects.requireNonNull(command, "command");
        AccountIdentity actorIdentity = accounts.requireIdentityById(actor.userId());
        AuthorizationRequest policyRequest = AttendanceAuthorizationRequests.request(
                actor,
                actorIdentity.status() == AccountStatus.ACTIVE && actorIdentity.role() == actor.role(),
                actor.userId(),
                actorIdentity.status().name());
        if (actor.role() != GlobalRole.INTERN) {
            AttendanceAuthorizationRequests.requireAllowed(
                    authorizationPolicy, AuthorizationCapability.SUBMIT_ATTENDANCE_REQUEST, policyRequest);
        }
        lockAccounts(List.of(actor.userId()));
        InternWorkWindow window = lockEligibleIntern(
                actor.userId(), command.startDate(), command.endDate());
        if (actor.role() == GlobalRole.INTERN) {
            AttendanceAuthorizationRequests.requireAllowed(
                    authorizationPolicy, AuthorizationCapability.SUBMIT_ATTENDANCE_REQUEST, policyRequest);
        }
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
            publishSubmissionNotification(recipients.forIntern(actor.userId()));
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
                || actor.role() != GlobalRole.INTERN
                && actor.role() != GlobalRole.MENTOR
                && actor.role() != GlobalRole.ADMIN) {
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
        if (actor == null) {
            throw new AccessDeniedException("An attendance actor is required");
        }
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
                .orElseThrow(AttendanceRecordNotFoundException::new);
        Map<Long, LockedAccountMutationEligibility> lockedAccounts = lockAccounts(List.of(actor.userId(), ownerId));
        AccountIdentity ownerIdentity = accounts.requireIdentityById(ownerId);
        if (ownerIdentity.role() != GlobalRole.INTERN) {
            throw new AttendanceRecordNotFoundException();
        }
        LockedAccountMutationEligibility lockedActor = lockedAccounts.get(actor.userId());
        boolean activeActor = lockedActor != null
                && lockedActor.accountStatus() == AccountStatus.ACTIVE
                && lockedActor.role() == actor.role();
        LeaveRequestEntity request = lockedRequest(requestId);
        requireOwner(request, actor.userId());
        AuthorizationRequest policyRequest = AttendanceAuthorizationRequests.request(
                actor, activeActor, ownerId, lockedActor == null ? null : lockedActor.accountStatus().name());
        AttendanceAuthorizationRequests.requireAllowed(
                authorizationPolicy, AuthorizationCapability.SUBMIT_ATTENDANCE_REQUEST, policyRequest);
        if (request.status() != LeaveStatus.PENDING) {
            throw new LeaveException("Only pending leave before its first counted start can be edited");
        }
        Instant now = clock.instant();
        if (request.status() == LeaveStatus.PENDING && !now.isBefore(request.firstCountedStartAt())) {
            request.autoReject(now);
            requests.saveAndFlush(request);
            publishOverdueReminder(request);
            return new MutationOutcome(null, true);
        }
        if (!now.isBefore(request.firstCountedStartAt())) {
            throw new LeaveException("Only pending leave before its first counted start can be edited");
        }
        InternWorkWindow window = lockIntern(actor.userId(), command.startDate());
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
     * Cancels approved leave before its first counted start while retaining its approval history and allocations.
     *
     * <p>Authorization, expiry, and cancellation share one independent {@code REQUIRES_NEW} transaction and row
     * lock. The independent boundary does not join an ambient caller transaction, so a pending request first marked
     * overdue during access commits before the owner action returns; guessed IDs are authorized before expiry.</p>
     *
     * @param actor authenticated Intern owner
     * @param requestId request identifier
     * @return changed request projection retaining its allocations and decision history
     */
    public LeaveRequestView cancel(AttendanceActor actor, long requestId) {
        if (actor == null) {
            throw new AccessDeniedException("An attendance actor is required");
        }
        MutationOutcome outcome = independentTransactions().execute(
                status -> mutateOwnedRequest(actor, requestId, true));
        if (outcome.expired()) {
            throw new LeaveException("Only approved leave can be cancelled");
        }
        return outcome.view();
    }

    /**
     * Withdraws pending or overdue leave while retaining its date allocations.
     *
     * <p>The same owner authorization, independent transaction, and row lock used by the existing cancellation route
     * apply. An approved request has a different owner action and is refused here.</p>
     *
     * @param actor authenticated Intern owner
     * @param requestId request identifier
     * @return withdrawn request projection retaining its allocations; overdue requests remain withdrawable after their start
     */
    public LeaveRequestView withdraw(AttendanceActor actor, long requestId) {
        if (actor == null) {
            throw new AccessDeniedException("An attendance actor is required");
        }
        MutationOutcome outcome = independentTransactions().execute(
                status -> mutateOwnedRequest(actor, requestId, false));
        if (outcome.expired()) {
            throw new LeaveException("Leave cannot be withdrawn after its first counted start");
        }
        return outcome.view();
    }

    private MutationOutcome mutateOwnedRequest(
            AttendanceActor actor, long requestId, boolean cancelApprovedRequests) {
        long ownerId = requests.findInternUserIdById(requestId)
                .orElseThrow(AttendanceRecordNotFoundException::new);
        Map<Long, LockedAccountMutationEligibility> lockedAccounts = lockAccounts(List.of(actor.userId(), ownerId));
        AccountIdentity ownerIdentity = accounts.requireIdentityById(ownerId);
        if (ownerIdentity.role() != GlobalRole.INTERN) {
            throw new AttendanceRecordNotFoundException();
        }
        LockedAccountMutationEligibility lockedActor = lockedAccounts.get(actor.userId());
        boolean activeActor = lockedActor != null
                && lockedActor.accountStatus() == AccountStatus.ACTIVE
                && lockedActor.role() == actor.role();
        AuthorizationRequest policyRequest = AttendanceAuthorizationRequests.request(
                actor, activeActor, ownerId, lockedActor == null ? null : lockedActor.accountStatus().name());
        AttendanceAuthorizationRequests.requireAllowed(
                authorizationPolicy, AuthorizationCapability.WITHDRAW_LEAVE_REQUEST, policyRequest);
        LeaveRequestEntity request = lockedRequest(requestId);
        Instant now = clock.instant();
        if (request.status() == LeaveStatus.PENDING && !now.isBefore(request.firstCountedStartAt())) {
            request.autoReject(now);
            requests.saveAndFlush(request);
            publishOverdueReminder(request);
            if (cancelApprovedRequests) {
                return new MutationOutcome(view(request), true);
            }
        }
        if (cancelApprovedRequests) {
            if (request.status() == LeaveStatus.APPROVED && !now.isBefore(request.firstCountedStartAt())) {
                throw new LeaveException("Leave cannot be cancelled after its first counted start");
            }
            if (request.status() == LeaveStatus.APPROVED) {
                request.cancel(now);
            } else {
                throw new LeaveException("Only approved leave can be cancelled");
            }
        } else {
            if (request.status() == LeaveStatus.PENDING || request.status() == LeaveStatus.OVERDUE) {
                request.withdraw(now);
            } else {
                throw new LeaveException("Only pending or overdue leave can be withdrawn");
            }
        }
        return new MutationOutcome(view(requests.saveAndFlush(request)), false);
    }

    /**
     * Approves a pending request before, or an overdue request after, its immutable first-counted-start boundary.
     *
     * <p>Responsible-Mentor authorization, request-time overdue transition, and approval share one independent
     * {@code REQUIRES_NEW} transaction and one target-row lock. A request that becomes overdue during this access
     * receives its one overdue reminder before the decision is applied.</p>
     *
     * @param actor authenticated responsible Mentor
     * @param requestId request identifier
     * @return approved request projection
     */
    public LeaveRequestView approve(AttendanceActor actor, long requestId) {
        if (actor == null) {
            throw new AccessDeniedException("An attendance actor is required");
        }
        return independentTransactions().execute(status -> approveInTransaction(actor, requestId));
    }

    /**
     * Rejects a pending request before, or an overdue request after, its first counted start.
     *
     * <p>Responsible-Mentor authorization, request-time overdue transition, and rejection share one independent
     * {@code REQUIRES_NEW} transaction and one target-row lock. A request that becomes overdue during this access
     * receives its one overdue reminder before the decision is applied.</p>
     *
     * @param actor authenticated responsible Mentor
     * @param requestId request identifier
     * @return rejected request projection
     */
    public LeaveRequestView reject(AttendanceActor actor, long requestId) {
        if (actor == null) {
            throw new AccessDeniedException("An attendance actor is required");
        }
        return independentTransactions().execute(status -> rejectInTransaction(actor, requestId));
    }

    /**
     * Amends an approved leave request by withdrawing approval from one or more of its dates.
     *
     * <p>Only the intern's responsible Mentor may amend, only after leave has begun (the first counted
     * start has passed), and only before any period the request touches is finalized. The amendment
     * must not add dates, must not change frozen allocations, and must include a reason. Each
     * withdrawn day's quota is released; the request stays {@code APPROVED}. The amendment is
     * appended to the leave decision history.</p>
     *
     * @param actor authenticated responsible Mentor
     * @param requestId leave request identifier
     * @param datesToWithdraw dates whose approval the Mentor is withdrawing
     * @param reason mandatory amendment reason
     * @return updated request projection
     */
    public LeaveRequestView amend(AttendanceActor actor, long requestId, List<LocalDate> datesToWithdraw, String reason) {
        if (actor == null) {
            throw new AccessDeniedException("An attendance actor is required");
        }
        if (datesToWithdraw == null || datesToWithdraw.isEmpty()) {
            throw new LeaveException("Amendment must withdraw approval from at least one date");
        }
        if (datesToWithdraw.stream().distinct().count() != datesToWithdraw.size()) {
            throw new LeaveException("Withdrawn dates must not contain duplicates");
        }
        if (reason == null || reason.isBlank()) {
            throw new LeaveException("A reason is required to amend a leave decision");
        }
        return independentTransactions().execute(status -> amendInTransaction(actor, requestId, datesToWithdraw, reason.strip()));
    }

    private LeaveRequestView amendInTransaction(
            AttendanceActor actor, long requestId, List<LocalDate> datesToWithdraw, String reason) {
        DecisionContext context = authorizeDecision(actor, requestId);
        AccountIdentity ownerIdentity = context.owner();
        LeaveRequestEntity request = context.request();
        if (request.status() != LeaveStatus.APPROVED) {
            throw new LeaveException("Only approved leave can be amended");
        }
        Instant now = clock.instant();
        if (now.isBefore(request.firstCountedStartAt())) {
            throw new LeaveException("Leave can be amended only after it has begun");
        }
        List<LeaveRequestDayEntity> allDays = days.findByRequestIdOrderByLeaveDate(requestId);
        for (LocalDate dateToWithdraw : datesToWithdraw) {
            LeaveRequestDayEntity day = allDays.stream()
                    .filter(d -> d.leaveDate().equals(dateToWithdraw))
                    .findFirst()
                    .orElseThrow(() -> new LeaveException("An amendment can only withdraw dates of this leave"));
            if (day.approvalWithdrawnAt() != null) {
                throw new LeaveException("An amendment can only withdraw dates of this leave");
            }
            day.withdrawApproval(now);
        }
        days.saveAllAndFlush(allDays);
        decisions.saveAndFlush(new LeaveRequestDecisionEntity(
                requestId, "AMENDMENT", "APPROVED", null, actor.userId(), now, reason.strip()));
        publishDecisionNotification(ownerIdentity, "AMENDED");
        return view(request);
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
                .orElseThrow(AttendanceRecordNotFoundException::new);
        Map<Long, LockedAccountMutationEligibility> lockedAccounts = lockAccounts(
                accountIds(actor.userId(), ownerId));
        AccountIdentity ownerIdentity = accounts.requireIdentityById(ownerId);
        if (ownerIdentity.role() != GlobalRole.INTERN) {
            throw new AttendanceRecordNotFoundException();
        }
        LockedAccountMutationEligibility lockedActor = lockedAccounts.get(actor.userId());
        boolean activeActor = lockedActor != null
                && lockedActor.accountStatus() == AccountStatus.ACTIVE
                && lockedActor.role() == actor.role();
        AuthorizationRequest policyRequest = AttendanceAuthorizationRequests.request(
                actor, activeActor, ownerId, null);
        boolean viewAllowed = authorizationPolicy.allows(
                AuthorizationCapability.VIEW_INTERN_ATTENDANCE, policyRequest);
        if (!viewAllowed && activeActor && actor.role() == GlobalRole.INTERN) {
            throw new AttendanceRecordNotFoundException();
        }
        if (!viewAllowed) {
            throw new AccessDeniedException("Leave is outside the requested scope");
        }
        LeaveRequestEntity request = lockedRequest(requestId);
        expireIfNeeded(request, clock.instant());
        return view(request);
    }

    /**
     * Moves a bounded batch of undecided {@code PENDING} requests whose first counted start has passed to
     * {@code OVERDUE}, keeping their quota and sending one reminder each.
     *
     * @param batchSize maximum requests transitioned in this transaction
     * @return count of newly overdue requests
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
        int changed = 0;
        for (LeaveRequestRepository.ExpiredRecipientRoute candidate : expired) {
            LeaveRequestEntity request = lockedRequest(candidate.getRequestId());
            Instant now = clock.instant();
            if (request.status() == LeaveStatus.PENDING && !now.isBefore(request.firstCountedStartAt())) {
                request.autoReject(now);
                requests.saveAndFlush(request);
                publishOverdueReminder(request);
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
            window = internships.lockedInternWorkWindow(internId, requestedStart);
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
        calendar.lockPolicyVersions(allocations.stream()
                .map(item -> item.policy().id())
                .distinct()
                .collect(Collectors.toSet()));
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
                        item.policy().id(),
                        item.policy().monthlyLeaveQuota()))
                .toList());
    }

    private LeaveRequestView view(LeaveRequestEntity request) {
        List<LeaveAllocation> allocations = days.findByRequestIdOrderByLeaveDate(request.id()).stream()
                .map(day -> new LeaveAllocation(
                        day.leaveDate(),
                        day.quotaMonth(),
                        day.policyVersionId(),
                        day.monthlyQuotaSnapshot(),
                        day.approvalWithdrawnAt()))
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
                .orElseThrow(AttendanceRecordNotFoundException::new);
    }

    private static void requireOwner(LeaveRequestEntity request, long actorId) {
        if (request.internUserId() != actorId) {
            throw new AttendanceRecordNotFoundException();
        }
    }

    private void requireReader(
            AttendanceActor actor,
            LeaveRequestEntity request,
            Map<Long, LockedAccountMutationEligibility> lockedAccounts) {
        if (actor.role() == GlobalRole.INTERN) {
            requireOwner(request, actor.userId());
            return;
        }
        if (actor.role() == GlobalRole.MENTOR) {
            requireActiveMentor(actor.userId(), lockedAccounts);
            return;
        }
        if (actor.role() != GlobalRole.ADMIN) {
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


    private void expireIfNeeded(LeaveRequestEntity request, Instant now) {
        if (request.status() == LeaveStatus.PENDING && !now.isBefore(request.firstCountedStartAt())) {
            request.autoReject(now);
            requests.saveAndFlush(request);
            publishOverdueReminder(request);
        }
    }

    private void publishOverdueReminder(LeaveRequestEntity request) {
        notifications.publish(
                new NotificationEvent(
                        NotificationType.SYSTEM,
                        "OVERDUE",
                        "Leave request overdue",
                        "An undecided Leave request is overdue."),
                new NotificationAction("/attendance/leave/" + request.id(), false),
                recipients.forIntern(request.internUserId()));
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
            return internships.lockedAccountMutationEligibility(accountIds).stream()
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

    private record DecisionContext(AccountIdentity owner, LeaveRequestEntity request) {}

    private DecisionContext authorizeDecision(AttendanceActor actor, long requestId) {
        long ownerId = requests.findInternUserIdById(requestId)
                .orElseThrow(AttendanceRecordNotFoundException::new);
        Map<Long, LockedAccountMutationEligibility> lockedAccounts = lockAccounts(
                accountIds(actor.userId(), ownerId));
        AccountIdentity ownerIdentity = accounts.requireIdentityById(ownerId);
        if (ownerIdentity.role() != GlobalRole.INTERN) {
            throw new AttendanceRecordNotFoundException();
        }
        LockedAccountMutationEligibility lockedActor = lockedAccounts.get(actor.userId());
        boolean activeActor = lockedActor != null
                && lockedActor.accountStatus() == AccountStatus.ACTIVE
                && lockedActor.role() == actor.role();
        boolean responsible = activeActor && actor.role() == GlobalRole.MENTOR
                && internships.responsibleMentorUserId(ownerId)
                        .map(mentorId -> mentorId == actor.userId()).orElse(false);
        AuthorizationRequest policyRequest = AttendanceAuthorizationRequests.decisionRequest(
                actor, activeActor, responsible);
        AttendanceAuthorizationRequests.requireAllowed(
                authorizationPolicy, AuthorizationCapability.DECIDE_ATTENDANCE_REQUEST, policyRequest);
        LeaveRequestEntity request = lockedRequest(requestId);
        return new DecisionContext(ownerIdentity, request);
    }

    private LeaveRequestView approveInTransaction(AttendanceActor actor, long requestId) {
        DecisionContext context = authorizeDecision(actor, requestId);
        LeaveRequestEntity request = context.request();
        AccountIdentity ownerIdentity = context.owner();
        Instant now = clock.instant();
        if (request.status() == LeaveStatus.PENDING && !now.isBefore(request.firstCountedStartAt())) {
            request.autoReject(now);
            requests.saveAndFlush(request);
            publishOverdueReminder(request);
        }
        if (request.status() != LeaveStatus.PENDING && request.status() != LeaveStatus.OVERDUE) {
            throw new LeaveException("Leave is no longer pending before its first counted start");
        }
        try {
            request.approve(actor.userId(), now);
            requests.saveAndFlush(request);
            decisions.saveAndFlush(new LeaveRequestDecisionEntity(
                    requestId, "DECISION", "APPROVED", null, actor.userId(), now, null));
            publishDecisionNotification(ownerIdentity, "APPROVED");
            return view(request);
        } catch (ObjectOptimisticLockingFailureException conflict) {
            throw new LeaveException("Leave changed concurrently; reload before deciding", conflict);
        }
    }

    private LeaveRequestView rejectInTransaction(AttendanceActor actor, long requestId) {
        DecisionContext context = authorizeDecision(actor, requestId);
        LeaveRequestEntity request = context.request();
        AccountIdentity ownerIdentity = context.owner();
        Instant now = clock.instant();
        if (request.status() == LeaveStatus.PENDING && !now.isBefore(request.firstCountedStartAt())) {
            request.autoReject(now);
            requests.saveAndFlush(request);
            publishOverdueReminder(request);
        }
        if (request.status() != LeaveStatus.PENDING && request.status() != LeaveStatus.OVERDUE) {
            throw new LeaveException("Leave is no longer pending before its first counted start");
        }
        request.reject(actor.userId(), now);
        requests.saveAndFlush(request);
        decisions.saveAndFlush(new LeaveRequestDecisionEntity(
                requestId, "DECISION", "REJECTED", null, actor.userId(), now, null));
        publishDecisionNotification(ownerIdentity, "REJECTED");
        return view(request);
    }

    private TransactionTemplate independentTransactions() {
        TransactionTemplate independent = new TransactionTemplate(transactions.getTransactionManager());
        independent.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return independent;
    }

    private AttendancePolicyTimeline timeline() {
        return calendar.policyTimeline();
    }

    private record AllocatedDate(
            LocalDate date, LocalDate quotaMonth, AttendancePolicy policy, Instant startAt) {}

    private record MutationOutcome(LeaveRequestView view, boolean expired) {}
}
