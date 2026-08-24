package com.lab.labtimesheet.feature.project.model.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Validated browser input for planning a Project and appointing its initial Leader.
 *
 * @param name required Project name, limited to the persisted column length
 * @param description optional description
 * @param startDate inclusive Project start date; the service rejects dates before today
 * @param endDate inclusive Project end date
 * @param initialLeaderUserId positive eligible Intern user identifier
 */
// DTO của tầng Web, đại diện cho đúng payload mà form HTML gửi lên endpoint POST /projects.
// Đây chưa phải ProjectEntity: object này chỉ sống trong request hiện tại, được Spring MVC tạo ra,
// bind dữ liệu từ các input có name tương ứng, rồi chạy Bean Validation trước khi Controller gọi Service.
public record ProjectCreateForm(
        // @NotBlank loại chuỗi null/rỗng/toàn khoảng trắng; @Size phải đồng bộ với giới hạn cột projects.name.
        // Lỗi của component này được đưa vào BindingResult và Thymeleaf đọc bằng *{name} / #fields.hasErrors('name').
        @NotBlank @Size(max = 160) String name,
        // Description là tùy chọn ở Web DTO; ProjectEntity/Service sẽ tiếp tục normalize trước khi lưu.
        String description,
        // Browser gửi date dưới dạng chuỗi ISO yyyy-MM-dd. @DateTimeFormat yêu cầu Spring đổi chuỗi đó thành LocalDate.
        // @NotNull đảm bảo lỗi thiếu ngày là lỗi field-level, trước khi logic date range chạy.
        @NotNull(message = "Start date is required")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        // End date cũng được convert/bind như startDate; quan hệ end >= start được kiểm tra ở isDateRangeValid().
        @NotNull(message = "End date is required")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
        // Form chỉ gửi ID của Intern được chọn, không gửi cả Account/InternProfile entity.
        // @Positive chặn ID âm/0 ở lớp Web; Service vẫn phải query và re-check eligibility vì client không đáng tin.
        @NotNull(message = "Choose an initial Leader")
        @Positive(message = "Choose a valid initial Leader") Long initialLeaderUserId) {

    /**
     * Tạo form rỗng cho GET /projects/new.
     *
     * <p>Spring MVC cần một object đích để Thymeleaf bind expression {@code *{name}}, {@code *{startDate}},...
     * khi render lần đầu. Constructor này chỉ tạo object trong Model/request; nó không gọi repository, không tạo
     * ProjectEntity và không phát sinh INSERT.</p>
     */
    public ProjectCreateForm() {
        this(null, null, null, null, null);
    }

    /**
     * Kiểm tra validation ở cấp toàn object cho khoảng ngày.
     *
     * <p>Method có tiền tố {@code is} nên Bean Validation/Thymeleaf coi kết quả là property
     * {@code dateRangeValid}. Khi một trong hai ngày còn null, method trả về true để @NotNull tự tạo lỗi đúng
     * field; khi cả hai đã có giá trị, chỉ chấp nhận endDate không đứng trước startDate.</p>
     *
     * @return true khi chưa đủ dữ liệu cho @NotNull xử lý hoặc khoảng ngày hợp lệ
     */
    @AssertTrue(message = "End date must be on or after the Start date")
    public boolean isDateRangeValid() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }

    /**
     * Chuyển Web DTO đã qua binding/validation thành command bất biến của application service.
     *
     * <p>Method này chỉ copy giá trị trong memory. Nó không kiểm tra quyền sở hữu, không kiểm tra lại Intern,
     * không tạo entity và không save database; các invariant phụ thuộc trạng thái hiện tại phải được xử lý trong
     * ProjectService ở bên trong transaction.</p>
     *
     * @return command giữ nguyên dữ liệu đã submit để Controller truyền vào Service
     */
    public ProjectCreateCommand toCommand() {
        return new ProjectCreateCommand(name, description, startDate, endDate, initialLeaderUserId);
    }
}
