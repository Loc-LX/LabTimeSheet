package com.lab.labtimesheet.feature.project.model.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Positive;
import java.util.List;

/**
 * Biểu mẫu trên trình duyệt cho một lần chọn thành viên để Mentor sở hữu thêm nguyên tử.
 *
 * @param internUserIds các mã tài khoản Intern dương, không trùng nhau, được chọn trong danh sách
 */
public record ProjectMembersForm(@NotEmpty List<@Positive Long> internUserIds) {

    /** Tạo biểu mẫu rỗng cho yêu cầu GET ban đầu của trang thành viên. */
    public ProjectMembersForm() {
        this(List.of());
    }
}
