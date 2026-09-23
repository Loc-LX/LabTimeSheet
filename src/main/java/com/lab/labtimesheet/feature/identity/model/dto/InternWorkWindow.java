package com.lab.labtimesheet.feature.identity.model.dto;

import java.time.LocalDate;
import java.util.Objects;

import com.lab.labtimesheet.feature.identity.model.AccountStatus;
import com.lab.labtimesheet.feature.identity.model.InternshipStatus;

/**
 * Immutable account-owned work-window snapshot for an Intern operation.
 *
 * <p>The snapshot carries no persistence type. A caller that needs to serialize a mutation against this account
 * boundary should obtain it through the Account service's locked method inside its transaction. The date range is
 * inclusive and is evaluated together with both lifecycle states.</p>
 *
 * @param userId Intern account identifier
 * @param businessDate server-derived business date being evaluated
 * @param startDate inclusive internship start date
 * @param endDate inclusive internship end date
 * @param accountStatus global account lifecycle state at the snapshot
 * @param internshipStatus internship lifecycle state at the snapshot
 */
public record InternWorkWindow(
        long userId,
        LocalDate businessDate,
        LocalDate startDate,
        LocalDate endDate,
        AccountStatus accountStatus,
        InternshipStatus internshipStatus) {

    /** Validates identifiers, date ordering, and required lifecycle state values at the public boundary. */
    public InternWorkWindow {
        if (userId <= 0) {
            throw new IllegalArgumentException("Intern user ID must be positive");
        }
        Objects.requireNonNull(businessDate, "Business date is required");
        Objects.requireNonNull(startDate, "Internship start date is required");
        Objects.requireNonNull(endDate, "Internship end date is required");
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("Internship end date cannot precede its start date");
        }
        Objects.requireNonNull(accountStatus, "Account status is required");
        Objects.requireNonNull(internshipStatus, "Internship status is required");
    }

    /**
     * Returns whether this Intern may perform a work-date operation in the snapshot.
     *
     * @return true only for an active account, active internship, and inclusive date-range match
     */
    public boolean eligible() {
        return accountStatus == AccountStatus.ACTIVE
                && internshipStatus == InternshipStatus.ACTIVE
                && !businessDate.isBefore(startDate)
                && !businessDate.isAfter(endDate);
    }

    /**
     * Checks another date against this snapshot's lifecycle states and inclusive internship range.
     *
     * @param date server-derived business date to evaluate
     * @return true when the supplied date is eligible under the captured states
     */
    public boolean eligibleOn(LocalDate date) {
        Objects.requireNonNull(date, "Business date is required");
        return accountStatus == AccountStatus.ACTIVE
                && internshipStatus == InternshipStatus.ACTIVE
                && !date.isBefore(startDate)
                && !date.isAfter(endDate);
    }
}
