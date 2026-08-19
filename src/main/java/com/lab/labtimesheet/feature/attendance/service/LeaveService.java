package com.lab.labtimesheet.feature.attendance.service;

import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.attendance.exception.LeaveException;
import com.lab.labtimesheet.feature.attendance.exception.LeaveRejection;
import com.lab.labtimesheet.feature.attendance.model.AttendancePolicy;
import com.lab.labtimesheet.feature.attendance.model.dto.CountedLeaveDay;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional Attendance boundary for full-day leave: eligible-workday materialization, monthly/cross-month
 * quota reservation, and Intern form reads. Intern eligibility and the serializing profile lock are obtained only
 * through {@link AccountService}; the lock precedes any reservation read (DB-008).
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
     * profile row is locked before reservations are read to serialize concurrent overbooking (DB-008).
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

        AttendancePolicyTimeline timeline = timeline();
        List<CountedDay> counted = new ArrayList<>();
        LocalDate firstCountedDate = null;
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
            if (firstCountedDate == null) {
                firstCountedDate = date;
            }
            counted.add(new CountedDay(date, policy));
        }
        if (counted.isEmpty()) {
            throw new LeaveException(LeaveRejection.NO_COUNTED_DAYS);
        }
        requireQuotaAvailable(internId, counted);

        Instant now = clock.instant();
        Instant firstCountedStartAt = firstCountedDate
                .atTime(counted.getFirst().policy().scheduledStart())
                .atZone(counted.getFirst().policy().zoneId())
                .toInstant();

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
     * Renders the Intern leave form: monthly quota, reserved and available days, plus prior requests with their
     * counted days. Only pending and approved days reserve quota.
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
        List<PriorLeaveRequest> prior = requests.findByInternUserIdOrderByIdDesc(internId).stream()
                .map(request -> new PriorLeaveRequest(
                        request.id(),
                        request.startDate(),
                        request.endDate(),
                        request.status(),
                        request.reason(),
                        request.submittedAt(),
                        days.findLeaveDatesByRequestId(request.id())))
                .toList();
        return new LeaveOverview(month, quota, (int) reserved, (int) (quota - reserved), prior);
    }

    private void requireQuotaAvailable(long internId, List<CountedDay> counted) {
        Map<LocalDate, Long> reserved = days.countReservedByMonth(internId).stream()
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