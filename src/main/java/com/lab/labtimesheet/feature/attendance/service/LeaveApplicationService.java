package com.lab.labtimesheet.feature.attendance.service;

import com.lab.labtimesheet.feature.account.model.AccountStatus;
import com.lab.labtimesheet.feature.account.model.GlobalRole;
import com.lab.labtimesheet.feature.account.model.dto.InternWorkWindow;
import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.attendance.exception.LeaveException;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.AttendancePolicy;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRole;
import com.lab.labtimesheet.feature.attendance.model.LeaveStatus;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveAllocation;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveRequestCommand;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveRequestView;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendancePolicyEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.LeaveRequestDayEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.LeaveRequestEntity;
import com.lab.labtimesheet.feature.attendance.repository.AttendancePolicyRepository;
import com.lab.labtimesheet.feature.attendance.repository.LeaveRequestDayRepository;
import com.lab.labtimesheet.feature.attendance.repository.LeaveRequestRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.ArrayList;
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

    /**
     * Submits one full-day inclusive request and materializes eligible workdays with policy/quota snapshots.
     * The account and Intern profile are pessimistically locked through the allocation and quota writes, while the
     * requested dates are checked against the account service's inclusive lifecycle window.
     *
     * @param actor authenticated Intern owner
     * @param command requested range and reason
     * @return persisted request and frozen allocations
     */
    @Transactional
    public LeaveRequestView submit(AttendanceActor actor, LeaveRequestCommand command) {
        requireIntern(actor);
        Objects.requireNonNull(command, "command");
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
            return view(request);
        } catch (DataIntegrityViolationException conflict) {
            throw new LeaveException("Leave overlaps an existing active request", conflict);
        }
    }

    /**
     * Edits a pending request before its first counted start, replacing allocations atomically after revalidation.
     *
     * @param actor authenticated Intern owner
     * @param requestId request identifier
     * @param command replacement inclusive range and reason
     * @return updated request with freshly frozen allocations
     * @implNote Authorization, expiry, quota validation, and allocation replacement share one independent
     * {@code REQUIRES_NEW} transaction and row lock. The independent boundary does not join an ambient caller
     * transaction, so a late pending request returns an internal sentinel only after automatic rejection commits;
     * the post-lock server-time expiry check runs before replacement lifecycle/date validation, and the public method
     * then reports that editing is closed even when the owner has since become ineligible. For a valid replacement,
     * the Account service's account and Intern-profile locks remain held through frozen allocation and quota
     * persistence.
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
     * @param actor authenticated Intern owner
     * @param requestId request identifier
     * @return cancelled request projection retaining its allocations
     * @implNote Authorization, expiry, and cancellation share one independent {@code REQUIRES_NEW} transaction and
     * row lock. The independent boundary does not join an ambient caller transaction, so a late pending request
     * returns an internal sentinel only after automatic rejection commits; guessed IDs are authorized before expiry.
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
        LeaveRequestEntity request = lockedRequest(requestId);
        requireOwner(request, actor.userId());
        Instant now = clock.instant();
        if (request.status() == LeaveStatus.PENDING && !now.isBefore(request.firstCountedStartAt())) {
            request.autoReject(now);
            requests.saveAndFlush(request);
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
     * @param actor authenticated active Mentor
     * @param requestId request identifier
     * @return approved request projection
     * @implNote Active-Mentor authorization, request-time expiry, and approval share one independent
     * {@code REQUIRES_NEW} transaction and one target-row lock. The independent boundary does not join an ambient
     * caller transaction; an expired request returns an internal sentinel only after automatic rejection commits,
     * then the public method reports the closed decision window.
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
     * @param actor authenticated active Mentor
     * @param requestId request identifier
     * @return rejected request projection
     * @implNote Active-Mentor authorization, request-time expiry, and rejection share one independent
     * {@code REQUIRES_NEW} transaction and one target-row lock. The independent boundary does not join an ambient
     * caller transaction; an expired request returns an internal sentinel only after automatic rejection commits,
     * then the public method reports the closed decision window.
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
        LeaveRequestEntity request = lockedRequest(requestId);
        requireReader(actor, request);
        expireIfNeeded(request, clock.instant());
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
        List<LeaveRequestEntity> expired = requests
                .findByStatusAndFirstCountedStartAtLessThanEqualOrderByIdAsc(
                        LeaveStatus.PENDING.name(), selectionTime, PageRequest.of(0, batchSize));
        int changed = 0;
        for (LeaveRequestEntity candidate : expired) {
            LeaveRequestEntity request = lockedRequest(candidate.id());
            Instant now = clock.instant();
            if (request.status() == LeaveStatus.PENDING && !now.isBefore(request.firstCountedStartAt())) {
                request.autoReject(now);
                requests.saveAndFlush(request);
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

    private void requireReader(AttendanceActor actor, LeaveRequestEntity request) {
        if (actor == null) {
            throw new AccessDeniedException("An attendance actor is required");
        }
        if (actor.role() == AttendanceRole.INTERN) {
            requireOwner(request, actor.userId());
            return;
        }
        if (actor.role() == AttendanceRole.MENTOR) {
            requireActiveMentor(actor.userId());
            return;
        }
        if (actor.role() != AttendanceRole.ADMIN) {
            throw new AccessDeniedException("Leave is outside the requested scope");
        }
    }

    private void requireActiveMentor(long userId) {
        var identity = accounts.requireIdentityById(userId);
        if (identity.role() != GlobalRole.MENTOR || identity.status() != AccountStatus.ACTIVE) {
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

    private static void expireIfNeeded(LeaveRequestEntity request, Instant now) {
        if (request.status() == LeaveStatus.PENDING && !now.isBefore(request.firstCountedStartAt())) {
            request.autoReject(now);
        }
    }

    private DecisionOutcome approveInTransaction(AttendanceActor actor, long requestId) {
        requireActiveMentor(actor.userId());
        LeaveRequestEntity request = lockedRequest(requestId);
        Instant now = clock.instant();
        if (request.status() == LeaveStatus.PENDING && !now.isBefore(request.firstCountedStartAt())) {
            request.autoReject(now);
            requests.saveAndFlush(request);
            return new DecisionOutcome(null, true);
        }
        if (request.status() != LeaveStatus.PENDING || !now.isBefore(request.firstCountedStartAt())) {
            throw new LeaveException("Leave is no longer pending before its first counted start");
        }
        try {
            request.approve(actor.userId(), now);
            return new DecisionOutcome(view(requests.saveAndFlush(request)), false);
        } catch (ObjectOptimisticLockingFailureException conflict) {
            throw new LeaveException("Leave changed concurrently; reload before deciding", conflict);
        }
    }

    private DecisionOutcome rejectInTransaction(AttendanceActor actor, long requestId) {
        requireActiveMentor(actor.userId());
        LeaveRequestEntity request = lockedRequest(requestId);
        Instant now = clock.instant();
        if (request.status() == LeaveStatus.PENDING && !now.isBefore(request.firstCountedStartAt())) {
            request.autoReject(now);
            requests.saveAndFlush(request);
            return new DecisionOutcome(null, true);
        }
        if (request.status() != LeaveStatus.PENDING || !now.isBefore(request.firstCountedStartAt())) {
            throw new LeaveException("Leave is no longer pending before its first counted start");
        }
        request.reject(actor.userId(), now);
        return new DecisionOutcome(view(requests.saveAndFlush(request)), false);
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
