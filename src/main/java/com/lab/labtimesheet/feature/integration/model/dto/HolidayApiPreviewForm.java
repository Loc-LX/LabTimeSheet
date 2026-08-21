package com.lab.labtimesheet.feature.integration.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;

/** Validated Vietnam HolidayAPI preview year submitted by an administrator. */
@Getter
@Setter
public class HolidayApiPreviewForm {
    @Min(value = 2000, message = "Year must be 2000 or later")
    @Max(value = 2100, message = "Year must be 2100 or earlier")
    private int year = 2026;
}
