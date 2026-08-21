package com.lab.labtimesheet.feature.attendance.model.dto;

import com.lab.labtimesheet.feature.attendance.model.AttendancePolicy;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Non-secret Admin Policy History projection retaining the effective version and actor metadata.
 *
 * @param id persisted policy version identifier
 * @param effectiveFrom first date governed by the version
 * @param policy immutable policy values
 * @param createdByUserId Admin actor, or {@code null} for the seeded version
 * @param createdAt creation instant
 * @param updatedAt last replacement instant
 * @param version optimistic version used by future edits
 */
public record AttendancePolicyHistoryItem(
        long id,
        LocalDate effectiveFrom,
        AttendancePolicy policy,
        Long createdByUserId,
        Instant createdAt,
        Instant updatedAt,
        long version) {}
