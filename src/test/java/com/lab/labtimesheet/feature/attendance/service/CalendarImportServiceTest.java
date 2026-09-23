package com.lab.labtimesheet.feature.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.platform.model.GlobalRole;
import com.lab.labtimesheet.feature.attendance.model.dto.CalendarImportSelection;
import com.lab.labtimesheet.feature.attendance.model.entity.GlobalCalendarEventEntity;
import com.lab.labtimesheet.feature.attendance.repository.GlobalCalendarEventRepository;
import com.lab.labtimesheet.feature.integration.model.dto.HolidayApiCandidate;
import com.lab.labtimesheet.feature.integration.model.dto.HolidayApiPreview;
import com.lab.labtimesheet.feature.integration.model.dto.HolidayApiPreviewStatus;
import com.lab.labtimesheet.feature.integration.service.HolidayApiConfigurationService;
import com.lab.labtimesheet.feature.identity.service.AccountService;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;
import org.mockito.InOrder;

class CalendarImportServiceTest {

    @Test
    void providerPreviewIsAdminOnlyAndLocalReadsDoNotCallProvider() {
        GlobalCalendarEventRepository events = mock(GlobalCalendarEventRepository.class);
        HolidayApiConfigurationService holidayApi = mock(HolidayApiConfigurationService.class);
        CalendarApplicationService service = service(events, holidayApi);
        AttendanceActor admin = new AttendanceActor(1L, GlobalRole.ADMIN);
        HolidayApiPreview upstream = new HolidayApiPreview(
                HolidayApiPreviewStatus.SUCCESS, List.of(), "loaded", Instant.parse("2026-08-20T00:00:00Z"));
        when(holidayApi.preview(1L, 2026)).thenReturn(upstream);

        assertThat(service.previewFromProvider(admin.userId(), 2026)).isSameAs(upstream);
        verify(holidayApi).preview(1L, 2026);

        HolidayApiConfigurationService localProvider = mock(HolidayApiConfigurationService.class);
        CalendarApplicationService localReadService = service(events, localProvider);
        when(events.existsByCalendarDateAndDayOffTrue(LocalDate.of(2026, 8, 20))).thenReturn(true);
        assertThat(localReadService.isGlobalDayOff(LocalDate.of(2026, 8, 20))).isTrue();
        verifyNoInteractions(localProvider);
        assertThatThrownBy(() -> service.previewFromProvider(
                2L, 2026))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }

    @Test
    void providerFailureStaysActionableAndDoesNotBecomeImportRows() {
        GlobalCalendarEventRepository events = mock(GlobalCalendarEventRepository.class);
        HolidayApiConfigurationService holidayApi = mock(HolidayApiConfigurationService.class);
        CalendarApplicationService service = service(events, holidayApi);
        AttendanceActor admin = new AttendanceActor(1L, GlobalRole.ADMIN);
        HolidayApiPreview unavailable = new HolidayApiPreview(
                HolidayApiPreviewStatus.UNAVAILABLE, List.of(),
                "HolidayAPI is unavailable; use a manual calendar event.", null);
        when(holidayApi.preview(1L, 2026)).thenReturn(unavailable);

        HolidayApiPreview result = service.previewFromProvider(admin.userId(), 2026);

        assertThat(result.status()).isEqualTo(HolidayApiPreviewStatus.UNAVAILABLE);
        assertThat(result.message()).contains("manual calendar event");
        assertThat(service.preview(admin.userId(), 2026, result)).isEmpty();
    }

    @Test
    void previewRejectsCandidateOutsideRequestedYear() {
        CalendarApplicationService service = service(
                mock(GlobalCalendarEventRepository.class), mock(HolidayApiConfigurationService.class));
        HolidayApiPreview upstream = new HolidayApiPreview(
                HolidayApiPreviewStatus.SUCCESS,
                List.of(new HolidayApiCandidate(
                        "vn-2025", "Prior year", LocalDate.of(2025, 12, 31), LocalDate.of(2025, 12, 31), true)),
                "loaded",
                Instant.parse("2026-08-20T00:00:00Z"));

        assertThatThrownBy(() -> service.preview(1L, 2026, upstream))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Holiday candidate dates must belong to the requested year");
    }

    @Test
    void importsExistingCandidatesInCanonicalSourceUuidLockOrder() {
        GlobalCalendarEventRepository events = mock(GlobalCalendarEventRepository.class);
        CalendarApplicationService service = service(events, mock(HolidayApiConfigurationService.class));
        HolidayApiCandidate later = new HolidayApiCandidate(
                "b-source", "Later", LocalDate.of(2026, 9, 3), LocalDate.of(2026, 9, 3), false);
        HolidayApiCandidate earlier = new HolidayApiCandidate(
                "a-source", "Earlier", LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 2), false);
        GlobalCalendarEventEntity existing = mock(GlobalCalendarEventEntity.class);
        when(events.findForUpdateBySourceAndSourceUuid("HOLIDAY_API", "a-source"))
                .thenReturn(Optional.of(existing));
        when(events.findForUpdateBySourceAndSourceUuid("HOLIDAY_API", "b-source"))
                .thenReturn(Optional.of(existing));
        HolidayApiPreview trustedPreview = new HolidayApiPreview(
                HolidayApiPreviewStatus.SUCCESS,
                List.of(later, earlier),
                "loaded",
                Instant.parse("2026-08-20T00:00:00Z"));

        assertThat(service.importSelectedFromTrustedPreview(1L, 2026,
                        trustedPreview,
                        List.of(new CalendarImportSelection("b-source", false),
                                new CalendarImportSelection("a-source", false))))
                .isEmpty();

        InOrder locks = inOrder(events);
        locks.verify(events).findForUpdateBySourceAndSourceUuid("HOLIDAY_API", "a-source");
        locks.verify(events).findForUpdateBySourceAndSourceUuid("HOLIDAY_API", "b-source");
    }

    @Test
    void publicHolidayOnlySetsTheDefaultAndRepeatedImportDoesNotOverwrite() {
        GlobalCalendarEventRepository events = mock(GlobalCalendarEventRepository.class);
        CalendarApplicationService service = service(events, mock(HolidayApiConfigurationService.class));
        HolidayApiCandidate candidate = new HolidayApiCandidate(
                "vn-1",
                "Observed holiday",
                LocalDate.of(2026, 9, 2),
                LocalDate.of(2026, 9, 3),
                true);

        HolidayApiPreview upstream = new HolidayApiPreview(
                HolidayApiPreviewStatus.SUCCESS,
                List.of(candidate, candidate),
                "loaded",
                Instant.parse("2026-08-20T00:00:00Z"));
        assertThat(service.preview(1L, 2026, upstream))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.selectedByDefault()).isTrue();
                    assertThat(item.retrievedAt()).isEqualTo(upstream.retrievedAt());
                });
        assertThat(service.preview(1L, 2026, upstream)).hasSize(1);

        GlobalCalendarEventEntity existing = mock(GlobalCalendarEventEntity.class);
        when(events.findForUpdateBySourceAndSourceUuid("HOLIDAY_API", "vn-1"))
                .thenReturn(Optional.of(existing));

        assertThat(service.importSelectedFromTrustedPreview(1L,
                        2026,
                        upstream,
                        List.of(new CalendarImportSelection("vn-1", false))))
                .isEmpty();
    }

    @Test
    void importResolvesCandidateAndProvenanceFromTrustedPreview() {
        GlobalCalendarEventRepository events = mock(GlobalCalendarEventRepository.class);
        CalendarApplicationService service = service(events, mock(HolidayApiConfigurationService.class));
        HolidayApiCandidate trustedCandidate = new HolidayApiCandidate(
                "trusted-source", "Trusted holiday", LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 3), true);
        HolidayApiPreview trustedPreview = new HolidayApiPreview(
                HolidayApiPreviewStatus.SUCCESS,
                List.of(trustedCandidate),
                "loaded",
                Instant.parse("2026-08-20T00:00:00Z"));
        when(events.findForUpdateBySourceAndSourceUuid("HOLIDAY_API", "trusted-source"))
                .thenReturn(Optional.of(mock(GlobalCalendarEventEntity.class)));

        assertThat(service.importSelectedFromTrustedPreview(1L,
                        2026,
                        trustedPreview,
                        List.of(new CalendarImportSelection("trusted-source", false))))
                .isEmpty();
        assertThat(CalendarImportSelection.class.getRecordComponents())
                .extracting(java.lang.reflect.RecordComponent::getName)
                .containsExactly("sourceUuid", "dayOff");
    }

    private static CalendarApplicationService service(
            GlobalCalendarEventRepository events, HolidayApiConfigurationService holidayApi) {
        AccountService accounts = mock(AccountService.class);
        when(accounts.requireActiveAdminId(org.mockito.ArgumentMatchers.anyLong()))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(accounts.requireActiveAdminId(2L)).thenThrow(new IllegalArgumentException("An active Admin is required"));
        TransactionTemplate transactions = mock(TransactionTemplate.class);
        PlatformTransactionManager transactionManager = mock(PlatformTransactionManager.class);
        when(transactions.getTransactionManager()).thenReturn(transactionManager);
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        return new CalendarApplicationService(
                Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), ZoneOffset.UTC),
                accounts,
                policyRepository(),
                events,
                holidayApi,
                transactions);
    }

    private static com.lab.labtimesheet.feature.attendance.repository.AttendancePolicyRepository policyRepository() {
        var policies = mock(com.lab.labtimesheet.feature.attendance.repository.AttendancePolicyRepository.class);
        when(policies.findFirstByOrderByEffectiveFromAsc())
                .thenReturn(Optional.of(mock(com.lab.labtimesheet.feature.attendance.model.entity.AttendancePolicyEntity.class)));
        return policies;
    }
}
