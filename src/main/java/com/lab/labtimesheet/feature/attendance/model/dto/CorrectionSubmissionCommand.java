package com.lab.labtimesheet.feature.attendance.model.dto;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * Validated form values for a missed-checkout correction. The proposed checkout is expressed as a policy-local
 * time on the original work date; the attached historical policy supplies the timezone when deriving the instant.
 *
 * @param workDate policy-local date of the missing-checkout attendance record
 * @param proposedCheckoutTime proposed policy-local checkout time on that date
 * @param reason non-blank human-readable explanation
 */
public record CorrectionSubmissionCommand(
        LocalDate workDate,
        LocalTime proposedCheckoutTime,
        String reason) {}