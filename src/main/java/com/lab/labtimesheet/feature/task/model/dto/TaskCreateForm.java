package com.lab.labtimesheet.feature.task.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
 */
public record TaskCreateForm(
        @NotBlank @Size(max = 200) String title,
        String description,
        @NotNull Long assigneeMembershipId,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDate) {}
