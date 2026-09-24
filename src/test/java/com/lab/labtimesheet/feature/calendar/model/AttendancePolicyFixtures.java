package com.lab.labtimesheet.feature.calendar.model;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Set;

public final class AttendancePolicyFixtures {

    private AttendancePolicyFixtures() {}

    public static AttendancePolicy seeded(long id) {
        return new AttendancePolicy(
                id,
                LocalDate.of(1970, 1, 1),
                ZoneId.of("Asia/Ho_Chi_Minh"),
                LocalTime.of(8, 30),
                LocalTime.of(15, 30),
                30,
                30,
                3,
                new BigDecimal("0.25"),
                Set.of(
                        DayOfWeek.MONDAY,
                        DayOfWeek.TUESDAY,
                        DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY,
                        DayOfWeek.FRIDAY));
    }
}
