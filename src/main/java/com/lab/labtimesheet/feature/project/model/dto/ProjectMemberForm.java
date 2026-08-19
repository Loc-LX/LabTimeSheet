package com.lab.labtimesheet.feature.project.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * [I1-PRJ-03, I2-PRJ-03] Form trên trình duyệt dùng để chọn Intern thay thế cho việc bổ nhiệm
 * Leader hoặc đóng membership Leader cũ.
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
