package com.lab.labtimesheet.feature.attendance.service;

import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.attendance.exception.AttendanceException;
import com.lab.labtimesheet.feature.attendance.exception.AttendanceRejection;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.AttendanceDayContext;
import com.lab.labtimesheet.feature.attendance.model.AttendancePolicy;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRecord;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRole;
import com.lab.labtimesheet.feature.attendance.model.dto.AttendanceCurrentState;
import com.lab.labtimesheet.feature.attendance.model.dto.AttendanceHistoryItem;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendancePolicyEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceRecordEntity;
import com.lab.labtimesheet.feature.attendance.repository.AttendancePolicyRepository;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceQueryRepository;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceRecordRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional Attendance application boundary for punches, current state, and authorized history reads.
 * Account eligibility is obtained only through {@link AccountService}; raw rows retain their attached policy.
 */
@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class AttendanceApplicationService {

    private final Clock clock;
    private final AttendancePolicyRepository policyEntities;
    private final AttendanceRecordRepository recordEntities;
    private final AttendanceQueryRepository queries;
    private final AccountService accounts;
    private final CalendarApplicationService calendar;
    private final AttendanceService attendance;

    /**
     * Records the sole server-time check-in for the effective policy-local date.
     * Eligibility, workday, calendar, and exact frozen leave allocation are evaluated in the transaction;
     * a concurrent unique conflict is returned as {@link AttendanceRejection#ALREADY_CHECKED_IN}.
     *
     * @param internId Intern account identifier
     * @return persisted raw attendance record
     */
    @Transactional
    public AttendanceRecord checkIn(long internId) {
        Instant now = clock.instant();
        AttendancePolicy policy = timeline().resolve(now);
        LocalDate workDate = now.atZone(policy.zoneId()).toLocalDate();
        Optional<AttendanceRecord> existing = recordEntities
                .findByInternUserIdAndWorkDate(internId, workDate)
                .map(AttendanceRecordEntity::toDomain);
        AttendanceRecord record = attendance.checkIn(
                internId, now, policy, dayContext(internId, workDate), existing);
        try {
            return recordEntities.saveAndFlush(new AttendanceRecordEntity(
                            record.internId(),
                            record.workDate(),
                            policyEntities.getReferenceById(record.policy().id()),
                            record.checkInAt(),
                            record.checkOutAt()))
                    .toDomain();
        } catch (DataIntegrityViolationException conflict) {
            throw new AttendanceException(AttendanceRejection.ALREADY_CHECKED_IN);
        }
    }

    /**
     * Records the first server-time checkout for today's open row under its attached policy cutoff.
     * Account eligibility is revalidated for the persisted work date, and an optimistic race is returned as
     * {@link AttendanceRejection#ALREADY_CHECKED_OUT}; rejected attempts do not replace raw checkout.
     *
     * @param internId Intern account identifier
     * @return persisted checked-out record
     */
    @Transactional
    public AttendanceRecord checkOut(long internId) {
        Instant now = clock.instant();
        AttendancePolicy currentPolicy = timeline().resolve(now);
        LocalDate workDate = now.atZone(currentPolicy.zoneId()).toLocalDate();
        Optional<AttendanceRecordEntity> entity = recordEntities.findByInternUserIdAndWorkDate(internId, workDate);
        AttendanceRecord checkedOut = attendance.checkOut(entity.map(AttendanceRecordEntity::toDomain), now);
        AttendanceRecordEntity persisted = entity.orElseThrow();
        if (!accounts.isEligibleIntern(internId, persisted.workDate())) {
            throw new AttendanceException(AttendanceRejection.INACTIVE_INTERN);
        }
        persisted.setCheckOutAt(checkedOut.checkOutAt());
        try {
            return recordEntities.saveAndFlush(persisted).toDomain();
        } catch (ObjectOptimisticLockingFailureException conflict) {
            throw new AttendanceException(AttendanceRejection.ALREADY_CHECKED_OUT);
        }
    }

    /**
     * Reports an eligible Intern's current policy-local business-date punch state.
     *
     * @param internId Intern account identifier
     * @return presentation-safe current state
     */
    @Transactional(readOnly = true)
    public AttendanceCurrentState currentState(long internId) {
        Instant now = clock.instant();
        AttendancePolicy policy = timeline().resolve(now);
        LocalDate workDate = now.atZone(policy.zoneId()).toLocalDate();
        if (!accounts.isEligibleIntern(internId, workDate)) {
            throw new AttendanceException(AttendanceRejection.INACTIVE_INTERN);
        }
        return recordEntities.findByInternUserIdAndWorkDate(internId, workDate)
                .map(AttendanceRecordEntity::toDomain)
                .map(record -> record.checkOutAt() == null
                        ? AttendanceCurrentState.CHECKED_IN
                        : AttendanceCurrentState.CHECKED_OUT)
                .orElse(AttendanceCurrentState.NOT_CHECKED_IN);
    }

    /**
     * Returns inclusive historical rows newest-first, allowing Interns only their own history while Mentor and Admin
     * actors may inspect another Intern. DTOs retain raw instants and provide attached-policy local display values.
     *
     * @param actor authenticated Attendance authorization context
     * @param internId target Intern account identifier
     * @param from inclusive first local date
     * @param to inclusive last local date
     * @return immutable presentation/reporting history items
     */
    @Transactional(readOnly = true)
    public List<AttendanceHistoryItem> history(
            AttendanceActor actor, long internId, LocalDate from, LocalDate to) {
        if (actor.role() == AttendanceRole.INTERN && actor.userId() != internId) {
            throw new AccessDeniedException("Interns may view only their own attendance");
        }
        if (from.isAfter(to)) {
            throw new IllegalArgumentException("from must not be after to");
        }
        return recordEntities.findByInternUserIdAndWorkDateBetweenOrderByWorkDateDesc(internId, from, to)
                .stream()
                .map(AttendanceRecordEntity::toDomain)
                .map(record -> new AttendanceHistoryItem(
                        record.workDate(),
                        record.checkInAt(),
                        record.checkOutAt(),
                        record.policy(),
                        record.violations(clock.instant())))
                .toList();
    }

    /**
     * Resolves the current business date in the effective policy timezone.
     *
     * @return current policy-local date from the injected server clock
     */
    @Transactional(readOnly = true)
    public LocalDate currentBusinessDate() {
        AttendancePolicy policy = timeline().resolve(clock.instant());
        return clock.instant().atZone(policy.zoneId()).toLocalDate();
    }

    private AttendancePolicyTimeline timeline() {
        return new AttendancePolicyTimeline(policyEntities
                .findAllByOrderByEffectiveFromAsc()
                .stream()
                .map(AttendancePolicyEntity::toDomain)
                .toList());
    }

    private AttendanceDayContext dayContext(long internId, LocalDate workDate) {
        return new AttendanceDayContext(
                accounts.isEligibleIntern(internId, workDate),
                calendar.isGlobalDayOff(workDate),
                queries.hasApprovedLeave(internId, workDate));
    }
}
