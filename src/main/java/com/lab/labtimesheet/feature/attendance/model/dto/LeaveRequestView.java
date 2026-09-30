package com.lab.labtimesheet.feature.attendance.model.dto;

import com.lab.labtimesheet.feature.attendance.model.LeaveStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Persistence-free leave request view retaining its frozen allocation dates and decision boundary.
 *
 * @param id request identifier
 * @param internUserId owning Intern account
 * @param startDate inclusive range start
 * @param endDate inclusive range end
 * @param reason normalized reason
 * @param status current decision state
 * @param submittedAt submission instant
 * @param firstCountedStartAt first scheduled start that freezes the request
 * @param decidedByMentorUserId decision actor, when any
 * @param decidedAt decision instant, when any
 * @param cancelledAt cancellation instant, when any
 * @param allocations frozen eligible workdays
 */
public record LeaveRequestView(
        long id,
        long internUserId,
        LocalDate startDate,
        LocalDate endDate,
        String reason,
        LeaveStatus status,
        Instant submittedAt,
        Instant firstCountedStartAt,
        Long decidedByMentorUserId,
        Instant decidedAt,
        Instant cancelledAt,
        List<LeaveAllocation> allocations) {

    /**
     * Groups frozen leave dates by the quota month they consume, ordered chronologically.
     *
     * @return immutable quota-month groups with each group's dates in chronological order
     */
    public Map<LocalDate, List<LeaveAllocation>> allocationsByQuotaMonth() {
        Map<LocalDate, List<LeaveAllocation>> groups = new TreeMap<>();
        for (LeaveAllocation allocation : allocations) {
            groups.computeIfAbsent(allocation.quotaMonth(), ignored -> new ArrayList<>()).add(allocation);
        }
        groups.replaceAll((month, days) -> List.copyOf(days));
        return Collections.unmodifiableMap(groups);
    }
}
