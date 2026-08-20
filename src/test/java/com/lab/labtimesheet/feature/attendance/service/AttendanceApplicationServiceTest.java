package com.lab.labtimesheet.feature.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.attendance.exception.AttendanceException;
import com.lab.labtimesheet.feature.attendance.exception.AttendanceRejection;
import com.lab.labtimesheet.feature.attendance.model.AttendancePolicy;
import com.lab.labtimesheet.feature.attendance.model.AttendancePolicyFixtures;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRecord;
import com.lab.labtimesheet.feature.attendance.model.dto.AttendanceCurrentState;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendancePolicyEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceRecordEntity;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceCorrectionRepository;
import com.lab.labtimesheet.feature.attendance.repository.AttendancePolicyRepository;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceQueryRepository;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceRecordRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

class AttendanceApplicationServiceTest {

    private static final long INTERN_ID = 42L;
    private static final Instant NOW = Instant.parse("2026-08-14T02:00:00Z");
    private static final LocalDate WORK_DATE = LocalDate.of(2026, 8, 14);

    private final AttendancePolicyRepository policies = mock(AttendancePolicyRepository.class);
    private final AttendanceRecordRepository records = mock(AttendanceRecordRepository.class);
    private final AccountService accounts = mock(AccountService.class);
    private AttendanceApplicationService attendance;

    @BeforeEach
    void setUp() {
        AttendancePolicyEntity policyEntity = mock(AttendancePolicyEntity.class);
        when(policyEntity.toDomain()).thenReturn(AttendancePolicyFixtures.seeded(1L));
        when(policies.findAllByOrderByEffectiveFromAsc()).thenReturn(List.of(policyEntity));
        when(accounts.isEligibleIntern(INTERN_ID, WORK_DATE)).thenReturn(true);
        attendance = new AttendanceApplicationService(
                Clock.fixed(NOW, ZoneOffset.UTC),
                policies,
                records,
                mock(AttendanceCorrectionRepository.class),
                mock(AttendanceQueryRepository.class),
                accounts,
                mock(CalendarApplicationService.class),
                new AttendanceService());
    }

    @Test
    void reportsCurrentBusinessDatePunchStateWithoutExposingPersistenceTypes() {
        when(records.findByInternUserIdAndWorkDate(INTERN_ID, WORK_DATE))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(entityFor(null)))
                .thenReturn(Optional.of(entityFor(NOW.plusSeconds(60))));

        assertThat(attendance.currentState(INTERN_ID)).isEqualTo(AttendanceCurrentState.NOT_CHECKED_IN);
        assertThat(attendance.currentState(INTERN_ID)).isEqualTo(AttendanceCurrentState.CHECKED_IN);
        assertThat(attendance.currentState(INTERN_ID)).isEqualTo(AttendanceCurrentState.CHECKED_OUT);
    }

    @Test
    void rejectsCurrentStateLookupForIneligibleIntern() {
        when(accounts.isEligibleIntern(INTERN_ID, WORK_DATE)).thenReturn(false);

        assertThatThrownBy(() -> attendance.currentState(INTERN_ID))
                .isInstanceOfSatisfying(AttendanceException.class,
                        exception -> assertThat(exception.rejection())
                                .isEqualTo(AttendanceRejection.INACTIVE_INTERN));
    }

    @Test
    void rejectsCheckoutWhenInternIsNoLongerEligibleForPersistedWorkDate() {
        AttendanceRecordEntity entity = entityFor(null);
        when(records.findByInternUserIdAndWorkDate(INTERN_ID, WORK_DATE))
                .thenReturn(Optional.of(entity));
        when(accounts.isEligibleIntern(INTERN_ID, WORK_DATE)).thenReturn(false);

        assertThatThrownBy(() -> attendance.checkOut(INTERN_ID))
                .isInstanceOfSatisfying(AttendanceException.class,
                        exception -> assertThat(exception.rejection())
                                .isEqualTo(AttendanceRejection.INACTIVE_INTERN));
    }

    @Test
    void translatesConcurrentCheckInUniqueConflictToStableDuplicateRejection() {
        when(records.findByInternUserIdAndWorkDate(INTERN_ID, WORK_DATE)).thenReturn(Optional.empty());
        when(records.saveAndFlush(any(AttendanceRecordEntity.class)))
                .thenThrow(new DataIntegrityViolationException("concurrent unique conflict"));

        assertThatThrownBy(() -> attendance.checkIn(INTERN_ID))
                .isInstanceOfSatisfying(AttendanceException.class,
                        exception -> assertThat(exception.rejection())
                                .isEqualTo(AttendanceRejection.ALREADY_CHECKED_IN));
    }

    @Test
    void translatesConcurrentCheckoutVersionConflictToStableDuplicateRejection() {
        AttendanceRecordEntity entity = entityFor(null);
        when(records.findByInternUserIdAndWorkDate(INTERN_ID, WORK_DATE)).thenReturn(Optional.of(entity));
        when(records.saveAndFlush(entity))
                .thenThrow(new ObjectOptimisticLockingFailureException(AttendanceRecordEntity.class, 1L));

        assertThatThrownBy(() -> attendance.checkOut(INTERN_ID))
                .isInstanceOfSatisfying(AttendanceException.class,
                        exception -> assertThat(exception.rejection())
                                .isEqualTo(AttendanceRejection.ALREADY_CHECKED_OUT));
    }

    private static AttendanceRecordEntity entityFor(Instant checkOutAt) {
        AttendanceRecordEntity entity = mock(AttendanceRecordEntity.class);
        when(entity.toDomain()).thenReturn(new AttendanceRecord(
                INTERN_ID,
                WORK_DATE,
                AttendancePolicyFixtures.seeded(1L),
                NOW,
                checkOutAt));
        when(entity.workDate()).thenReturn(WORK_DATE);
        return entity;
    }
}
