package com.lab.labtimesheet.feature.project.model.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Dữ liệu biểu mẫu trên trình duyệt đã được kiểm tra để tạo Project và bổ nhiệm Leader ban đầu.
 *
 * @param name tên Project bắt buộc, giới hạn theo độ dài cột lưu trữ
 * @param description mô tả tùy chọn
 * @param startDate ngày bắt đầu Project, được tính cả ngày này
 * @param endDate ngày kết thúc Project, được tính cả ngày này
 * @param initialLeaderUserId mã người dùng Intern dương và đủ điều kiện
 */
public record ProjectCreateForm(
        @NotBlank @Size(max = 160) String name,
        String description,
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
        @NotNull @Positive Long initialLeaderUserId) {

    /** Tạo biểu mẫu rỗng cho yêu cầu GET ban đầu và quá trình gắn dữ liệu của Thymeleaf. */
    public ProjectCreateForm() {
        this(null, null, null, null, null);
    }

    /**
     * Kiểm tra khoảng ngày sau khi cả hai ngày bắt buộc đã được gắn dữ liệu thành công.
     *
     * @return true khi một trong hai ngày còn chờ kiểm tra bắt buộc hoặc ngày kết thúc không trước ngày bắt đầu
     */
    @AssertTrue(message = "End date must not precede start date")
    public boolean isDateRangeValid() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }

    /**
     * Chuyển dữ liệu biểu mẫu đã kiểm tra thành lệnh dịch vụ bất biến.
     *
     * @return lệnh tạo Project giữ nguyên các giá trị đã gửi
     */
    public ProjectCreateCommand toCommand() {
        return new ProjectCreateCommand(name, description, startDate, endDate, initialLeaderUserId);
    }
}
