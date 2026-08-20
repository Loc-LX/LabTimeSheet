package com.lab.labtimesheet.feature.attendance.model.dto;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Intern input for one missed-checkout correction.
 *
 * @param proposedCheckout local proposed checkout on the original work date
 * @param reason non-blank explanation
 */
public record CorrectionRequestCommand(LocalDateTime proposedCheckout, String reason) {

    /** Validates command shape before attendance-row lookup. */
    public CorrectionRequestCommand {
        Objects.requireNonNull(proposedCheckout, "proposedCheckout");
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("reason must not be blank");
        }
        reason = reason.strip();
    }
}
