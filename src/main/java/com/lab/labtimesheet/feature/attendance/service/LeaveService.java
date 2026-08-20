package com.lab.labtimesheet.feature.attendance.service;

import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.attendance.exception.LeaveException;
import com.lab.labtimesheet.feature.attendance.exception.LeaveRejection;
import com.lab.labtimesheet.feature.attendance.model.AttendancePolicy;
import com.lab.labtimesheet.feature.attendance.model.dto.CountedLeaveDay;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveDecisionCommand;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveDecisionRow;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveOverview;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveSubmission;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveSubmissionCommand;
import com.lab.labtimesheet.feature.attendance.model.dto.MonthReservation;
import com.lab.labtimesheet.feature.attendance.model.dto.PriorLeaveRequest;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendancePolicyEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.LeaveRequestDayEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.LeaveRequestEntity;
import com.lab.labtimesheet.feature.attendance.repository.AttendancePolicyRepository;
import com.lab.labtimesheet.feature.attendance.repository.LeaveRequestDayRepository;
import com.lab.labtimesheet.feature.attendance.repository.LeaveRequestRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional Attendance boundary for full-day leave: eligible-workday materialization, monthly/cross-month
 * quota reservation, the same-day boundary, overlap protection, Mentor decisions, Intern cancel/edit, and the
 * Intern/Mentor read paths. Intern eligibility and the serializing profile lock are obtained only through
 * {@link AccountService}; the lock precedes any reservation or overlap read (DB-008).
 */
@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class LeaveService {

    private final Clock clock;
    private final AccountService accounts;
    private final AttendancePolicyRepository policies;
    private final CalendarApplicationService calendar;
    private final LeaveRequestRepository requests;
    private final LeaveRequestDayRepository days;

    /**
     * Submits a full-day inclusive leave range in one transaction: freeze eligible workdays with their policy
     * version, calendar month, and quota snapshot, then reserve quota across every affected month. The Intern
     * profile row is locked before reservations and overlap are read to serialize concurrent overbooking (DB-008).
     * A range must not intersect an existing pending/approved request (LEV-006) and its first counted workday must
     * not have started (LEV-009).
     *
     * @param internId owning Intern account identifier
     * @param command validated form values
     * @return persisted pending submission with its materialized days
     */
    @Transactional
    public LeaveSubmission submit(long internId, LeaveSubmissionCommand command) {
        LocalDate startDate = requireStartDate(command);
        LocalDate endDate = command.endDate();
        String reason = requireReason(command.reason());
        if (startDate.isAfter(endDate)) {
            throw new LeaveException(LeaveRejection.INVALID_REQUEST);
        }
        if (!accounts.isEligibleIntern(internId)) {
            throw new LeaveException(LeaveRejection.INACTIVE_INTERN);
        }
        AccountService.InternshipWindow window = accounts.lockActiveInternship(internId);
        requireNoOverlap(internId, startDate, endDate, 0L);

        List<CountedDay> counted = countEligible(startDate, endDate, window);
        if (counted.isEmpty()) {
            throw new LeaveException(LeaveRejection.NO_COUNTED_DAYS);
        }
        Instant now = clock.instant();
        Instant firstCountedStartAt = firstCountedStartAt(counted);
        requireBeforeBoundary(firstCountedStartAt, now);
        requireQuotaAvailable(internId, counted);

        LeaveRequestEntity request = requests.save(LeaveRequestEntity.pending(
                internId, startDate, endDate, reason, now, firstCountedStartAt));
        for (CountedDay day : counted) {
            days.save(new LeaveRequestDayEntity(
                    request,
                    day.date(),
                    policies.getReferenceById(day.policy().id()),
                    day.policy().monthlyLeaveQuota()));
        }
        List<CountedLeaveDay> countedDays = counted.stream()
                .map(day -> new CountedLeaveDay(
                        day.date(), day.date().withDayOfMonth(1), day.policy().monthlyLeaveQuota()))
                .toList();
        return new LeaveSubmission(request.id(), "PENDING", countedDays);
    }

    /**
     * Approves or rejects a pending request before its first counted start. Only an active Mentor may decide; the
     * decision is recorded with the deciding Mentor, the server instant, and an optional note.
     *
     * @param mentorId deciding active Mentor account identifier
     * @param requestId request identifier
     * @param command approved/rejected plus optional decision note
     * @return the decided submission
     */
    @Transactional
    public LeaveSubmission decide(long mentorId, long requestId, LeaveDecisionCommand command) {
        requireActiveMentor(mentorId);
        Instant now = clock.instant();
        LeaveRequestEntity request = requireRequest(requestId);
        requireStatus(request, "PENDING");
        requireBeforeBoundary(request.firstCountedStartAt(), now);
        if (command.approved()) {
            request.approve(mentorId, now, command.decisionNote());
        } else {
            request.reject(mentorId, now, command.decisionNote());
        }
        saveAndFlushChecked(request);
        return submission(request);
    }

    /**
     * Cancels the Intern's pending or approved request before its first counted start, releasing its reservation.
     *
     * @param internId owning Intern account identifier
     * @param requestId request identifier
     * @return the cancelled submission
     */
    @Transactional
    public LeaveSubmission cancel(long internId, long requestId) {
        Instant now = clock.instant();
        LeaveRequestEntity request = requireRequest(requestId);
        requireOwner(request, internId);
        requireStatus(request, "PENDING", "APPROVED");
        requireBeforeBoundary(request.firstCountedStartAt(), now);
        request.cancel(now);
        saveAndFlushChecked(request);
        return submission(request);
    }

    /**
     * Replaces a pending request's range before its first counted start: the replacement is recomputed and checked
     * against internship, day-off, quota, and overlap exactly like a new submission, and the old frozen days are
     * replaced only after every check passes. Any failure leaves the original request and its days intact.
     *
     * @param internId owning Intern account identifier
     * @param requestId request identifier
     * @param command replacement range and reason
     * @return the edited pending submission
     */
    @Transactional
    public LeaveSubmission edit(long internId, long requestId, LeaveSubmissionCommand command) {
        Instant now = clock.instant();
        LeaveRequestEntity request = requireRequest(requestId);
        requireOwner(request, internId);
        requireStatus(request, "PENDING");
        requireBeforeBoundary(request.firstCountedStartAt(), now);

        LocalDate startDate = requireStartDate(command);
        LocalDate endDate = command.endDate();
        String reason = requireReason(command.reason());
        if (startDate.isAfter(endDate)) {
            throw new LeaveException(LeaveRejection.INVALID_REQUEST);
        }
        AccountService.InternshipWindow window = accounts.lockActiveInternship(internId);
        List<CountedDay> counted = countEligible(startDate, endDate, window);
        if (counted.isEmpty()) {
            throw new LeaveException(LeaveRejection.NO_COUNTED_DAYS);
        }
        requireNoOverlap(internId, startDate, endDate, requestId);
        Instant firstCountedStartAt = firstCountedStartAt(counted);
        requireBeforeBoundary(firstCountedStartAt, now);
        requireQuotaAvailableExcluding(internId, counted, requestId);

        days.deleteAllByRequestId(requestId);
        request.updateRange(startDate, endDate, reason, now, firstCountedStartAt);
        saveAndFlushChecked(request);
        for (CountedDay day : counted) {
            days.save(new LeaveRequestDayEntity(
                    request,
                    day.date(),
                    policies.getReferenceById(day.policy().id()),
                    day.policy().monthlyLeaveQuota()));
        }
        return new LeaveSubmission(request.id(), request.status(), counted.stream()
                .map(day -> new CountedLeaveDay(
                        day.date(), day.date().withDayOfMonth(1), day.policy().monthlyLeaveQuota()))
                .toList());
    }

    /**
     * Renders the Intern leave form: monthly quota, reserved and available days, plus prior requests with their
     * counted days and boundary-based edit/cancel actions. Only pending and approved days reserve quota.
     *
     * @param internId owning Intern account identifier
     * @param month quota month being displayed, first day
     * @return form state
     */
    @Transactional(readOnly = true)
    public LeaveOverview overview(long internId, LocalDate month) {
        Objects.requireNonNull(month, "month");
        if (!accounts.isEligibleIntern(internId)) {
            throw new LeaveException(LeaveRejection.INACTIVE_INTERN);
        }
        int quota = timeline().resolve(month).monthlyLeaveQuota();
        long reserved = days.countReservedByMonth(internId).stream()
                .filter(item -> item.quotaMonth().equals(month))
                .mapToLong(MonthReservation::count)
                .sum();
        Instant now = clock.instant();
        List<PriorLeaveRequest> prior = requests.findByInternUserIdOrderByIdDesc(internId).stream()
                .map(request -> new PriorLeaveRequest(
                        request.id(),
                        request.startDate(),
                        request.endDate(),
                        request.status(),
                        request.reason(),
                        request.submittedAt(),
                        request.firstCountedStartAt(),
                        isEditable(request, now),
                        isCancellable(request, now),
                        days.findLeaveDatesByRequestId(request.id())))
                .toList();
        return new LeaveOverview(month, quota, (int) reserved, (int) (quota - reserved), prior);
    }

    /**
     * Lists every leave request newest-first for the Mentor decisions page, with the Intern's display name and the
     * decision boundary for each request.
     *
     * @return decision rows newest-first
     */
    @Transactional(readOnly = true)
    public List<LeaveDecisionRow> decisions() {
        return requests.findAllByOrderByIdDesc().stream()
                .map(request -> new LeaveDecisionRow(
                        request.id(),
                        request.internUserId(),
                        internDisplayName(request.internUserId()),
                        request.startDate(),
                        request.endDate(),
                        request.reason(),
                        request.status(),
                        request.firstCountedStartAt(),
                        days.findLeaveDatesByRequestId(request.id())))
                .toList();
    }

    private List<CountedDay> countEligible(
            LocalDate startDate, LocalDate endDate, AccountService.InternshipWindow window) {
        AttendancePolicyTimeline timeline = timeline();
        List<CountedDay> counted = new ArrayList<>();
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            if (date.isBefore(window.start()) || date.isAfter(window.end())) {
                continue;
            }
            AttendancePolicy policy = timeline.resolve(date);
            if (!policy.isWorkday(date)) {
                continue;
            }
            if (calendar.isGlobalDayOff(date)) {
                continue;
            }
            counted.add(new CountedDay(date, policy));
        }
        return counted;
    }

    private static Instant firstCountedStartAt(List<CountedDay> counted) {
        return counted.getFirst()
                .date()
                .atTime(counted.getFirst().policy().scheduledStart())
                .atZone(counted.getFirst().policy().zoneId())
                .toInstant();
    }

    private void requireNoOverlap(long internId, LocalDate startDate, LocalDate endDate, long excludedRequestId) {
        boolean overlaps = requests.findOverlapping(internId, startDate, endDate).stream()
                .anyMatch(existing -> existing.id() != excludedRequestId);
        if (overlaps) {
            throw new LeaveException(LeaveRejection.OVERLAPS_PENDING);
        }
    }

    private void requireQuotaAvailable(long internId, List<CountedDay> counted) {
        requireQuotaWithin(days.countReservedByMonth(internId), counted);
    }

    private void requireQuotaAvailableExcluding(long internId, List<CountedDay> counted, long excludedRequestId) {
        requireQuotaWithin(days.countReservedByMonthExcluding(internId, excludedRequestId), counted);
    }

    private static void requireQuotaWithin(List<MonthReservation> reservations, List<CountedDay> counted) {
        Map<LocalDate, Long> reserved = reservations.stream()
                .collect(Collectors.toMap(MonthReservation::quotaMonth, MonthReservation::count));
        Map<LocalDate, Integer> candidate = new HashMap<>();
        for (CountedDay day : counted) {
            int candidateForMonth = candidate.merge(day.date().withDayOfMonth(1), 1, Integer::sum);
            long reservedForMonth = reserved.getOrDefault(day.date().withDayOfMonth(1), 0L);
            if (reservedForMonth + candidateForMonth > day.policy().monthlyLeaveQuota()) {
                throw new LeaveException(LeaveRejection.QUOTA_EXCEEDED);
            }
        }
    }

    private void requireActiveMentor(long mentorId) {
        try {
            accounts.requireActiveMentorId(mentorId);
        } catch (IllegalArgumentException exception) {
            throw new LeaveException(LeaveRejection.INACTIVE_MENTOR);
        }
    }

    private LeaveRequestEntity requireRequest(long requestId) {
        return requests.findById(requestId)
                .orElseThrow(() -> new LeaveException(LeaveRejection.NOT_FOUND));
    }

    private static void requireOwner(LeaveRequestEntity request, long internId) {
        if (request.internUserId() != internId) {
            throw new LeaveException(LeaveRejection.NOT_OWNER);
        }
    }

    private static void requireStatus(LeaveRequestEntity request, String... allowed) {
        for (String status : allowed) {
            if (request.status().equals(status)) {
                return;
            }
        }
        throw new LeaveException(LeaveRejection.INVALID_STATE);
    }

    private static void requireBeforeBoundary(Instant boundary, Instant now) {
        if (!now.isBefore(boundary)) {
            throw new LeaveException(LeaveRejection.BOUNDARY_PASSED);
        }
    }

    private void saveAndFlushChecked(LeaveRequestEntity request) {
        try {
            requests.saveAndFlush(request);
        } catch (ObjectOptimisticLockingFailureException exception) {
            throw new LeaveException(LeaveRejection.INVALID_STATE);
        }
    }

    private LeaveSubmission submission(LeaveRequestEntity request) {
        List<CountedLeaveDay> counted = days.findDaysByRequestId(request.id()).stream()
                .map(day -> new CountedLeaveDay(day.leaveDate(), day.quotaMonth(), day.monthlyQuotaSnapshot()))
                .toList();
        return new LeaveSubmission(request.id(), request.status(), counted);
    }

    private String internDisplayName(long internId) {
        return accounts.requireIdentityById(internId).displayName();
    }

    private static boolean isEditable(LeaveRequestEntity request, Instant now) {
        return "PENDING".equals(request.status()) && now.isBefore(request.firstCountedStartAt());
    }

    private static boolean isCancellable(LeaveRequestEntity request, Instant now) {
        return ("PENDING".equals(request.status()) || "APPROVED".equals(request.status()))
                && now.isBefore(request.firstCountedStartAt());
    }

    private AttendancePolicyTimeline timeline() {
        return new AttendancePolicyTimeline(policies
                .findAllByOrderByEffectiveFromAsc()
                .stream()
                .map(AttendancePolicyEntity::toDomain)
                .toList());
    }

    private static LocalDate requireStartDate(LeaveSubmissionCommand command) {
        if (command == null || command.startDate() == null || command.endDate() == null) {
            throw new LeaveException(LeaveRejection.INVALID_REQUEST);
        }
        return command.startDate();
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new LeaveException(LeaveRejection.INVALID_REQUEST);
        }
        return reason.strip();
    }

    private record CountedDay(LocalDate date, AttendancePolicy policy) {
    }
}