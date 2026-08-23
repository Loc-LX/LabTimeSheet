package com.lab.labtimesheet.feature.reporting.model.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * One present workday's compliance score for the report trend chart and day table.
 *
 * @param date local business date
 * @param score daily compliance score from zero through one
 */
public record AttendanceDailyScore(LocalDate date, BigDecimal score) {

    /**
     * Validates required chart fields.
     */
    public AttendanceDailyScore {
        Objects.requireNonNull(date, "date");
        Objects.requireNonNull(score, "score");
    }
}