package com.lab.labtimesheet.feature.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lab.labtimesheet.feature.identity.model.AccountStatus;
import com.lab.labtimesheet.feature.identity.model.InternshipStatus;
import com.lab.labtimesheet.feature.identity.model.dto.AccountIdentity;
import com.lab.labtimesheet.feature.identity.model.dto.LockedAccountMutationEligibility;
import com.lab.labtimesheet.feature.identity.service.AccountService;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.AttendancePolicyFixtures;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRecord;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRole;
import com.lab.labtimesheet.feature.attendance.model.CorrectionStatus;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionRequestCommand;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceCorrectionEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceRecordEntity;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceCorrectionEventRepository;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceCorrectionRepository;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceRecordRepository;
import com.lab.labtimesheet.feature.notification.service.NotificationService;
import com.lab.labtimesheet.platform.model.GlobalRole;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;
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
                calendar(),
                mock(TransactionTemplate.class),
                mock(NotificationService.class));

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
        AttendanceRecordRepository records = mock(AttendanceRecordRepository.class);
        AttendanceCorrectionRepository.ExpiredRecipientRoute candidate =
                mock(AttendanceCorrectionRepository.ExpiredRecipientRoute.class);
        AttendanceCorrectionEntity locked = mock(AttendanceCorrectionEntity.class);
        when(candidate.getCorrectionId()).thenReturn(11L);
        when(candidate.getAttendanceRecordId()).thenReturn(42L);
        when(candidate.getInternUserId()).thenReturn(42L);
        when(locked.id()).thenReturn(11L);
        when(locked.status()).thenReturn(CorrectionStatus.PENDING);
        when(locked.lockedAt()).thenReturn(null);
        when(locked.decisionDeadline()).thenReturn(afterLock);
        when(corrections.findExpiredRecipientRoutes(eq(beforeLock), any(Pageable.class)))
                .thenReturn(List.of(candidate));
        when(corrections.findForUpdateById(11L)).thenAnswer(invocation -> {
            now.set(afterLock);
            return Optional.of(locked);
        });
        AttendanceRecordEntity attendance = mock(AttendanceRecordEntity.class);
        when(attendance.policyVersionId()).thenReturn(1L);
        when(attendance.toDomain(any())).thenReturn(new AttendanceRecord(
                42L,
                LocalDate.of(2026, 8, 15),
                AttendancePolicyFixtures.seeded(1L),
                Instant.parse("2026-08-15T02:00:00Z"),
                null));
        when(records.findById(42L)).thenReturn(Optional.of(attendance));
        AccountService accounts = mock(AccountService.class);
        AtomicBoolean accountLocked = new AtomicBoolean();
        when(accounts.lockedAccountMutationEligibility(any())).thenAnswer(invocation -> {
            accountLocked.set(true);
            return List.of(new LockedAccountMutationEligibility(
                    42L,
                    GlobalRole.INTERN,
                    AccountStatus.ACTIVE,
                    java.util.Optional.of(InternshipStatus.ACTIVE)));
        });
        when(accounts.requireIdentityById(42L)).thenAnswer(invocation -> {
            assertThat(accountLocked).as("recipient identity must be read after the Account lock").isTrue();
            return new AccountIdentity(
                    42L, "intern@example.test", "Intern", GlobalRole.INTERN, AccountStatus.ACTIVE);
        });

        AttendanceCorrectionApplicationService service = new AttendanceCorrectionApplicationService(
                clock,
                records,
                corrections,
                mock(AttendanceCorrectionEventRepository.class),
                accounts,
                calendar(),
                mock(TransactionTemplate.class),
                mock(NotificationService.class));

        assertThat(service.expire(1)).isEqualTo(1);
    }

    @Test
    void historyGuardUsesOneBulkCorrectionQuery() {
        AttendanceCorrectionRepository corrections = mock(AttendanceCorrectionRepository.class);
        AttendanceRecordEntity first = mock(AttendanceRecordEntity.class);
        AttendanceRecordEntity second = mock(AttendanceRecordEntity.class);
        when(first.id()).thenReturn(101L);
        when(second.id()).thenReturn(102L);
        when(first.policyVersionId()).thenReturn(1L);
        when(second.policyVersionId()).thenReturn(1L);
        when(first.toDomain(any())).thenReturn(new AttendanceRecord(
                42L, LocalDate.of(2026, 8, 14), AttendancePolicyFixtures.seeded(1L),
                Instant.parse("2026-08-14T02:00:00Z"), null));
        when(second.toDomain(any())).thenReturn(new AttendanceRecord(
                42L, LocalDate.of(2026, 8, 15), AttendancePolicyFixtures.seeded(1L),
                Instant.parse("2026-08-15T02:00:00Z"), null));
        when(corrections.findByAttendanceRecordIdInForUpdate(List.of(101L, 102L))).thenReturn(List.of());

        AttendanceCorrectionApplicationService service = new AttendanceCorrectionApplicationService(
                Clock.fixed(Instant.parse("2026-08-16T00:00:00Z"), ZoneOffset.UTC),
                mock(AttendanceRecordRepository.class),
                corrections,
                mock(AttendanceCorrectionEventRepository.class),
                mock(AccountService.class),
                calendar(),
                mock(TransactionTemplate.class),
                mock(NotificationService.class));

        assertThat(service.prepareHistory(List.of(first, second)))
                .containsEntry(101L, null)
                .containsEntry(102L, null);
        verify(corrections).findByAttendanceRecordIdInForUpdate(List.of(101L, 102L));
    }

    /**
     * Protects {@code COR-003}. Observable break: the submission deadline is anchored to the
     * checkout cutoff instead of to scheduled end, which silently grants every Intern the checkout
     * grace as extra time to file a correction.
     *
     * <p>Expected value derived from the rule and {@code ATT-002}, not from the implementation.
     * The seeded policy schedules 08:30 to 15:30 in {@code Asia/Ho_Chi_Minh} with 30 minutes of
     * checkout grace. For an attendance row on 14 August 2026 that makes scheduled end
     * 15:30 local, which is {@code 2026-08-14T08:30:00Z}, and the checkout cutoff 16:00 local,
     * which is {@code 2026-08-14T09:00:00Z}. {@code COR-003} anchors the inclusive submission
     * deadline to scheduled end plus 24 hours, so it falls at 15:30 local the following day,
     * {@code 2026-08-15T08:30:00Z}. The rule states that same figure in words.
     *
     * <p>The assertion names the wrong anchor as well as the right one, because the two differ by
     * exactly the checkout grace and a test that only asserted "some instant on 15 August" would
     * pass against either.
     */
    @Test
    void correctionSubmissionDeadlineAnchorsToScheduledEndRatherThanTheCheckoutCutoff() {
        Instant scheduledEndOfTheAttendanceDate = Instant.parse("2026-08-14T08:30:00Z");
        Instant checkoutCutoffOfTheAttendanceDate = Instant.parse("2026-08-14T09:00:00Z");
        Instant expectedDeadline = scheduledEndOfTheAttendanceDate.plusSeconds(24 * 60 * 60L);
        Instant cutoffAnchoredDeadline = checkoutCutoffOfTheAttendanceDate.plusSeconds(24 * 60 * 60L);

        AttendanceRecordRepository records = mock(AttendanceRecordRepository.class);
        AttendanceRecordEntity entity = mock(AttendanceRecordEntity.class);
        when(entity.policyVersionId()).thenReturn(1L);
        when(entity.toDomain(any())).thenReturn(new AttendanceRecord(
                42L,
                LocalDate.of(2026, 8, 14),
                AttendancePolicyFixtures.seeded(1L),
                Instant.parse("2026-08-14T02:00:00Z"),
                null));
        when(records.findById(77L)).thenReturn(Optional.of(entity));

        AttendanceCorrectionRepository corrections = mock(AttendanceCorrectionRepository.class);
        when(corrections.findByAttendanceRecordId(77L)).thenReturn(Optional.empty());
        // The entity guards id() until JPA assigns one, and submit() reads it while writing the
        // SUBMITTED event. A spy keeps every value the constructor computed, including the
        // deadline under test, and supplies only the identifier persistence would have given.
        when(corrections.saveAndFlush(any(AttendanceCorrectionEntity.class)))
                .thenAnswer(invocation -> {
                    AttendanceCorrectionEntity persisted =
                            org.mockito.Mockito.spy(invocation.<AttendanceCorrectionEntity>getArgument(0));
                    org.mockito.Mockito.doReturn(1L).when(persisted).id();
                    return persisted;
                });

        AccountService accounts = mock(AccountService.class);
        when(accounts.activeGlobalMentorIdentities()).thenReturn(List.of());
        when(accounts.lockedAccountMutationEligibility(any())).thenReturn(List.of());

        AttendanceCorrectionApplicationService service = new AttendanceCorrectionApplicationService(
                Clock.fixed(Instant.parse("2026-08-14T10:00:00Z"), ZoneOffset.UTC),
                records,
                corrections,
                mock(AttendanceCorrectionEventRepository.class),
                accounts,
                calendar(),
                mock(TransactionTemplate.class),
                mock(NotificationService.class));

        service.submit(
                new AttendanceActor(42L, AttendanceRole.INTERN),
                77L,
                new CorrectionRequestCommand(LocalDateTime.of(2026, 8, 14, 15, 0), "Forgot to check out"));

        org.mockito.ArgumentCaptor<AttendanceCorrectionEntity> saved =
                org.mockito.ArgumentCaptor.forClass(AttendanceCorrectionEntity.class);
        verify(corrections).saveAndFlush(saved.capture());
        assertThat(saved.getValue().submissionDeadline())
                .as("COR-003 anchors the submission deadline to scheduled end, not the checkout cutoff")
                .isEqualTo(expectedDeadline)
                .isNotEqualTo(cutoffAnchoredDeadline);
    }

    private static CalendarApplicationService calendar() {
        CalendarApplicationService calendar = mock(CalendarApplicationService.class);
        when(calendar.policiesByVersionIds(any())).thenReturn(Map.of(1L, AttendancePolicyFixtures.seeded(1L)));
        return calendar;
    }
}
