package com.lab.labtimesheet.feature.integration.model.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/** Validated draft identifier submitted by HolidayAPI test and activation actions. */
@Getter
@Setter
public class HolidayApiActionForm {
    @NotNull(message = "HolidayAPI draft is required")
    @Positive(message = "HolidayAPI draft is invalid")
    private Long draftId;
}
