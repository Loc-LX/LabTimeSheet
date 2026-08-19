package com.lab.labtimesheet.feature.project.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Form trên trình duyệt dùng để chọn Intern thay thế cho việc bổ nhiệm Leader.
 *
 * @param internUserId mã người dùng Intern phải là số dương; dữ liệu null bị từ chối trước khi
 *        thay đổi dữ liệu
 * @param expectedLeadershipTermId token của nhiệm kỳ hiện tại được hiển thị cùng biểu mẫu Leader; token
 *        này ngăn form cũ thay thế nhiệm kỳ Leader mới hơn
 */
public record ProjectMemberForm(
        @NotNull @Positive Long internUserId,
        @NotNull @Positive Long expectedLeadershipTermId) {
}
