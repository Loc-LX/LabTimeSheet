package com.lab.labtimesheet.feature.task.model.dto;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Retained Task work-log projection with immutable author and correction attribution.
 *
 * @param id work-log identifier
 * @param projectId owning Project identifier
 * @param taskId owning Task identifier
 * @param membershipId historical author membership identifier
 * @param workDate local business date of the effort
 * @param minutes recorded effort minutes
 * @param note optional normalized note
 * @param createdAt original creation instant
 * @param updatedAt latest correction instant
 */
public record TaskWorkLogView(
        long id,
        long projectId,
        long taskId,
        long membershipId,
        LocalDate workDate,
        int minutes,
        String note,
        Instant createdAt,
        Instant updatedAt) {}
