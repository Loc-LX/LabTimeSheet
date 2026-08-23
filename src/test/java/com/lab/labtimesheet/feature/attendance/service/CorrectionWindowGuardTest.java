package com.lab.labtimesheet.feature.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lab.labtimesheet.feature.attendance.model.AttendanceRecord;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceCorrectionEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceCorrectionEventEntity;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendanceRecordEntity;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceCorrectionEventRepository;
import com.lab.labtimesheet.feature.attendance.repository.AttendanceCorrectionRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class CorrectionWindowGuardTest {

    private static final long CORRECTION_ID = 88L;
    private static final long INTERN_ID = 7L;
    private static final Instant DECISION_DEADLINE = Instant.parse("2026-08-15T09:00:01Z");

    private final AttendanceCorrectionRepository corrections = mock(AttendanceCorrectionRepository.class);
    private final AttendanceCorrectionEventRepository events = mock(AttendanceCorrectionEventRepository.class);
    private final AttendanceNotificationClient notifications = mock(AttendanceNotificationClient.class);

    private AttendanceCorrectionEntity correction;

    @BeforeEach
    void setUp() {
        correction = mock(AttendanceCorrectionEntity.class);
        when(correction.id()).thenReturn(CORRECTION_ID);
        when(correction.decisionDeadline()).thenReturn(DECISION_DEADLINE);
        when(correction.lockedAt()).thenReturn(null);
        AttendanceRecordEntity record = mock(AttendanceRecordEntity.class);
        AttendanceRecord domain = mock(AttendanceRecord.class);
        when(domain.internId()).thenReturn(INTERN_ID);
        when(record.toDomain()).thenReturn(domain);
        when(correction.attendanceRecord()).thenReturn(record);
        when(corrections.findById(CORRECTION_ID)).thenReturn(Optional.of(correction));
    }

    @Test
    void expiresPendingCorrectionAfterDeadlineByAutoRejectingAndLocking() {
        Instant now = Instant.parse("2026-08-15T09:00:01.001Z");
        when(correction.status()).thenReturn("PENDING");

        assertThat(guardAt(now).expire(CORRECTION_ID)).isTrue();

        verify(corrections).saveAndFlush(correction);
        ArgumentCaptor<AttendanceCorrectionEventEntity> eventCaptor =
                ArgumentCaptor.forClass(AttendanceCorrectionEventEntity.class);
        verify(events).save(eventCaptor.capture());
        AttendanceCorrectionEventEntity event = eventCaptor.getValue();
        assertThat(event.eventType()).isEqualTo("AUTO_REJECTED");
        assertThat(event.fromStatus()).isEqualTo("PENDING");
        assertThat(event.toStatus()).isEqualTo("REJECTED");
        assertThat(event.actorUserId()).isNull();
        assertThat(event.correctionId()).isEqualTo(CORRECTION_ID);
        assertThat(event.occurredAt()).isEqualTo(now);
        verify(notifications).correctionAutoRejected(INTERN_ID, CORRECTION_ID, now);
    }

    @Test
    void expiresDecidedCorrectionAfterDeadlineByLockingWithoutChangingStatus() {
        Instant now = Instant.parse("2026-08-15T09:00:01.001Z");
        when(correction.status()).thenReturn("APPROVED");

        assertThat(guardAt(now).expire(CORRECTION_ID)).isTrue();

        verify(corrections).saveAndFlush(correction);
        ArgumentCaptor<AttendanceCorrectionEventEntity> eventCaptor =
                ArgumentCaptor.forClass(AttendanceCorrectionEventEntity.class);
        verify(events).save(eventCaptor.capture());
        AttendanceCorrectionEventEntity event = eventCaptor.getValue();
        assertThat(event.eventType()).isEqualTo("LOCKED");
        assertThat(event.fromStatus()).isEqualTo("APPROVED");
        assertThat(event.toStatus()).isEqualTo("APPROVED");
        assertThat(event.actorUserId()).isNull();
        assertThat(event.correctionId()).isEqualTo(CORRECTION_ID);
        assertThat(event.occurredAt()).isEqualTo(now);
        verify(notifications).correctionLocked(INTERN_ID, CORRECTION_ID, now);
    }

    @Test
    void leavesCorrectionUntouchedAtInclusiveDeadline() {
        assertThat(guardAt(Instant.parse("2026-08-15T09:00:01Z")).expire(CORRECTION_ID)).isFalse();

        verify(corrections, never()).saveAndFlush(any());
        verify(events, never()).save(any());
        verify(notifications, never()).correctionAutoRejected(anyLong(), anyLong(), any());
        verify(notifications, never()).correctionLocked(anyLong(), anyLong(), any());
    }

    @Test
    void leavesCorrectionUntouchedInsideWindow() {
        assertThat(guardAt(Instant.parse("2026-08-15T08:00:00Z")).expire(CORRECTION_ID)).isFalse();

        verify(corrections, never()).saveAndFlush(any());
        verify(events, never()).save(any());
        verify(notifications, never()).correctionAutoRejected(anyLong(), anyLong(), any());
        verify(notifications, never()).correctionLocked(anyLong(), anyLong(), any());
    }

    @Test
    void leavesAlreadyLockedCorrectionUntouched() {
        when(correction.lockedAt()).thenReturn(Instant.parse("2026-08-15T09:00:01.001Z"));

        assertThat(guardAt(Instant.parse("2026-08-15T09:00:01.002Z")).expire(CORRECTION_ID)).isFalse();

        verify(corrections, never()).saveAndFlush(any());
        verify(events, never()).save(any());
        verify(notifications, never()).correctionAutoRejected(anyLong(), anyLong(), any());
        verify(notifications, never()).correctionLocked(anyLong(), anyLong(), any());
    }

    @Test
    void leavesMissingCorrectionUntouched() {
        when(corrections.findById(CORRECTION_ID)).thenReturn(Optional.empty());

        assertThat(guardAt(Instant.parse("2026-08-15T09:00:01.001Z")).expire(CORRECTION_ID)).isFalse();

        verify(corrections, never()).saveAndFlush(any());
        verify(events, never()).save(any());
        verify(notifications, never()).correctionAutoRejected(anyLong(), anyLong(), any());
        verify(notifications, never()).correctionLocked(anyLong(), anyLong(), any());
    }

    private CorrectionWindowGuard guardAt(Instant now) {
        return new CorrectionWindowGuard(
                Clock.fixed(now, ZoneOffset.UTC),
                corrections,
                events,
                notifications);
    }
}