package com.lab.labtimesheet.feature.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRole;
import com.lab.labtimesheet.feature.attendance.model.dto.CalendarImportSelection;
import com.lab.labtimesheet.feature.attendance.model.dto.HolidayCandidate;
import com.lab.labtimesheet.feature.attendance.model.entity.GlobalCalendarEventEntity;
import com.lab.labtimesheet.feature.attendance.repository.GlobalCalendarEventRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CalendarImportServiceTest {

    @Test
    void publicHolidayOnlySetsTheDefaultAndRepeatedImportDoesNotOverwrite() {
        GlobalCalendarEventRepository events = mock(GlobalCalendarEventRepository.class);
        CalendarApplicationService service = new CalendarApplicationService(
                Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), ZoneOffset.UTC),
                mock(com.lab.labtimesheet.feature.attendance.repository.AttendancePolicyRepository.class),
                events);
        HolidayCandidate candidate = new HolidayCandidate(
                "vn-1",
                "Observed holiday",
                LocalDate.of(2026, 9, 2),
                LocalDate.of(2026, 9, 3),
                true,
                Instant.parse("2026-08-20T00:00:00Z"));

        assertThat(service.preview(new AttendanceActor(1L, AttendanceRole.ADMIN), List.of(candidate)))
                .singleElement()
                .satisfies(item -> assertThat(item.selectedByDefault()).isTrue());
        assertThat(service.preview(new AttendanceActor(1L, AttendanceRole.ADMIN), List.of(candidate, candidate)))
                .hasSize(1);

        GlobalCalendarEventEntity existing = mock(GlobalCalendarEventEntity.class);
        when(events.findBySourceAndSourceUuid("HOLIDAY_API", "vn-1"))
                .thenReturn(Optional.of(existing));

        assertThat(service.importSelected(
                        new AttendanceActor(1L, AttendanceRole.ADMIN),
                        List.of(new CalendarImportSelection(candidate, false))))
                .isEmpty();
    }
}
