package com.lab.labtimesheet.feature.task.model.dto;

/** Immutable request for the initial Leader-authored forecast required by worked reassignment. */
public record RemainingEffortForecastInput(Integer remainingMinutes, String note) {
    /** Raw immutable input; TaskService owns validation and normalization. */
}
