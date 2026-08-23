package com.lab.labtimesheet.feature.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceCorrectionEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.LeaveRequestEntity;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceCorrectionRepository;
import com.lab.labtimesheet.feature.attendance.repository.LeaveRequestRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;

class AttendanceDeadlineServiceTest {

    private static final long REQUEST_ID = 11L;
    private static final long INTERN_ID = 7L;
    private static final Instant BOUNDARY = Instant.parse("2026-09-01T01:30:00Z");

    private final LeaveRequestRepository leaveRequests = mock(LeaveRequestRepository.class);
    private final AttendanceCorrectionRepository corrections = mock(AttendanceCorrectionRepository.class);
    private final CorrectionWindowGuard windowGuard = mock(CorrectionWindowGuard.class);
    private final AttendanceNotificationClient notifications = mock(AttendanceNotificationClient.class);

    private LeaveRequestEntity request;

    @BeforeEach
    void setUp() {
        request = mock(LeaveRequestEntity.class);
        when(request.id()).thenReturn(REQUEST_ID);
        when(request.internUserId()).thenReturn(INTERN_ID);
        when(request.status()).thenReturn("PENDING");
        when(request.firstCountedStartAt()).thenReturn(BOUNDARY);
        when(leaveRequests.findById(REQUEST_ID)).thenReturn(Optional.of(request));
    }

    @Test
    void expiresPendingRequestAtBoundaryPersistingAndNotifyingOnce() {
        Instant now = Instant.parse("2026-09-01T01:30:00Z");

        assertThat(deadlineServiceAt(now).expireLeave(REQUEST_ID)).isTrue();
        verify(request).autoReject(now);
        verify(leaveRequests).saveAndFlush(request);
        verify(notifications).leaveAutoRejected(INTERN_ID, REQUEST_ID, now);
    }

    @Test
    void leavesPendingRequestUntouchedBeforeBoundary() {
        Instant now = Instant.parse("2026-09-01T01:29:59Z");

        assertThat(deadlineServiceAt(now).expireLeave(REQUEST_ID)).isFalse();
        verify(request, never()).autoReject(any());
        verify(leaveRequests, never()).saveAndFlush(any());
        verify(notifications, never()).leaveAutoRejected(anyLong(), anyLong(), any());
    }

    @Test
    void leavesResolvedRequestUntouchedWithoutNotifying() {
        when(request.status()).thenReturn("REJECTED");

        assertThat(deadlineServiceAt(Instant.parse("2026-09-01T01:30:00Z")).expireLeave(REQUEST_ID)).isFalse();
        verify(request, never()).autoReject(any());
        verify(leaveRequests, never()).saveAndFlush(any());
        verify(notifications, never()).leaveAutoRejected(anyLong(), anyLong(), any());
    }

    @Test
    void leavesMissingRequestUntouched() {
        when(leaveRequests.findById(REQUEST_ID)).thenReturn(Optional.empty());

        assertThat(deadlineServiceAt(Instant.parse("2026-09-01T01:30:00Z")).expireLeave(REQUEST_ID)).isFalse();
        verify(notifications, never()).leaveAutoRejected(anyLong(), anyLong(), any());
    }

    @Test
    void boundedLeaveBatchTransitionsOnlyExpiredPendingRequests() {
        when(leaveRequests.findByStatusAndFirstCountedStartAtLessThanEqualOrderByIdAsc(
                        eq("PENDING"), eq(BOUNDARY), any(PageRequest.class)))
                .thenReturn(List.of(request));
        when(request.firstCountedStartAt()).thenReturn(BOUNDARY);

        int expired = deadlineServiceAt(BOUNDARY).expirePendingLeaves(100);

        assertThat(expired).isEqualTo(1);
        verify(request).autoReject(BOUNDARY);
        verify(leaveRequests).saveAndFlush(request);
        verify(notifications).leaveAutoRejected(INTERN_ID, REQUEST_ID, BOUNDARY);
    }

    @Test
    void boundedCorrectionBatchDelegatesIdempotentExpiry() {
        AttendanceCorrectionEntity correction = mock(AttendanceCorrectionEntity.class);
        when(correction.id()).thenReturn(99L);
        when(corrections.findExpiredByDecisionDeadline(eq(BOUNDARY), any(PageRequest.class)))
                .thenReturn(List.of(correction));
        when(windowGuard.expireNow(correction)).thenReturn(true);

        int expired = deadlineServiceAt(BOUNDARY).expireCorrections(100);

        assertThat(expired).isEqualTo(1);
        verify(windowGuard).expireNow(correction);
    }

    @Test
    void boundedCorrectionBatchSkipsRowsTheGuardAlreadyClosed() {
        AttendanceCorrectionEntity correction = mock(AttendanceCorrectionEntity.class);
        when(correction.id()).thenReturn(99L);
        when(corrections.findExpiredByDecisionDeadline(eq(BOUNDARY), any(PageRequest.class)))
                .thenReturn(List.of(correction));
        when(windowGuard.expireNow(correction)).thenReturn(false);

        int expired = deadlineServiceAt(BOUNDARY).expireCorrections(100);

        assertThat(expired).isZero();
        verify(windowGuard).expireNow(correction);
    }

    private AttendanceDeadlineService deadlineServiceAt(Instant now) {
        return new AttendanceDeadlineService(
                Clock.fixed(now, ZoneOffset.UTC),
                leaveRequests,
                corrections,
                windowGuard,
                notifications);
    }
}