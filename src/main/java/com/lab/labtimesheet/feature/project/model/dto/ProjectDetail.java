package com.lab.labtimesheet.feature.project.model.dto;

import java.time.LocalDate;

/**
 * Chi tiết Project đã được phân quyền cho các trang hiển thị phía máy chủ.
 *
 * @param id mã Project
 * @param name tên hiển thị
 * @param description mô tả tùy chọn
 * @param status trạng thái vòng đời
 * @param startDate ngày bắt đầu Project, được tính cả ngày này
 * @param endDate ngày kết thúc Project, được tính cả ngày này
 * @param mentorName tên hiển thị của Mentor sở hữu
 * @param leaderName tên hiển thị của Leader hiện tại, hoặc null sau khi hoàn tất và đóng nhiệm kỳ
 * @param canManage cho biết người xem có phải chủ sở hữu và Project còn cho phép thay đổi hay không
 */
public record ProjectDetail(
        long id,
        String name,
        String description,
        String status,
        LocalDate startDate,
        LocalDate endDate,
        String mentorName,
        String leaderName,
        boolean canManage) {
}
