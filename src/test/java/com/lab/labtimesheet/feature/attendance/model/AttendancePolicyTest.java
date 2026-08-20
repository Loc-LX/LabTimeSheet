package com.lab.labtimesheet.feature.attendance.model;

import com.lab.labtimesheet.feature.attendance.service.AttendancePolicyTimeline;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AttendancePolicyTest {

    @Test
    void resolvesSeedPolicyForHistoricalAndCurrentDates() {
        AttendancePolicy seeded = AttendancePolicyFixtures.seeded(1L);
        AttendancePolicyTimeline timeline = new AttendancePolicyTimeline(Set.of(seeded));

        assertEquals(seeded, timeline.resolve(LocalDate.of(1970, 1, 1)));
        assertEquals(seeded, timeline.resolve(LocalDate.of(2026, 8, 14)));
        assertEquals(ZoneId.of("Asia/Ho_Chi_Minh"), seeded.zoneId());
        assertEquals(LocalTime.of(8, 30), seeded.scheduledStart());
        assertEquals(LocalTime.of(15, 30), seeded.scheduledEnd());
        assertEquals(30, seeded.checkInGraceMinutes());
        assertEquals(30, seeded.checkoutGraceMinutes());
        assertEquals(3, seeded.monthlyLeaveQuota());
        assertEquals(new BigDecimal("0.25"), seeded.violationPenalty());
        assertEquals(
                Set.of(
                        DayOfWeek.MONDAY,
                        DayOfWeek.TUESDAY,
                        DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY,
                        DayOfWeek.FRIDAY),
                seeded.workdays());
    }

    @Test
    void rejectsGraceOutsideZeroThroughSevenHundredTwenty() {
        assertThrows(IllegalArgumentException.class, () -> policy(-1, 30, LocalTime.of(15, 30)));
        assertThrows(IllegalArgumentException.class, () -> policy(30, 721, LocalTime.of(15, 30)));
    }

    @Test
    void rejectsCheckoutCutoffAtLocalMidnight() {
        assertThrows(IllegalArgumentException.class, () -> policy(30, 30, LocalTime.of(23, 30)));
    }

    @Test
    void rejectsMonthlyLeaveQuotaOutsideZeroThroughThirtyOne() {
        assertThrows(
                IllegalArgumentException.class,
                () -> policy(30, 30, LocalTime.of(15, 30), -1, new BigDecimal("0.25"), Set.of(DayOfWeek.MONDAY)));
        assertThrows(
                IllegalArgumentException.class,
                () -> policy(30, 30, LocalTime.of(15, 30), 32, new BigDecimal("0.25"), Set.of(DayOfWeek.MONDAY)));
    }

    @Test
    void rejectsViolationPenaltyOutsideZeroToOne() {
        assertThrows(
                IllegalArgumentException.class,
                () -> policy(30, 30, LocalTime.of(15, 30), 3, new BigDecimal("-0.01"), Set.of(DayOfWeek.MONDAY)));
        assertThrows(
                IllegalArgumentException.class,
                () -> policy(30, 30, LocalTime.of(15, 30), 3, new BigDecimal("1.01"), Set.of(DayOfWeek.MONDAY)));
    }

    @Test
    void rejectsEmptyWorkdays() {
        assertThrows(
                IllegalArgumentException.class,
                () -> policy(30, 30, LocalTime.of(15, 30), 3, new BigDecimal("0.25"), Set.of()));
    }

    private static AttendancePolicy policy(
            int checkInGraceMinutes, int checkoutGraceMinutes, LocalTime scheduledEnd) {
        return policy(
                checkInGraceMinutes, checkoutGraceMinutes, scheduledEnd, 3, new BigDecimal("0.25"), Set.of(DayOfWeek.MONDAY));
    }

    private static AttendancePolicy policy(
            int checkInGraceMinutes,
            int checkoutGraceMinutes,
            LocalTime scheduledEnd,
            int monthlyLeaveQuota,
            BigDecimal violationPenalty,
            Set<DayOfWeek> workdays) {
        return new AttendancePolicy(
                2L,
                LocalDate.of(2026, 9, 1),
                ZoneId.of("Asia/Ho_Chi_Minh"),
                LocalTime.of(8, 30),
                scheduledEnd,
                checkInGraceMinutes,
                checkoutGraceMinutes,
                monthlyLeaveQuota,
                violationPenalty,
                workdays);
    }
}
