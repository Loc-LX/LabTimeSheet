package com.lab.labtimesheet.feature.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRole;
import com.lab.labtimesheet.feature.attendance.model.AttendancePolicyFixtures;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRecord;
import com.lab.labtimesheet.feature.attendance.model.CorrectionStatus;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionRequestCommand;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceCorrectionEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceRecordEntity;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceCorrectionEventRepository;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceCorrectionRepository;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceRecordRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.data.domain.Pageable;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

class AttendanceCorrectionApplicationServiceTest {

    @Test
    void rejectsBlankCorrectionReasonBeforeRecordLookup() {
        AttendanceCorrectionApplicationService service = new AttendanceCorrectionApplicationService(
                Clock.fixed(Instant.parse("2026-08-14T10:00:00Z"), ZoneOffset.UTC),
                mock(AttendanceRecordRepository.class),
                mock(AttendanceCorrectionRepository.class),
                mock(AttendanceCorrectionEventRepository.class),
                mock(AccountService.class),
                mock(TransactionTemplate.class));

        assertThatThrownBy(() -> service.submit(
                        new AttendanceActor(42L, AttendanceRole.INTERN),
                        99L,
                        new CorrectionRequestCommand(LocalDateTime.of(2026, 8, 14, 15, 0), "  ")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void expirySamplesClockAfterLockedRowAcquisition() {
        Instant beforeLock = Instant.parse("2026-08-15T09:00:59Z");
        Instant afterLock = Instant.parse("2026-08-15T09:01:00Z");
        AtomicReference<Instant> now = new AtomicReference<>(beforeLock);
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenAnswer(invocation -> now.get());

        AttendanceCorrectionRepository corrections = mock(AttendanceCorrectionRepository.class);
        AttendanceCorrectionEntity candidate = mock(AttendanceCorrectionEntity.class);
        AttendanceCorrectionEntity locked = mock(AttendanceCorrectionEntity.class);
        when(candidate.id()).thenReturn(11L);
        when(locked.id()).thenReturn(11L);
        when(locked.status()).thenReturn(CorrectionStatus.PENDING);
        when(locked.lockedAt()).thenReturn(null);
        when(locked.decisionDeadline()).thenReturn(afterLock);
        when(corrections.findExpiredUnlocked(eq(beforeLock), any(Pageable.class)))
                .thenReturn(List.of(candidate));
        when(corrections.findForUpdateById(11L)).thenAnswer(invocation -> {
            now.set(afterLock);
            return Optional.of(locked);
        });

        AttendanceCorrectionApplicationService service = new AttendanceCorrectionApplicationService(
                clock,
                mock(AttendanceRecordRepository.class),
                corrections,
                mock(AttendanceCorrectionEventRepository.class),
                mock(AccountService.class),
                mock(TransactionTemplate.class));

        assertThat(service.expire(1)).isEqualTo(1);
    }

    @Test
    void historyGuardUsesOneBulkCorrectionQuery() {
        AttendanceCorrectionRepository corrections = mock(AttendanceCorrectionRepository.class);
        AttendanceRecordEntity first = mock(AttendanceRecordEntity.class);
        AttendanceRecordEntity second = mock(AttendanceRecordEntity.class);
        when(first.id()).thenReturn(101L);
        when(second.id()).thenReturn(102L);
        when(first.toDomain()).thenReturn(new AttendanceRecord(
                42L, LocalDate.of(2026, 8, 14), AttendancePolicyFixtures.seeded(1L),
                Instant.parse("2026-08-14T02:00:00Z"), null));
        when(second.toDomain()).thenReturn(new AttendanceRecord(
                42L, LocalDate.of(2026, 8, 15), AttendancePolicyFixtures.seeded(1L),
                Instant.parse("2026-08-15T02:00:00Z"), null));
        when(corrections.findByAttendanceRecordIdInForUpdate(List.of(101L, 102L))).thenReturn(List.of());

        AttendanceCorrectionApplicationService service = new AttendanceCorrectionApplicationService(
                Clock.fixed(Instant.parse("2026-08-16T00:00:00Z"), ZoneOffset.UTC),
                mock(AttendanceRecordRepository.class),
                corrections,
                mock(AttendanceCorrectionEventRepository.class),
                mock(AccountService.class),
                mock(TransactionTemplate.class));

        assertThat(service.prepareHistory(List.of(first, second)))
                .containsEntry(101L, null)
                .containsEntry(102L, null);
        verify(corrections).findByAttendanceRecordIdInForUpdate(List.of(101L, 102L));
    }
}
