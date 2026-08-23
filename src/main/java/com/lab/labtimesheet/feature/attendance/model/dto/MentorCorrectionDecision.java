package com.lab.labtimesheet.feature.attendance.model.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Presentation DTO for one correction on the Mentor decisions page. Raw instants and the attached policy timezone
 * remain available so display accessors render policy-local 24-hour values consistently.
 *
 * @param id correction identifier
 * @param internUserId owning Intern account identifier
 * @param internDisplayName owning Intern display name
 * @param workDate policy-local date of the corrected attendance record
 * @param proposedCheckoutAt proposed checkout instant derived from the attached policy timezone
 * @param zoneId policy timezone used for display formatting
 * @param status PENDING, APPROVED, or REJECTED
 * @param decisionDeadline inclusive decision-window end, submitted time plus 24 hours
 * @param lockedAt instant the decided outcome was locked, or {@code null} while mutable
 * @param decidedByMentorUserId deciding Mentor account identifier once decided
 * @param decidedAt server decision instant once decided
 * @param decisionNote optional Mentor decision note
 * @param rawCheckoutPresent whether the raw attendance record already holds a checkout
 * @param approvedCompliant whether the proposed checkout meets or exceeds the policy scheduled end
 * @param revertable whether the Mentor may still revert this decided correction inside the window
 */
public record MentorCorrectionDecision(
        long id,
        long internUserId,
        String internDisplayName,
        LocalDate workDate,
        Instant proposedCheckoutAt,
        ZoneId zoneId,
        String status,
        Instant decisionDeadline,
        Instant lockedAt,
        Long decidedByMentorUserId,
        Instant decidedAt,
        String decisionNote,
        boolean rawCheckoutPresent,
        boolean approvedCompliant,
        boolean revertable) {

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