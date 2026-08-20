package com.lab.labtimesheet.feature.task.model.dto;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Authorized Task work log projection with stable logging-membership attribution.
 *
 * @param id work log identifier
 * @param projectId owning Project identifier
 * @param taskId owning Task identifier
 * @param membershipId logging membership identifier retained across reassignment
 * @param memberName current display name of the logging member
 * @param workDate local effort date
 * @param minutes logged minutes from 1 through 1440
 * @param note optional non-blank note, or null
 * @param createdAt persisted creation instant
 * @param updatedAt persisted last correction instant
 */
public record WorkLogView(
        long id,
        long projectId,
        long taskId,
        long membershipId,
        String memberName,
        LocalDate workDate,
        int minutes,
        String note,
        Instant createdAt,
        Instant updatedAt) {}
