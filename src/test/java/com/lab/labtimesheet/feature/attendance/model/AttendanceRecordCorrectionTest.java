package com.lab.labtimesheet.feature.attendance.model;

import com.lab.labtimesheet.feature.calendar.model.AttendancePolicy;
import com.lab.labtimesheet.feature.calendar.model.AttendancePolicyFixtures;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class AttendanceRecordCorrectionTest {

    @Test
    void effectiveCheckoutRemovesMissingFlagWithoutChangingRawPunch() {
        AttendancePolicy policy = AttendancePolicyFixtures.seeded(1L);
        AttendanceRecord record = new AttendanceRecord(
                42L,
                LocalDate.of(2026, 8, 14),
                policy,
                Instant.parse("2026-08-14T02:00:00Z"),
                null);

        Instant cutoff = Instant.parse("2026-08-14T09:00:00Z");
        assertThat(record.violations(cutoff, null).missingCheckout()).isFalse();
        assertThat(record.violations(cutoff.plusSeconds(1), null).missingCheckout()).isTrue();
        assertThat(record.violations(cutoff.plusSeconds(1), Instant.parse("2026-08-14T07:00:00Z")))
                .extracting(AttendanceViolations::missingCheckout)
                .isEqualTo(false);
        assertThat(record.checkOutAt()).isNull();
    }
}
