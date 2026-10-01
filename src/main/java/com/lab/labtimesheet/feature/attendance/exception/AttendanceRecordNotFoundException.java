package com.lab.labtimesheet.feature.attendance.exception;

/**
 * Signals that an authenticated caller cannot address an Attendance record in the requested scope.
 *
 * <p>The same exception represents an absent identifier and a record outside the caller's scope,
 * so the web boundary can return one non-disclosing not-found response.</p>
 */
public final class AttendanceRecordNotFoundException extends RuntimeException {

    /** Creates the non-disclosing Attendance record lookup failure. */
    public AttendanceRecordNotFoundException() {
        super("Attendance record unavailable");
    }
}
