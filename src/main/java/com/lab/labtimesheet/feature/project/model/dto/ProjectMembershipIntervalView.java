package com.lab.labtimesheet.feature.project.model.dto;

import java.time.Instant;

/**
 * Immutable retained membership interval for one authenticated Intern across Projects.
 *
 * <p>The interval identity is the value referenced by Task attribution and work-log rows. A
 * non-null {@code leftAt} is retained history, while a null value means that the membership is
 * still current.</p>
 *
 * @param projectId owning Project identifier
 * @param membershipId stable Project membership-interval identifier
 * @param joinedAt inclusive membership start instant
 * @param leftAt membership end instant, or null while current
 */
// DTO nhẹ chỉ mang ID membership và mốc thời gian tham gia/rời Project.
// QueryService dùng nó để kiểm tra quan hệ lịch sử mà không cần tải toàn bộ ProjectEntity.
public record ProjectMembershipIntervalView(
        long projectId,
        long membershipId,
        Instant joinedAt,
        Instant leftAt) {
}
