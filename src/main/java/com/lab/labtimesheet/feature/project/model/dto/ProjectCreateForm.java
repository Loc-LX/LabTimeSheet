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
 * @param startDate inclusive Project start date
 * @param endDate inclusive Project end date
 * @param initialLeaderUserId positive eligible Intern user identifier
 */
// Object nhận dữ liệu từ form tạo Project trên trình duyệt.
// Spring dùng @ModelAttribute để map các ô nhập thành object này trước khi ProjectController gọi Service.
public record ProjectCreateForm(
        @NotBlank @Size(max = 160) String name,
        String description,
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
        @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
        @NotNull @Positive Long initialLeaderUserId) {

    /** Creates an empty form for the initial GET request and Thymeleaf binding. */
    public ProjectCreateForm() {
        this(null, null, null, null, null);
    }

    /**
     * Validates the date interval only after both required dates have bound successfully.
     *
     * @return true when either date awaits required-field validation or end is not before start
     */
    @AssertTrue(message = "End date must not precede start date")
    public boolean isDateRangeValid() {
        return startDate == null || endDate == null || !endDate.isBefore(startDate);
    }

    /**
     * Converts validated browser input to the immutable service command.
     *
     * @return creation command preserving the submitted values
     */
    public ProjectCreateCommand toCommand() {
        return new ProjectCreateCommand(name, description, startDate, endDate, initialLeaderUserId);
    }
}
