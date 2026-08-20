package com.lab.labtimesheet.feature.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRole;
import com.lab.labtimesheet.feature.attendance.model.LeaveStatus;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveRequestCommand;
import com.lab.labtimesheet.feature.attendance.model.entity.LeaveRequestEntity;
import com.lab.labtimesheet.feature.attendance.repository.AttendancePolicyRepository;
import com.lab.labtimesheet.feature.attendance.repository.LeaveRequestDayRepository;
import com.lab.labtimesheet.feature.attendance.repository.LeaveRequestRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.data.domain.Pageable;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;

class LeaveApplicationServiceTest {

    @Test
    void rejectsBlankReasonBeforeReadingOrWritingLeaveState() {
        LeaveApplicationService service = new LeaveApplicationService(
                Clock.fixed(Instant.parse("2026-08-20T00:00:00Z"), ZoneOffset.UTC),
                mock(AttendancePolicyRepository.class),
                mock(LeaveRequestRepository.class),
                mock(LeaveRequestDayRepository.class),
                mock(AccountService.class),
                mock(CalendarApplicationService.class),
                mock(TransactionTemplate.class));

        assertThatThrownBy(() -> service.submit(
                        new AttendanceActor(42L, AttendanceRole.INTERN),
                        new LeaveRequestCommand(
                                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 1), "  ")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void expirySamplesClockAfterLockedRowAcquisition() {
        Instant beforeLock = Instant.parse("2026-08-14T00:59:59Z");
        Instant afterLock = Instant.parse("2026-08-14T01:00:00Z");
        AtomicReference<Instant> now = new AtomicReference<>(beforeLock);
        Clock clock = mock(Clock.class);
        when(clock.instant()).thenAnswer(invocation -> now.get());

        LeaveRequestRepository requests = mock(LeaveRequestRepository.class);
        LeaveRequestEntity candidate = mock(LeaveRequestEntity.class);
        LeaveRequestEntity locked = mock(LeaveRequestEntity.class);
        when(candidate.id()).thenReturn(7L);
        when(locked.status()).thenReturn(LeaveStatus.PENDING);
        when(locked.firstCountedStartAt()).thenReturn(afterLock);
        when(requests.findByStatusAndFirstCountedStartAtLessThanEqualOrderByIdAsc(
                        eq(LeaveStatus.PENDING.name()), eq(beforeLock), any(Pageable.class)))
                .thenReturn(List.of(candidate));
        when(requests.findForUpdateById(7L)).thenAnswer(invocation -> {
            now.set(afterLock);
            return Optional.of(locked);
        });

        LeaveApplicationService service = new LeaveApplicationService(
                clock,
                mock(AttendancePolicyRepository.class),
                requests,
                mock(LeaveRequestDayRepository.class),
                mock(AccountService.class),
                mock(CalendarApplicationService.class),
                mock(TransactionTemplate.class));

        assertThat(service.expirePending(1)).isEqualTo(1);
    }
}
