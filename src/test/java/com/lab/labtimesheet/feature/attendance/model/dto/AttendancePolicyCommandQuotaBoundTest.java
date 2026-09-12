package com.lab.labtimesheet.feature.attendance.model.dto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Protects {@code ATT-003}, the configurable bound on the monthly leave quota.
 *
 * <p>Observable break: an Admin can save a policy version whose monthly leave quota lets an
 * Intern be absent for more than a fifth of the month. That is not merely generous. {@code
 * ATT-014} computes the attendance rate over eligible workdays minus approved-leave workdays,
 * so a quota near the length of the month can drive that denominator to zero and render every
 * compliance figure {@code N/A} with no error raised anywhere.
 *
 * <p>Expected values are derived from {@code ATT-003}, not from the implementation. It permits
 * an integer from 0 through 4, four being the largest whole number within a fifth of the
 * shortest twenty-workday month. So 0 and 4 are accepted, and 5 and -1 are refused.
 */
class AttendancePolicyCommandQuotaBoundTest {

    @Test
    void acceptsTheInclusiveQuotaBounds() {
        assertThat(commandWithQuota(0).monthlyLeaveQuota()).isZero();
        assertThat(commandWithQuota(4).monthlyLeaveQuota()).isEqualTo(4);
    }

    @Test
    void refusesAQuotaAboveOneFifthOfTheShortestMonth() {
        assertThatThrownBy(() -> commandWithQuota(5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("monthlyLeaveQuota");
    }

    @Test
    void refusesANegativeQuota() {
        assertThat(catchThrowable(() -> commandWithQuota(-1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static AttendancePolicyCommand commandWithQuota(int monthlyLeaveQuota) {
        return new AttendancePolicyCommand(
                LocalDate.of(2026, 10, 1),
                ZoneId.of("Asia/Ho_Chi_Minh"),
                LocalTime.of(8, 30),
                LocalTime.of(15, 30),
                30,
                30,
                monthlyLeaveQuota,
                new BigDecimal("0.2500"),
                Set.of(
                        DayOfWeek.MONDAY,
                        DayOfWeek.TUESDAY,
                        DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY,
                        DayOfWeek.FRIDAY));
    }
}
