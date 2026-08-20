package com.lab.labtimesheet.feature.reporting.service;

import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.reporting.model.dto.AttendanceReportDay;
import java.time.LocalDate;
import java.util.List;

/**
 * Attendance report dataset boundary implemented by the Attendance feature.
 *
 * <p>The Attendance feature owns raw rows, leave, eligibility, and calendar queries, so it is the
 * only feature able to classify dates into {@link AttendanceReportDay} rows. Scope is enforced here,
 * mirroring {@code AttendanceApplicationService.history}: Interns receive only their own rows, while
 * Mentor and Admin actors may target any Intern.
 */
public interface AttendanceReportDataProvider {

    /**
     * Returns the classified inclusive date range for the target Intern.
     *
     * @param actor authenticated attendance authorization context
     * @param targetInternId target Intern account identifier
     * @param from inclusive first local date
     * @param to inclusive last local date
     * @return classified day rows in ascending date order
     * @throws org.springframework.security.access.AccessDeniedException when the actor may not
     *     inspect the target Intern
     */
    List<AttendanceReportDay> reportDays(AttendanceActor actor, long targetInternId, LocalDate from, LocalDate to);
}