package com.lab.labtimesheet.feature.attendance.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.lab.labtimesheet.feature.attendance.exception.PolicyException;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.AttendancePolicy;
import com.lab.labtimesheet.feature.attendance.model.AttendancePolicyFixtures;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRole;
import com.lab.labtimesheet.feature.attendance.model.dto.AttendancePolicyCommand;
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
import java.util.Set;
import org.junit.jupiter.api.Test;

class AttendancePolicyApplicationServiceTest {

    @Test
    void rejectsEffectivePolicyOutsideTheNextOrLaterFutureMonth() {
        AttendancePolicyEntity seed = mock(AttendancePolicyEntity.class);
        when(seed.toDomain()).thenReturn(AttendancePolicyFixtures.seeded(1L));
        AttendancePolicyRepository policies = mock(AttendancePolicyRepository.class);
        when(policies.findAllByOrderByEffectiveFromAsc()).thenReturn(List.of(seed));

        AttendancePolicyApplicationService service = new AttendancePolicyApplicationService(
                Clock.fixed(Instant.parse("2026-08-20T03:00:00Z"), ZoneOffset.UTC), policies);

        assertThatThrownBy(() -> service.schedule(
                        new AttendanceActor(9L, AttendanceRole.ADMIN),
                        new AttendancePolicyCommand(
                                LocalDate.of(2026, 8, 31),
                                ZoneId.of("Asia/Ho_Chi_Minh"),
                                LocalTime.of(8, 30),
                                LocalTime.of(15, 30),
                                30,
                                30,
                                3,
                                new BigDecimal("0.25"),
                                Set.of(DayOfWeek.MONDAY))))
                .isInstanceOf(PolicyException.class);
    }
}
