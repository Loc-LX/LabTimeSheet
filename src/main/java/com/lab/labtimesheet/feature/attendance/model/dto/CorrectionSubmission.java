package com.lab.labtimesheet.feature.attendance.model.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Presentation and reporting DTO for one missed-checkout correction. Raw instants and the attached policy timezone
 * remain available so display accessors render policy-local 24-hour values consistently.
 *
 * @param id correction identifier
 * @param workDate policy-local date of the corrected attendance record
 * @param proposedCheckoutAt proposed checkout instant derived from the attached policy timezone
 * @param zoneId policy timezone used for display formatting
 * @param reason submitted explanation
 * @param status PENDING, APPROVED, or REJECTED
 * @param submittedAt server submission instant
 * @param submissionDeadline inclusive scheduled-end-plus-24-hours submission deadline
 * @param decisionDeadline separate 24-hour decision window end
 */
public record CorrectionSubmission(
        long id,
        LocalDate workDate,
        Instant proposedCheckoutAt,
        ZoneId zoneId,
        String reason,
        String status,
        Instant submittedAt,
        Instant submissionDeadline,
        Instant decisionDeadline) {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("dd/MM/uuuu");
    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    /**
     * Formats the business date as {@code dd/MM/yyyy}.
     *
     * @return presentation-ready work date
     */
    public String workDateDisplay() {
        return DATE_FORMAT.format(workDate);
    }

    /**
     * Formats the proposed checkout in the attached policy timezone as 24-hour {@code HH:mm}.
     *
     * @return presentation-ready proposed checkout time
     */
    public String proposedCheckoutTimeDisplay() {
        return TIME_FORMAT.format(proposedCheckoutAt.atZone(zoneId));
    }
}