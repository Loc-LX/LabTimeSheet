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
// Command nội bộ nằm giữa Controller và ProjectService.
// Controller nhận dữ liệu theo quy ước HTML/Spring MVC (ProjectCreateForm), sau đó đổi sang command này để
// Service không phụ thuộc vào tên field, annotation validation hay Thymeleaf của giao diện web.
// Command chỉ là dữ liệu bất biến trong memory; bản thân nó không mở transaction và không tự lưu database.
public record ProjectCreateCommand(
        // Giá trị đã được @NotBlank/@Size kiểm tra ở boundary Web; Service vẫn normalize lần cuối khi tạo entity.
        String name,
        // Mô tả có thể null; entity factory sẽ chuyển null/blank theo rule của domain.
        String description,
        // Hai ngày đã được Spring convert từ chuỗi HTML date sang LocalDate và form đã kiểm tra quan hệ ngày.
        LocalDate startDate,
        LocalDate endDate,
        // ID do client gửi, chỉ là khóa tham chiếu; Service phải query Account/InternProfile và authorize lại.
        long initialLeaderUserId) {
}
