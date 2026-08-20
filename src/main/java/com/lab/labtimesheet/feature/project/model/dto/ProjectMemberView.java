package com.lab.labtimesheet.feature.project.model.dto;

import java.time.Instant;

/**
 * Thông tin thành viên Project hiện tại hoặc trong lịch sử cho các trang hiển thị phía máy chủ đã được phân quyền.
 *
 * @param membershipId mã ổn định của khoảng thời gian tham gia
 * @param internUserId mã người dùng Intern tham gia
 * @param displayName tên hiển thị hiện tại của Account
 * @param joinedAt thời điểm bắt đầu tham gia, được tính cả thời điểm này
 * @param leftAt thời điểm kết thúc tham gia, hoặc null khi vẫn là thành viên hiện tại
 * @param currentLeader true chỉ khi đây là thành viên Leader hiện tại của Project chưa hoàn tất
 */
public record ProjectMemberView(
        long membershipId,
        long internUserId,
        String displayName,
        Instant joinedAt,
        Instant leftAt,
        boolean currentLeader) {
}
