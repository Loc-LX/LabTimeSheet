package com.lab.labtimesheet.feature.project.model.dto;

import java.time.LocalDate;

/**
 * Immutable, non-managed work-log identity projection used before a correction acquires locks.
 *
 * <p>The projection carries only the fields needed to discover the Account-lock date and to
 * re-check the candidate after the pessimistic work-log query. It deliberately cannot become a
 * first-level-cache entity that would defeat the later locked load.</p>
 *
 * @param id work-log identifier
 * @param projectId owning Project identifier
 * @param taskId owning Task identifier
 * @param membershipId retained author membership identifier
 * @param workDate local effort date
 */
public record TaskWorkLogCandidate(
        long id,
        long projectId,
        long taskId,
        long membershipId,
        LocalDate workDate) {}
