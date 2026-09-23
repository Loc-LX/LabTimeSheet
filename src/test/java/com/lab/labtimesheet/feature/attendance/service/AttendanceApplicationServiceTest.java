package com.lab.labtimesheet.feature.attendance.service;

import com.lab.labtimesheet.feature.internship.service.InternshipService;

import com.lab.labtimesheet.feature.calendar.service.AttendancePolicyTimeline;

import com.lab.labtimesheet.feature.calendar.service.CalendarApplicationService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lab.labtimesheet.feature.identity.service.AccountService;
import com.lab.labtimesheet.feature.attendance.exception.AttendanceException;
import com.lab.labtimesheet.feature.attendance.exception.AttendanceRejection;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.calendar.model.AttendancePolicy;
import com.lab.labtimesheet.feature.calendar.model.AttendancePolicyFixtures;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRecord;
import com.lab.labtimesheet.platform.model.GlobalRole;
import com.lab.labtimesheet.feature.attendance.model.dto.AttendanceCurrentState;
import com.lab.labtimesheet.feature.attendance.model.dto.AttendanceReportDateContext;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceRecordEntity;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceQueryRepository;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceRecordRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.transaction.support.TransactionTemplate;

class AttendanceApplicationServiceTest {

    private static final long INTERN_ID = 42L;
    private static final Instant NOW = Instant.parse("2026-08-14T02:00:00Z");
    private static final LocalDate WORK_DATE = LocalDate.of(2026, 8, 14);

    private final AttendanceRecordRepository records = mock(AttendanceRecordRepository.class);
    private final AccountService accounts = mock(AccountService.class);
    private final InternshipService internships = mock(InternshipService.class);
    private final AttendanceCorrectionApplicationService corrections = mock(AttendanceCorrectionApplicationService.class);
    private final CalendarApplicationService calendar = mock(CalendarApplicationService.class);
    private AttendanceApplicationService attendance;

    @BeforeEach
    void setUp() {
        when(calendar.policyTimeline()).thenReturn(new AttendancePolicyTimeline(
                List.of(AttendancePolicyFixtures.seeded(1L))));
        when(calendar.policiesByVersionIds(any())).thenReturn(Map.of(1L, AttendancePolicyFixtures.seeded(1L)));
        when(internships.isEligibleIntern(INTERN_ID, WORK_DATE)).thenReturn(true);
        attendance = new AttendanceApplicationService(
                Clock.fixed(NOW, ZoneOffset.UTC),
                records,
                mock(AttendanceQueryRepository.class),
                accounts,
                internships,
                calendar,
                new AttendanceService(),
                corrections);
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
    void reportsEffectivePolicyAndGlobalCalendarContextWithoutReadingAttendanceRows() {
        when(calendar.isGlobalDayOff(WORK_DATE)).thenReturn(true);

        AttendanceReportDateContext context = attendance.reportDateContext(WORK_DATE);

        assertThat(context.reportDate()).isEqualTo(WORK_DATE);
        assertThat(context.configuredWorkday()).isTrue();
        assertThat(context.globalDayOff()).isTrue();
        assertThat(context.policyId()).isEqualTo(1L);
        assertThat(context.policyZoneId().getId()).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(context.label()).isEqualTo("Global day off");
        verify(calendar).isGlobalDayOff(WORK_DATE);
        verifyNoInteractions(records);
    }

    @Test
    void rejectsCurrentStateLookupForIneligibleIntern() {
        when(internships.isEligibleIntern(INTERN_ID, WORK_DATE)).thenReturn(false);

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
        when(internships.isEligibleIntern(INTERN_ID, WORK_DATE)).thenReturn(false);

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

    @Test
    void historyUsesOneBulkCorrectionGuardForLoadedRows() {
        AttendanceRecordEntity first = entityFor(null);
        AttendanceRecordEntity second = entityFor(null);
        when(first.id()).thenReturn(101L);
        when(second.id()).thenReturn(102L);
        when(records.findByInternUserIdAndWorkDateBetweenOrderByWorkDateDesc(
                        INTERN_ID, WORK_DATE, WORK_DATE))
                .thenReturn(List.of(first, second));
        HashMap<Long, Instant> effectiveCheckouts = new HashMap<>();
        effectiveCheckouts.put(101L, null);
        effectiveCheckouts.put(102L, null);
        when(corrections.prepareHistory(List.of(first, second))).thenReturn(effectiveCheckouts);

        attendance.history(
                new AttendanceActor(INTERN_ID, GlobalRole.INTERN), INTERN_ID, WORK_DATE, WORK_DATE);

        verify(corrections).prepareHistory(List.of(first, second));
    }

    private static AttendanceRecordEntity entityFor(Instant checkOutAt) {
        AttendanceRecordEntity entity = mock(AttendanceRecordEntity.class);
        when(entity.policyVersionId()).thenReturn(1L);
        when(entity.toDomain(any(AttendancePolicy.class))).thenReturn(new AttendanceRecord(
                INTERN_ID,
                WORK_DATE,
                AttendancePolicyFixtures.seeded(1L),
                NOW,
                checkOutAt));
        when(entity.workDate()).thenReturn(WORK_DATE);
        return entity;
    }
}
