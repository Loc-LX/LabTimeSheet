package com.lab.labtimesheet.feature.account.model.dto;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Immutable Account-owned historical reporting window for one Intern.
 *
 * <p>The dates are inclusive business dates. {@code activationDate} and the optional {@code terminalDate} are
 * derived from retained lifecycle timestamps in the Account feature's business timezone; callers never receive an
 * account or Intern-profile entity. A terminal date is included when it falls inside the configured internship
 * range, so completion or withdrawal does not erase that day's historical reporting eligibility.</p>
 *
 * @param userId Intern account identifier
 * @param activationDate local business date on which the internship became active
 * @param terminalDate local business date of completion or withdrawal, or {@code null} while active
 * @param startDate effective inclusive reporting-window start after activation
 * @param endDate effective inclusive reporting-window end after configured and terminal bounds
 */
public record InternReportingWindow(
        long userId,
        LocalDate activationDate,
        LocalDate terminalDate,
        LocalDate startDate,
        LocalDate endDate) {

    /** Validates the immutable cross-feature contract and its inclusive date ordering. */
    public InternReportingWindow {
        if (userId <= 0) {
            throw new IllegalArgumentException("Intern user ID must be positive");
        }
        Objects.requireNonNull(activationDate, "Activation date is required");
        Objects.requireNonNull(startDate, "Reporting start date is required");
        Objects.requireNonNull(endDate, "Reporting end date is required");
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("Reporting end date cannot precede its start date");
        }
        if (terminalDate != null && terminalDate.isBefore(activationDate)) {
            throw new IllegalArgumentException("Terminal date cannot precede activation date");
        }
    }

    /**
     * Checks an inclusive business date without exposing Account persistence or current lifecycle status.
     *
     * @param date business date supplied by the reporting consumer; {@code null} is rejected
     * @return {@code true} when the date belongs to this historical window
     * @throws NullPointerException when {@code date} is {@code null}
     */
    public boolean eligibleOn(LocalDate date) {
        Objects.requireNonNull(date, "Business date is required");
        return !date.isBefore(startDate) && !date.isAfter(endDate);
    }
}
