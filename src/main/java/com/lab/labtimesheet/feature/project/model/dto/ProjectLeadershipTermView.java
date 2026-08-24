package com.lab.labtimesheet.feature.project.model.dto;

import java.time.Instant;

/**
 * Historical leadership interval for an authorized Project page.
 *
 * @param id leadership-term identifier
 * @param leaderName retained Leader display name
 * @param startedAt inclusive term start instant
 * @param endedAt term end instant, or null while the term is current
 * @param appointedByMentorUserId owning Mentor that appointed this term
 * @param endedByMentorUserId Mentor that closed this term, or null while current
 */
// DTO một giai đoạn Leader cho tab Leadership/History.
// Một Intern đổi Leader sẽ có term cũ được đóng và term mới được tạo thay vì ghi đè dữ liệu cũ.
public record ProjectLeadershipTermView(
        long id,
        String leaderName,
        Instant startedAt,
        Instant endedAt,
        long appointedByMentorUserId,
        Long endedByMentorUserId) {
}
