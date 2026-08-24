package com.lab.labtimesheet.feature.project.model.dto;

import java.time.LocalDate;

/**
 * Compact authorized Project row for lists.
 *
 * @param id Project identifier
 * @param name display name
 * @param status lifecycle status
 * @param startDate inclusive start date
 * @param endDate inclusive end date
 */
// DTO gọn cho từng dòng ở trang danh sách Project.
// QueryService tạo từ ProjectEntity để UI không cần nhận toàn bộ membership và lịch sử bên trong Project.
public record ProjectSummary(long id, String name, String status, LocalDate startDate, LocalDate endDate) {
}
