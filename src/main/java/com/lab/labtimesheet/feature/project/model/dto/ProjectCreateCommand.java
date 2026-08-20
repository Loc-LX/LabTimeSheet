package com.lab.labtimesheet.feature.project.model.dto;

import java.time.LocalDate;

/**
 * Lệnh dịch vụ dùng để tạo Project ở trạng thái Planned cùng Leader ban đầu trong một lần nguyên tử.
 *
 * @param name tên Project bắt buộc
 * @param description mô tả Project tùy chọn
 * @param startDate ngày bắt đầu Project, được tính cả ngày này
 * @param endDate ngày kết thúc Project, không được trước {@code startDate}
 * @param initialLeaderUserId mã Intern đủ điều kiện được bổ nhiệm làm Leader đầu tiên
 */
public record ProjectCreateCommand(
        String name,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        long initialLeaderUserId) {
}
