package com.lab.labtimesheet.feature.project.model.dto;

import java.time.Instant;

/**
 * Khoảng thời gian nhiệm kỳ Leader trong lịch sử của trang Project đã được phân quyền.
 *
 * @param id mã nhiệm kỳ Leader
 * @param leaderName tên hiển thị được lưu lại của Leader
 * @param startedAt thời điểm bắt đầu nhiệm kỳ, được tính cả thời điểm này
 * @param endedAt thời điểm kết thúc nhiệm kỳ, hoặc null khi nhiệm kỳ đang hiện tại
 */
public record ProjectLeadershipTermView(
        long id,
        String leaderName,
        Instant startedAt,
        Instant endedAt) {
}
