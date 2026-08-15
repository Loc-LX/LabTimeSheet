package com.lab.labtimesheet.feature.attendance.service;

import com.lab.labtimesheet.feature.attendance.exception.AttendanceException;
import com.lab.labtimesheet.feature.attendance.exception.AttendanceRejection;
import com.lab.labtimesheet.feature.attendance.model.AttendanceDayContext;
import com.lab.labtimesheet.feature.attendance.model.AttendancePolicy;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRecord;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Pure attendance punch rules over immutable policy, date context, and raw record values.
 */
@Service
@NoArgsConstructor(access = AccessLevel.PACKAGE)
public final class AttendanceService {

    /**
     * Creates the sole raw check-in for an eligible Intern/date using the supplied server instant.
     * Equality at the grace boundary is accepted; violation classification remains attached-policy based.
     *
     * @param internId Intern account identifier
     * @param now authoritative server instant
     * @param policy effective policy at check-in
     * @param context date-specific account, calendar, and frozen-leave facts
     * @param existingRecord existing row for the same Intern/date, if any
     * @return new raw attendance record
     */
    public AttendanceRecord checkIn(
            long internId,
            Instant now,
            AttendancePolicy policy,
            AttendanceDayContext context,
            Optional<AttendanceRecord> existingRecord) {
        LocalDate workDate = now.atZone(policy.zoneId()).toLocalDate();
        requireEligible(policy, workDate, context);
        if (existingRecord.isPresent()) {
            throw new AttendanceException(AttendanceRejection.ALREADY_CHECKED_IN);
        }
        return new AttendanceRecord(internId, workDate, policy, now, null);
    }

    /**
     * Applies the first raw checkout through the attached-policy inclusive cutoff.
     *
     * @param record open attendance row, if one exists
     * @param now authoritative server instant
     * @return checked-out record preserving its original check-in and attached policy
     */
    public AttendanceRecord checkOut(Optional<AttendanceRecord> record, Instant now) {
        return record
                .orElseThrow(() -> new AttendanceException(AttendanceRejection.NO_ATTENDANCE_RECORD))
                .checkOut(now);
    }

    private static void requireEligible(
            AttendancePolicy policy, LocalDate workDate, AttendanceDayContext context) {
        if (!context.activeIntern()) {
            throw new AttendanceException(AttendanceRejection.INACTIVE_INTERN);
        }
        if (!policy.isWorkday(workDate)) {
            throw new AttendanceException(AttendanceRejection.NON_WORKDAY);
        }
        if (context.globalDayOff()) {
            throw new AttendanceException(AttendanceRejection.GLOBAL_DAY_OFF);
        }
        if (context.approvedLeave()) {
            throw new AttendanceException(AttendanceRejection.APPROVED_LEAVE);
        }
    }
}
