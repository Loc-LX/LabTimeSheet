package com.lab.labtimesheet.feature.attendance.service;

import com.lab.labtimesheet.feature.calendar.service.CalendarApplicationService;
import com.lab.labtimesheet.feature.calendar.service.AttendancePolicyTimeline;

import com.lab.labtimesheet.feature.identity.model.AccountStatus;
import com.lab.labtimesheet.feature.identity.model.dto.AccountIdentity;
import com.lab.labtimesheet.feature.identity.model.dto.InternReportingWindow;
import com.lab.labtimesheet.feature.identity.service.AccountService;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.calendar.model.AttendancePolicy;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRecord;
import com.lab.labtimesheet.platform.model.GlobalRole;
import com.lab.labtimesheet.feature.attendance.model.AttendanceViolations;
import com.lab.labtimesheet.feature.attendance.model.dto.AttendanceReport;
import com.lab.labtimesheet.feature.attendance.model.dto.AttendanceReportClassification;
import com.lab.labtimesheet.feature.attendance.model.dto.AttendanceReportDay;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceRecordEntity;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceQueryRepository;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceRecordRepository;
import com.lab.labtimesheet.feature.calendar.model.dto.CalendarHistoryItem;
import com.lab.labtimesheet.platform.model.GlobalRole;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Attendance-owned read boundary for the bounded detailed report consumed by Reports/UI.
 *
 * <p>The service resolves authorization through AccountService DTOs, then reads only Attendance-owned policy,
 * calendar, leave, raw attendance, and correction rows. It never invokes the external HolidayAPI path. The
 * correction bulk guard is deliberately retained in this transaction so first report access can persist a deadline
 * rejection before deriving the effective checkout and violation flags.</p>
 */
@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class AttendanceReportQueryService {

    private static final int MAX_RANGE_DAYS = 366;
    private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);
    private static final int PERCENT_SCALE = 2;
    private static final int DAILY_SCORE_SCALE = 2;

    private final Clock clock;
    private final AccountService accounts;
    private final AttendanceRecordRepository records;
    private final AttendanceQueryRepository queries;
    private final CalendarApplicationService calendar;
    private final AttendanceCorrectionApplicationService corrections;

    /**
     * Builds one inclusive, oldest-first report for an authenticated actor and target Intern.
     *
     * <p>Intern actors are authorized from their own Account identity before any target lookup and may request only
     * themselves. Active Mentors and active Admins may inspect any target account whose immutable role is Intern; a
     * Project Leader remains an Intern at this boundary and therefore gets no cross-user access. Unavailable or
     * non-Intern targets use the same denied outcome for broad actors. The inclusive range is limited to one calendar
     * year to keep the server-side read bounded. Historical eligibility comes from the Account-owned activation and
     * terminal timestamp window, so completion does not remove previously eligible empty workdays. Percentages are
     * exact two-decimal HALF_UP values; empty metrics and their display helpers represent N/A when no expected
     * workday remains.</p>
     *
     * @param actor authenticated Attendance authorization context
     * @param internId target Intern account identifier
     * @param from inclusive first local business date
     * @param to inclusive last local business date
     * @return immutable daily rows and aggregate metrics in ascending local-date order
     * @throws AccessDeniedException when actor scope or target role is not permitted
     * @throws IllegalArgumentException when the range is malformed or exceeds the bound
     */
    @Transactional
    public AttendanceReport query(AttendanceActor actor, long internId, LocalDate from, LocalDate to) {
        authorize(actor, internId);
        validateRange(from, to);
        InternReportingWindow reportingWindow = accounts.historicalInternReportingWindow(internId).orElse(null);

        List<AttendanceRecordEntity> recordRows = records
                .findByInternUserIdAndWorkDateBetweenOrderByWorkDateAsc(internId, from, to);
        Map<Long, AttendancePolicy> policiesByVersionId = calendar.policiesByVersionIds(recordRows.stream()
                .map(AttendanceRecordEntity::policyVersionId)
                .collect(Collectors.toSet()));
        Map<LocalDate, AttendanceRecordEntity> recordsByDate = recordRows.stream()
                .collect(Collectors.toMap(AttendanceRecordEntity::workDate, row -> row));
        Map<Long, java.time.Instant> effectiveCheckouts = corrections.prepareHistory(recordRows);
        Set<LocalDate> approvedLeaveDates = new HashSet<>(queries.findApprovedLeaveDates(internId, from, to));
        Map<LocalDate, CalendarDayOff> dayOffs = calendarDayOffs(from, to);
        AttendancePolicyTimeline timeline = calendar.policyTimeline();
        java.time.Instant observedAt = clock.instant();

        List<AttendanceReportDay> days = new ArrayList<>();
        BigDecimal complianceTotal = BigDecimal.ZERO;
        int presentWorkdays = 0;
        int expectedWorkdays = 0;
        LocalDate date = from;
        while (!date.isAfter(to)) {
            AttendanceRecordEntity entity = recordsByDate.get(date);
            // A terminal lifecycle action closes future obligations but cannot erase a
            // previously eligible empty workday or a row recorded on that local date.
            if ((reportingWindow == null || !reportingWindow.eligibleOn(date)) && entity == null) {
                date = date.plusDays(1);
                continue;
            }
            AttendanceRecord record = entity == null ? null : toDomain(entity, policiesByVersionId);
            AttendancePolicy policy = record == null ? timeline.resolve(date) : record.policy();
            AttendanceReportClassification classification = classify(
                    date, policy, dayOffs.get(date), approvedLeaveDates, record);
            java.time.Instant effectiveCheckout = record == null
                    ? null
                    : effectiveCheckouts.get(entity.id());
            AttendanceViolations violations = record == null
                    ? new AttendanceViolations(false, false, false)
                    : record.violations(observedAt, effectiveCheckout);
            Optional<BigDecimal> score = dailyScore(classification, policy, violations);
            if (classification == AttendanceReportClassification.PRESENT) {
                presentWorkdays++;
                expectedWorkdays++;
            } else if (classification == AttendanceReportClassification.ABSENT) {
                expectedWorkdays++;
            }
            if (score.isPresent() && isExpected(classification)) {
                complianceTotal = complianceTotal.add(score.orElseThrow());
            }
            days.add(toDay(
                    date,
                    classification,
                    policy,
                    record,
                    effectiveCheckout,
                    violations,
                    score));
            date = date.plusDays(1);
        }

        Optional<BigDecimal> attendanceRate = expectedWorkdays == 0
                ? Optional.empty()
                : Optional.of(percentage(BigDecimal.valueOf(presentWorkdays), BigDecimal.valueOf(expectedWorkdays)));
        Optional<BigDecimal> compliance = expectedWorkdays == 0
                ? Optional.empty()
                : Optional.of(percentage(complianceTotal, BigDecimal.valueOf(expectedWorkdays)));
        return new AttendanceReport(
                internId,
                from,
                to,
                days,
                presentWorkdays,
                expectedWorkdays,
                attendanceRate,
                compliance);
    }

    private static AttendanceReportDay toDay(
            LocalDate date,
            AttendanceReportClassification classification,
            AttendancePolicy policy,
            AttendanceRecord record,
            java.time.Instant effectiveCheckout,
            AttendanceViolations violations,
            Optional<BigDecimal> score) {
        return new AttendanceReportDay(
                date,
                classification,
                policy.id(),
                policy.effectiveFrom(),
                policy.zoneId(),
                policy.scheduledStart(),
                policy.scheduledEnd(),
                policy.checkInGraceMinutes(),
                policy.checkoutGraceMinutes(),
                policy.violationPenalty(),
                record == null ? null : record.checkInAt(),
                record == null ? null : record.checkOutAt(),
                effectiveCheckout,
                violations.late(),
                violations.earlyDeparture(),
                violations.missingCheckout(),
                score);
    }

    private static AttendanceReportClassification classify(
            LocalDate date,
            AttendancePolicy policy,
            CalendarDayOff dayOff,
            Set<LocalDate> approvedLeaveDates,
            AttendanceRecord record) {
        if (dayOff != null && dayOff.dayOff()) {
            return dayOff.importedHoliday()
                    ? AttendanceReportClassification.HOLIDAY
                    : AttendanceReportClassification.OFF_DAY;
        }
        if (!policy.isWorkday(date)) {
            return AttendanceReportClassification.OFF_DAY;
        }
        if (approvedLeaveDates.contains(date)) {
            return AttendanceReportClassification.APPROVED_LEAVE;
        }
        return record == null
                ? AttendanceReportClassification.ABSENT
                : AttendanceReportClassification.PRESENT;
    }

    private static Optional<BigDecimal> dailyScore(
            AttendanceReportClassification classification,
            AttendancePolicy policy,
            AttendanceViolations violations) {
        if (classification == AttendanceReportClassification.ABSENT) {
            return Optional.of(BigDecimal.ZERO.setScale(DAILY_SCORE_SCALE, RoundingMode.HALF_UP));
        }
        if (classification != AttendanceReportClassification.PRESENT) {
            return Optional.empty();
        }
        int violationCount = (violations.late() ? 1 : 0)
                + ((violations.earlyDeparture() || violations.missingCheckout()) ? 1 : 0);
        BigDecimal score = BigDecimal.ONE.subtract(
                        policy.violationPenalty().multiply(BigDecimal.valueOf(violationCount)))
                .max(BigDecimal.ZERO);
        return Optional.of(score);
    }

    private static boolean isExpected(AttendanceReportClassification classification) {
        return classification == AttendanceReportClassification.PRESENT
                || classification == AttendanceReportClassification.ABSENT;
    }

    private Map<LocalDate, CalendarDayOff> calendarDayOffs(LocalDate from, LocalDate to) {
        Map<LocalDate, CalendarDayOff> result = new HashMap<>();
        for (CalendarHistoryItem event : calendar.historyBetween(from, to)) {
            if (!event.dayOff()) {
                continue;
            }
            result.merge(
                    event.calendarDate(),
                    new CalendarDayOff(true, "HOLIDAY_API".equals(event.source())),
                    CalendarDayOff::preferImportedHoliday);
        }
        return result;
    }

    private static AttendanceRecord toDomain(
            AttendanceRecordEntity entity, Map<Long, AttendancePolicy> policiesByVersionId) {
        AttendancePolicy policy = policiesByVersionId.get(entity.policyVersionId());
        if (policy == null) {
            throw new IllegalStateException("Attendance policy version not found");
        }
        return entity.toDomain(policy);
    }

    private void authorize(AttendanceActor actor, long internId) {
        if (actor == null) {
            throw new AccessDeniedException("An attendance actor is required");
        }
        AccountIdentity identity = accounts.requireIdentityById(actor.userId());
        GlobalRole expectedRole = GlobalRole.valueOf(actor.role().name());
        if (identity.role() != expectedRole) {
            throw new AccessDeniedException("Attendance actor role does not match the account");
        }
        if (actor.role() == GlobalRole.INTERN) {
            if (actor.userId() != internId || identity.status() != AccountStatus.ACTIVE) {
                throw new AccessDeniedException("Interns may view only their own attendance");
            }
            return;
        }
        boolean activeBroadActor = (actor.role() == GlobalRole.MENTOR && identity.role() == GlobalRole.MENTOR
                || actor.role() == GlobalRole.ADMIN && identity.role() == GlobalRole.ADMIN)
                && identity.status() == AccountStatus.ACTIVE;
        if (!activeBroadActor) {
            throw new AccessDeniedException("An active Mentor or Admin is required");
        }
        if (internId <= 0) {
            throw new AccessDeniedException("Attendance report target must be an Intern");
        }
        AccountIdentity target;
        try {
            target = accounts.requireIdentityById(internId);
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException("Attendance report target must be an Intern");
        }
        if (target.role() != GlobalRole.INTERN) {
            throw new AccessDeniedException("Attendance report target must be an Intern");
        }
    }

    private static void validateRange(LocalDate from, LocalDate to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from must not be after to");
        }
        long inclusiveDays = ChronoUnit.DAYS.between(from, to) + 1;
        if (inclusiveDays > MAX_RANGE_DAYS) {
            throw new IllegalArgumentException("Attendance report range must not exceed 366 days");
        }
    }

    private static BigDecimal percentage(BigDecimal numerator, BigDecimal denominator) {
        return numerator.multiply(ONE_HUNDRED)
                .divide(denominator, PERCENT_SCALE + 4, RoundingMode.HALF_UP)
                .setScale(PERCENT_SCALE, RoundingMode.HALF_UP);
    }

    private record CalendarDayOff(boolean dayOff, boolean importedHoliday) {

        private CalendarDayOff preferImportedHoliday(CalendarDayOff other) {
            return importedHoliday || other.importedHoliday
                    ? new CalendarDayOff(true, true)
                    : other;
        }
    }
}
