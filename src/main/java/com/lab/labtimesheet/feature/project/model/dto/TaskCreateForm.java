package com.lab.labtimesheet.feature.project.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Browser-bound Task creation fields.
 *
 * @param title required title, limited to 200 characters before service normalization
 * @param description optional safe text retained after validation
 * @param assigneeMembershipId selected same-Project membership identifier
 * @param dueDate optional ISO date; service validation applies Project and calendar rules
 * @param estimatedMinutes optional Leader-only whole-Task estimate in minutes
 */
public record TaskCreateForm(
        @NotBlank @Size(max = 200) String title,
        String description,
        @NotNull Long assigneeMembershipId,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDate,
        @Min(value = 1, message = "Estimate must be at least 1 minute")
        @Max(value = 527040, message = "Estimate must not exceed 527040 minutes")
        Integer estimatedMinutes) {
    public TaskCreateForm(String title, String description, Long assigneeMembershipId, LocalDate dueDate) {
        this(title, description, assigneeMembershipId, dueDate, null);
    }
}
