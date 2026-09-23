package com.lab.labtimesheet.feature.project.model.dto;

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
 * @param version client-observed optimistic-concurrency version
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
        Instant updatedAt,
        long version) {

    /**
     * Retains the pre-version constructor for historical report fixtures.
     *
     * @param id work-log identifier
     * @param projectId owning Project identifier
     * @param taskId owning Task identifier
     * @param membershipId historical author membership
     * @param workDate local effort date
     * @param minutes recorded effort minutes
     * @param note normalized note
     * @param createdAt original creation instant
     * @param updatedAt latest correction instant
     */
    public TaskWorkLogView(
            long id,
            long projectId,
            long taskId,
            long membershipId,
            LocalDate workDate,
            int minutes,
            String note,
            Instant createdAt,
            Instant updatedAt) {
        this(id, projectId, taskId, membershipId, workDate, minutes, note, createdAt, updatedAt, 0L);
    }
}
