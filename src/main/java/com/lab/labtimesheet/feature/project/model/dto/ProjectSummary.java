package com.lab.labtimesheet.feature.project.model.dto;

import java.time.LocalDate;

/**
 * Dòng thông tin Project rút gọn đã được phân quyền để hiển thị trong danh sách.
 *
 * @param id mã Project
 * @param name tên hiển thị
 * @param status trạng thái vòng đời
 * @param startDate ngày bắt đầu, được tính cả ngày này
 * @param endDate ngày kết thúc, được tính cả ngày này
 */
public record ProjectSummary(long id, String name, String status, LocalDate startDate, LocalDate endDate) {
}
