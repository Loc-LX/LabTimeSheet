package com.lab.labtimesheet.feature.attendance.exception;

/** Signals a refused or conflicting Intern attendance-exception request. */
public final class AttendanceExceptionRequestException extends RuntimeException {

    /** Creates a request rejection with safe operator-facing context. */
    public AttendanceExceptionRequestException(String message) {
        super(message);
    }

    /** Retains a persistence conflict while presenting a stable request error. */
    public AttendanceExceptionRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
