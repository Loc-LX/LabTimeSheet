package com.lab.labtimesheet.feature.attendance.model.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Intern leave form state: one month's quota, reservation, and available days plus prior requests.
 *
 * @param month quota month being displayed (first day)
 * @param quota monthly leave quota from the policy effective on the month
 * @param reserved frozen pending/approved days in that month
 * @param available remaining quota days in that month
 * @param requests prior requests newest-first with their counted days
 */
public record LeaveOverview(
        LocalDate month,
        int quota,
        int reserved,
        int available,
        List<PriorLeaveRequest> requests) {
}