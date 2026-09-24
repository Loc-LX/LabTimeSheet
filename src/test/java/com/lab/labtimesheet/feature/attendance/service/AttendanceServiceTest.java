package com.lab.labtimesheet.feature.attendance.service;

import static com.lab.labtimesheet.feature.attendance.exception.AttendanceRejection.ALREADY_CHECKED_IN;
import static com.lab.labtimesheet.feature.attendance.exception.AttendanceRejection.ALREADY_CHECKED_OUT;
import static com.lab.labtimesheet.feature.attendance.exception.AttendanceRejection.APPROVED_LEAVE;
import static com.lab.labtimesheet.feature.attendance.exception.AttendanceRejection.CHECKOUT_CUTOFF_PASSED;
import static com.lab.labtimesheet.feature.attendance.exception.AttendanceRejection.GLOBAL_DAY_OFF;
import static com.lab.labtimesheet.feature.attendance.exception.AttendanceRejection.INACTIVE_INTERN;
import static com.lab.labtimesheet.feature.attendance.exception.AttendanceRejection.NON_WORKDAY;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lab.labtimesheet.feature.attendance.exception.AttendanceException;
import com.lab.labtimesheet.feature.attendance.exception.AttendanceRejection;
import com.lab.labtimesheet.feature.attendance.model.AttendanceDayContext;
import com.lab.labtimesheet.feature.calendar.model.AttendancePolicy;
import com.lab.labtimesheet.feature.calendar.model.AttendancePolicyFixtures;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRecord;
import com.lab.labtimesheet.feature.attendance.model.AttendanceViolations;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class AttendanceServiceTest {

    private static final long INTERN_ID = 42L;
    private static final LocalDate WORKDAY = LocalDate.of(2026, 8, 14);

    @Test
    void exactCheckInGraceBoundaryIsOnTimeAndFirstLaterInstantIsLate() {
        AttendanceRecord exactBoundary = checkInAt("2026-08-14T02:00:00Z", activeDay(), seededPolicy());
        AttendanceRecord firstLater = checkInAt("2026-08-14T02:00:00.001Z", activeDay(), seededPolicy());

        assertFalse(exactBoundary.violations(at("2026-08-14T02:00:00Z")).late());
        assertTrue(firstLater.violations(at("2026-08-14T02:00:00.001Z")).late());
        assertEquals(WORKDAY, exactBoundary.workDate());
        assertEquals(1L, exactBoundary.policy().id());
    }

    @Test
    void rejectsIneligibleAndDuplicateCheckIns() {
        assertCheckInRejected(INACTIVE_INTERN, new AttendanceDayContext(false, false, false));
        assertCheckInRejected(GLOBAL_DAY_OFF, new AttendanceDayContext(true, true, false));
        assertCheckInRejected(APPROVED_LEAVE, new AttendanceDayContext(true, false, true));

        AttendancePolicy weekendOnly = policy(30, Set.of(DayOfWeek.SATURDAY));
        assertCheckInRejected(NON_WORKDAY, activeDay(), weekendOnly);

        AttendanceService service = new AttendanceService();
        AttendanceRecord existing = checkInAt("2026-08-14T01:30:00Z", activeDay(), seededPolicy());

        AttendanceException exception = assertThrows(
                AttendanceException.class,
                () -> service.checkIn(
                        INTERN_ID,
                        at("2026-08-14T01:30:00Z"),
                        seededPolicy(),
                        activeDay(),
                        Optional.of(existing)));
        assertEquals(ALREADY_CHECKED_IN, exception.rejection());
    }

    @Test
    void checkoutIsInclusiveAtCutoffAndCannotBeOverwritten() {
        AttendanceService service = new AttendanceService();
        AttendanceRecord checkedIn = checkedInRecord(seededPolicy());

        AttendanceRecord checkedOut = service.checkOut(
                Optional.of(checkedIn), at("2026-08-14T09:00:00Z"));

        assertEquals(at("2026-08-14T09:00:00Z"), checkedOut.checkOutAt());
        AttendanceException repeated = assertThrows(
                AttendanceException.class,
                () -> service.checkOut(Optional.of(checkedOut), at("2026-08-14T09:00:00.001Z")));
        assertEquals(ALREADY_CHECKED_OUT, repeated.rejection());
        assertEquals(at("2026-08-14T09:00:00Z"), checkedOut.checkOutAt());
    }

    @Test
    void firstInstantAfterCheckoutCutoffIsRejectedWithoutRawCheckout() {
        AttendanceRecord checkedIn = checkedInRecord(seededPolicy());
        AttendanceService service = new AttendanceService();

        AttendanceException exception = assertThrows(
                AttendanceException.class,
                () -> service.checkOut(Optional.of(checkedIn), at("2026-08-14T09:00:00.001Z")));

        assertEquals(CHECKOUT_CUTOFF_PASSED, exception.rejection());
        assertNull(checkedIn.checkOutAt());
        AttendanceViolations violations = checkedIn.violations(at("2026-08-14T09:00:00.001Z"));
        assertTrue(violations.missingCheckout());
        assertFalse(violations.earlyDeparture());
    }

    @Test
    void zeroGraceCheckoutUsesScheduledEndAsInclusiveCutoff() {
        AttendancePolicy zeroGrace = policy(
                0,
                Set.of(
                        DayOfWeek.MONDAY,
                        DayOfWeek.TUESDAY,
                        DayOfWeek.WEDNESDAY,
                        DayOfWeek.THURSDAY,
                        DayOfWeek.FRIDAY));
        AttendanceService service = new AttendanceService();
        AttendanceRecord checkedIn = checkedInRecord(zeroGrace);

        AttendanceRecord checkedOut = service.checkOut(
                Optional.of(checkedIn), at("2026-08-14T08:30:00Z"));

        assertEquals(at("2026-08-14T08:30:00Z"), checkedOut.checkOutAt());

        AttendanceRecord lateRecord = checkedInRecord(zeroGrace);
        AttendanceException exception = assertThrows(
                AttendanceException.class,
                () -> service.checkOut(Optional.of(lateRecord), at("2026-08-14T08:30:00.001Z")));
        assertEquals(CHECKOUT_CUTOFF_PASSED, exception.rejection());
        assertNull(lateRecord.checkOutAt());
    }

    private static AttendanceRecord checkInAt(
            String instant, AttendanceDayContext context, AttendancePolicy policy) {
        return new AttendanceService().checkIn(
                INTERN_ID, at(instant), policy, context, Optional.empty());
    }

    private static void assertCheckInRejected(AttendanceRejection rejection, AttendanceDayContext context) {
        assertCheckInRejected(rejection, context, seededPolicy());
    }

    private static void assertCheckInRejected(
            AttendanceRejection rejection, AttendanceDayContext context, AttendancePolicy policy) {
        AttendanceException exception = assertThrows(
                AttendanceException.class,
                () -> new AttendanceService().checkIn(
                        INTERN_ID,
                        at("2026-08-14T01:30:00Z"),
                        policy,
                        context,
                        Optional.empty()));
        assertEquals(rejection, exception.rejection());
    }

    private static AttendanceRecord checkedInRecord(AttendancePolicy policy) {
        return checkInAt("2026-08-14T01:30:00Z", activeDay(), policy);
    }

    private static AttendanceDayContext activeDay() {
        return new AttendanceDayContext(true, false, false);
    }

    private static AttendancePolicy seededPolicy() {
        return AttendancePolicyFixtures.seeded(1L);
    }

    private static AttendancePolicy policy(int checkoutGraceMinutes, Set<DayOfWeek> workdays) {
        return new AttendancePolicy(
                2L,
                LocalDate.of(1970, 1, 1),
                ZoneId.of("Asia/Ho_Chi_Minh"),
                LocalTime.of(8, 30),
                LocalTime.of(15, 30),
                30,
                checkoutGraceMinutes,
                3,
                new BigDecimal("0.25"),
                workdays);
    }

    private static Instant at(String instant) {
        return Instant.parse(instant);
    }

}
