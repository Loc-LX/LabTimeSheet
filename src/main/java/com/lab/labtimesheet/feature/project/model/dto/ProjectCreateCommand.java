package com.lab.labtimesheet.feature.project.model.dto;

import java.time.LocalDate;

/**
 * Service command for atomically planning a Project with its initial Leader.
 *
 * @param name required Project name
 * @param description optional Project description
 * @param startDate inclusive Project start date
 * @param endDate inclusive Project end date, not before {@code startDate}
 * @param initialLeaderUserId eligible Intern appointed as the first Leader
 */
// Command nội bộ chuyển dữ liệu đã hợp lệ từ Controller sang ProjectService.
// Tách command khỏi form giúp Service không phụ thuộc trực tiếp vào chi tiết giao diện web.
public record ProjectCreateCommand(
        String name,
        String description,
        LocalDate startDate,
        LocalDate endDate,
        long initialLeaderUserId) {
}
