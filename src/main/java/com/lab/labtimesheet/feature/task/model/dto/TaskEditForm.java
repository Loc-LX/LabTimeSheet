package com.lab.labtimesheet.feature.task.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Browser-bound Task definition edit fields.
 *
 * <p>The assignee and assignment actor are intentionally absent: editing a Task definition never
 * changes assignment, which is a distinct Leader-only operation.
 *
 * @param title required title, limited to 200 characters before service normalization
 * @param description optional safe text retained after validation
 * @param dueDate optional ISO date; service validation applies Project and calendar rules
 */
public record TaskEditForm(
        @NotBlank @Size(max = 200) String title,
        String description,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDate) {}
