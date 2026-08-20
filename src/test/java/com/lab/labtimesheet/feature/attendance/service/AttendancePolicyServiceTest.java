package com.lab.labtimesheet.feature.attendance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lab.labtimesheet.feature.attendance.exception.PolicyException;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.AttendancePolicy;
import com.lab.labtimesheet.feature.attendance.model.AttendancePolicyFixtures;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRole;
import com.lab.labtimesheet.feature.attendance.model.dto.SchedulePolicyCommand;
import com.lab.labtimesheet.feature.attendance.model.entity.AttendancePolicyEntity;
import com.lab.labtimesheet.feature.attendance.repository.AttendancePolicyRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

class AttendancePolicyServiceTest {

    private static final Clock NOW = Clock.fixed(Instant.parse("2026-08-14T02:00:00Z"), ZoneOffset.UTC);
    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
    private static final Set<DayOfWeek> WORKDAYS = Set.of(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY);

    private final AttendancePolicyRepository policies = mock(AttendancePolicyRepository.class);
    private AttendancePolicyService service;

    @BeforeEach
    void setUp() {
        AttendancePolicyEntity seed = mock(AttendancePolicyEntity.class);
        when(seed.toDomain()).thenReturn(AttendancePolicyFixtures.seeded(1L));
        when(policies.findAllByOrderByEffectiveFromAsc()).thenReturn(List.of(seed));
        service = new AttendancePolicyService(NOW, policies);
    }

    @Test
    void schedulesFutureMonthVersionWithSeparateGraceAndConfiguredWorkdays() {
        AttendancePolicy expected = policy(7L, LocalDate.of(2026, 9, 1), 20, 0);
        AttendancePolicyEntity persisted = mock(AttendancePolicyEntity.class);
        when(persisted.toDomain()).thenReturn(expected);
        when(policies.saveAndFlush(any(AttendancePolicyEntity.class))).thenReturn(persisted);

        assertThat(service.scheduleVersion(
                        new AttendanceActor(1L, AttendanceRole.ADMIN),
                        command(LocalDate.of(2026, 9, 1), 20, 0)))
                .isEqualTo(expected);
        verify(policies).saveAndFlush(any(AttendancePolicyEntity.class));
    }

    @Test
    void rejectsNonFirstOfMonthEffectiveDate() {
        assertThatThrownBy(() -> service.scheduleVersion(
                        new AttendanceActor(1L, AttendanceRole.ADMIN),
                        command(LocalDate.of(2026, 9, 15), 20, 0)))
                .isInstanceOf(IllegalArgumentException.class);
        verify(policies, never()).saveAndFlush(any());
    }

    @Test
    void rejectsCurrentOrPastMonthEffectiveDates() {
        assertThatThrownBy(() -> service.scheduleVersion(
                        new AttendanceActor(1L, AttendanceRole.ADMIN),
                        command(LocalDate.of(2026, 8, 1), 20, 0)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.scheduleVersion(
                        new AttendanceActor(1L, AttendanceRole.ADMIN),
                        command(LocalDate.of(2026, 7, 1), 20, 0)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rejectsNonAdminScheduling() {
        assertThatThrownBy(() -> service.scheduleVersion(
                        new AttendanceActor(2L, AttendanceRole.MENTOR),
                        command(LocalDate.of(2026, 9, 1), 20, 0)))
                .isInstanceOf(AccessDeniedException.class);
        verify(policies, never()).saveAndFlush(any());
    }

    @Test
    void rejectsOutOfRangeGraceAndCutoffReachingMidnight() {
        assertThatThrownBy(() -> service.scheduleVersion(
                        new AttendanceActor(1L, AttendanceRole.ADMIN),
                        command(LocalDate.of(2026, 9, 1), -1, 30)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.scheduleVersion(
                        new AttendanceActor(1L, AttendanceRole.ADMIN),
                        new SchedulePolicyCommand(
                                LocalDate.of(2026, 9, 1),
                                ZONE,
                                LocalTime.of(8, 30),
                                LocalTime.of(23, 30),
                                30,
                                30,
                                3,
                                new BigDecimal("0.25"),
                                WORKDAYS)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void replaceAllowsNotYetEffectiveVersionButNotAnEffectiveOne() {
        AttendancePolicyEntity future = mock(AttendancePolicyEntity.class);
        when(future.toDomain()).thenReturn(policy(7L, LocalDate.of(2026, 9, 1), 20, 0));
        when(future.version()).thenReturn(3L);
        when(policies.findById(7L)).thenReturn(Optional.of(future));
        AttendancePolicyEntity persisted = mock(AttendancePolicyEntity.class);
        when(persisted.toDomain()).thenReturn(policy(7L, LocalDate.of(2026, 9, 1), 10, 0));
        when(policies.saveAndFlush(future)).thenReturn(persisted);

        assertThat(service.replaceVersion(
                        new AttendanceActor(1L, AttendanceRole.ADMIN),
                        7L,
                        3L,
                        command(LocalDate.of(2026, 9, 1), 10, 0)))
                .isEqualTo(policy(7L, LocalDate.of(2026, 9, 1), 10, 0));
        verify(future).update(any(AttendancePolicy.class));

        AttendancePolicyEntity seed = mock(AttendancePolicyEntity.class);
        when(seed.toDomain()).thenReturn(AttendancePolicyFixtures.seeded(1L));
        when(seed.version()).thenReturn(0L);
        when(policies.findById(1L)).thenReturn(Optional.of(seed));

        assertThatThrownBy(() -> service.replaceVersion(
                        new AttendanceActor(1L, AttendanceRole.ADMIN),
                        1L,
                        0L,
                        command(LocalDate.of(2026, 9, 1), 10, 0)))
                .isInstanceOf(PolicyException.class);
        verify(seed, never()).update(any());
    }

    @Test
    void replaceRejectsStaleOptimisticVersion() {
        AttendancePolicyEntity future = mock(AttendancePolicyEntity.class);
        when(future.toDomain()).thenReturn(policy(7L, LocalDate.of(2026, 9, 1), 20, 0));
        when(future.version()).thenReturn(3L);
        when(policies.findById(7L)).thenReturn(Optional.of(future));

        assertThatThrownBy(() -> service.replaceVersion(
                        new AttendanceActor(1L, AttendanceRole.ADMIN),
                        7L,
                        2L,
                        command(LocalDate.of(2026, 9, 1), 10, 0)))
                .isInstanceOf(PolicyException.class);
        verify(future, never()).update(any());
    }

    private static SchedulePolicyCommand command(LocalDate effectiveFrom, int checkInGrace, int checkoutGrace) {
        return new SchedulePolicyCommand(
                effectiveFrom,
                ZONE,
                LocalTime.of(8, 30),
                LocalTime.of(15, 30),
                checkInGrace,
                checkoutGrace,
                5,
                new BigDecimal("0.5"),
                WORKDAYS);
    }

    private static AttendancePolicy policy(long id, LocalDate effectiveFrom, int checkInGrace, int checkoutGrace) {
        return new AttendancePolicy(
                id,
                effectiveFrom,
                ZONE,
                LocalTime.of(8, 30),
                LocalTime.of(15, 30),
                checkInGrace,
                checkoutGrace,
                5,
                new BigDecimal("0.5"),
                WORKDAYS);
    }
}