package com.lab.labtimesheet.feature.attendance.model.dto;

import com.lab.labtimesheet.feature.attendance.model.LeaveStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

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
        List<LeaveAllocation> allocations) {}
