package com.lab.labtimesheet.feature.attendance.model.dto;

import java.util.List;

/**
 * Outcome of an accepted leave submission, retaining its materialized frozen days.
 *
 * @param requestId persisted leave request identifier
 * @param status initial request state, always {@code PENDING}
 * @param countedDays eligible workdays materialized with month and quota snapshot
 */
public record LeaveSubmission(long requestId, String status, List<CountedLeaveDay> countedDays) {
}