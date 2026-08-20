package com.lab.labtimesheet.feature.attendance.model.dto;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Intern input for one full-day inclusive leave range.
 *
 * @param startDate first requested local date
 * @param endDate last requested local date
 * @param reason non-blank explanation
 */
public record LeaveRequestCommand(LocalDate startDate, LocalDate endDate, String reason) {

    /** Validates basic shape before service-level calendar and lifecycle checks. */
    public LeaveRequestCommand {
        Objects.requireNonNull(startDate, "startDate");
        Objects.requireNonNull(endDate, "endDate");
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("endDate must not be before startDate");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        reason = reason.strip();
    }
}
