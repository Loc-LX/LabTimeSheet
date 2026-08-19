package com.lab.labtimesheet.feature.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lab.labtimesheet.feature.attendance.exception.HolidayCalendarException;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRole;
import com.lab.labtimesheet.feature.attendance.model.dto.HolidayCandidate;
import com.lab.labtimesheet.feature.attendance.model.dto.HolidayImportSummary;
import com.lab.labtimesheet.feature.attendance.model.dto.HolidayPreviewRow;
import com.lab.labtimesheet.feature.attendance.model.dto.HolidaySelection;
import com.lab.labtimesheet.feature.attendance.model.entity.GlobalCalendarEventEntity;
import com.lab.labtimesheet.feature.attendance.repository.GlobalCalendarEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class HolidayImportServiceTest {

    private static final long ADMIN_ID = 7L;
    private static final int YEAR = 2026;
    private static final Instant NOW = Instant.parse("2026-08-14T02:00:00Z");

    private static final HolidayCandidate PUBLIC_HOLIDAY =
            new HolidayCandidate(
                    "11111111-1111-1111-1111-111111111111",
                    "National Day",
                    LocalDate.of(2026, 9, 2),
                    LocalDate.of(2026, 9, 2),
                    true);
    private static final HolidayCandidate NON_PUBLIC_OBSERVANCE =
            new HolidayCandidate(
                    "22222222-2222-2222-2222-222222222222",
                    "Lunar New Year Eve",
                    LocalDate.of(2026, 2, 16),
                    LocalDate.of(2026, 2, 17),
                    false);
    private static final HolidayCandidate SECOND_PUBLIC_HOLIDAY =
            new HolidayCandidate(
                    "33333333-3333-3333-3333-333333333333",
                    "Christmas Day",
                    LocalDate.of(2026, 12, 25),
                    LocalDate.of(2026, 12, 25),
                    true);

    private final HolidayApiClient client = mock(HolidayApiClient.class);
    private final GlobalCalendarEventRepository events = mock(GlobalCalendarEventRepository.class);
    private HolidayImportService holidays;

    @BeforeEach
    void setUp() {
        holidays = new HolidayImportService(Clock.fixed(NOW, ZoneOffset.UTC), client, events);
    }

    @Test
    void previewPreselectsOnlyPublicHolidaysAndFlagsAlreadyImported() {
        when(client.fetchVnHolidays(YEAR)).thenReturn(List.of(PUBLIC_HOLIDAY, NON_PUBLIC_OBSERVANCE, SECOND_PUBLIC_HOLIDAY));
        when(events.existsBySourceAndSourceUuid("HOLIDAY_API", PUBLIC_HOLIDAY.uuid())).thenReturn(true);
        when(events.existsBySourceAndSourceUuid("HOLIDAY_API", NON_PUBLIC_OBSERVANCE.uuid())).thenReturn(false);
        when(events.existsBySourceAndSourceUuid("HOLIDAY_API", SECOND_PUBLIC_HOLIDAY.uuid())).thenReturn(false);

        List<HolidayPreviewRow> rows = holidays.preview(admin(), YEAR);

        assertThat(rows).hasSize(3);
        assertThat(rows.get(0).candidate()).isEqualTo(PUBLIC_HOLIDAY);
        assertThat(rows.get(0).preselected()).isTrue();
        assertThat(rows.get(0).alreadyImported()).isTrue();
        assertThat(rows.get(1).candidate()).isEqualTo(NON_PUBLIC_OBSERVANCE);
        assertThat(rows.get(1).preselected()).isFalse();
        assertThat(rows.get(1).alreadyImported()).isFalse();
        assertThat(rows.get(2).candidate()).isEqualTo(SECOND_PUBLIC_HOLIDAY);
        assertThat(rows.get(2).preselected()).isTrue();
        assertThat(rows.get(2).alreadyImported()).isFalse();
    }

    @Test
    void previewPropagatesUnavailableClientAsActionableFailure() {
        when(client.fetchVnHolidays(YEAR))
                .thenThrow(new HolidayCalendarException("HolidayAPI is not configured; use manual calendar events instead"));

        assertThatThrownBy(() -> holidays.preview(admin(), YEAR))
                .isInstanceOf(HolidayCalendarException.class)
                .hasMessageContaining("not configured");
        verify(events, never()).existsBySourceAndSourceUuid(any(), any());
    }

    @Test
    void previewRejectsNonAdminWithoutContactingTheClient() {
        assertThatThrownBy(() -> holidays.preview(intern(), YEAR))
                .isInstanceOf(AccessDeniedException.class);
        verify(client, never()).fetchVnHolidays(any(int.class));
    }

    @Test
    void previewRejectsUnsupportedYears() {
        assertThatThrownBy(() -> holidays.preview(admin(), 1999))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> holidays.preview(admin(), 2101))
                .isInstanceOf(IllegalArgumentException.class);
        verify(client, never()).fetchVnHolidays(any(int.class));
    }

    @Test
    void importSkipsAlreadyImportedUuidsAndImportsTheRemainingSelections() {
        when(client.fetchVnHolidays(YEAR)).thenReturn(List.of(PUBLIC_HOLIDAY, SECOND_PUBLIC_HOLIDAY));
        when(events.existsBySourceAndSourceUuid(eq("HOLIDAY_API"), eq(PUBLIC_HOLIDAY.uuid()))).thenReturn(true);
        when(events.existsBySourceAndSourceUuid(eq("HOLIDAY_API"), eq(SECOND_PUBLIC_HOLIDAY.uuid()))).thenReturn(false);

        HolidayImportSummary summary = holidays.importSelections(
                admin(),
                YEAR,
                List.of(
                        new HolidaySelection(PUBLIC_HOLIDAY.uuid(), true),
                        new HolidaySelection(SECOND_PUBLIC_HOLIDAY.uuid(), false)));

        assertThat(summary.imported()).isEqualTo(1);
        assertThat(summary.skipped()).isEqualTo(1);
        verify(events, times(1)).saveAndFlush(any(GlobalCalendarEventEntity.class));
    }

    @Test
    void importUsesTheExplicitAdminDayOffChoiceRatherThanThePublicMarker() {
        when(client.fetchVnHolidays(YEAR)).thenReturn(List.of(PUBLIC_HOLIDAY, NON_PUBLIC_OBSERVANCE));
        when(events.existsBySourceAndSourceUuid(eq("HOLIDAY_API"), any())).thenReturn(false);

        HolidayImportSummary summary = holidays.importSelections(
                admin(),
                YEAR,
                List.of(
                        new HolidaySelection(PUBLIC_HOLIDAY.uuid(), false),
                        new HolidaySelection(NON_PUBLIC_OBSERVANCE.uuid(), true)));

        assertThat(summary.imported()).isEqualTo(2);
        assertThat(summary.skipped()).isZero();
        verify(events, times(2)).saveAndFlush(any(GlobalCalendarEventEntity.class));
    }

    @Test
    void importRejectsUnknownCandidateWithoutSavingAnything() {
        when(client.fetchVnHolidays(YEAR)).thenReturn(List.of(PUBLIC_HOLIDAY));

        assertThatThrownBy(() -> holidays.importSelections(
                        admin(), YEAR, List.of(new HolidaySelection("missing-uuid", true))))
                .isInstanceOf(HolidayCalendarException.class)
                .hasMessageContaining("missing-uuid");
        verify(events, never()).saveAndFlush(any());
    }

    @Test
    void importRejectsNonAdmin() {
        assertThatThrownBy(() -> holidays.importSelections(
                        intern(), YEAR, List.of(new HolidaySelection(PUBLIC_HOLIDAY.uuid(), true))))
                .isInstanceOf(AccessDeniedException.class);
        verify(client, never()).fetchVnHolidays(any(int.class));
    }

    @Test
    void importPropagatesUnavailableClientWithoutSavingAnything() {
        when(client.fetchVnHolidays(YEAR))
                .thenThrow(new HolidayCalendarException("HolidayAPI is currently unavailable"));

        assertThatThrownBy(() -> holidays.importSelections(
                        admin(), YEAR, List.of(new HolidaySelection(PUBLIC_HOLIDAY.uuid(), true))))
                .isInstanceOf(HolidayCalendarException.class)
                .hasMessageContaining("unavailable");
        verify(events, never()).saveAndFlush(any());
    }

    private static AttendanceActor admin() {
        return new AttendanceActor(ADMIN_ID, AttendanceRole.ADMIN);
    }

    private static AttendanceActor intern() {
        return new AttendanceActor(ADMIN_ID + 1, AttendanceRole.INTERN);
    }
}