package com.lab.labtimesheet.feature.attendance.model.dto;

import java.time.YearMonth;
import java.util.Objects;

/**
 * Reservation-aware monthly Leave balance for the owning Intern workflow.
 *
 * <p>Pending and approved frozen allocation rows count as reserved. Rejected
 * and cancelled requests therefore release the reservation without deleting
 * their retained history. The quota is the policy value effective on the
 * selected business month; the remaining value is never negative.</p>
 *
 * @param quotaMonth selected local business month
 * @param reservedDays pending/approved frozen allocation count
 * @param quotaDays applicable monthly quota
 * @param remainingDays non-negative quota remainder
 * @param frozenCrossMonthDays reserved rows belonging to requests spanning a month boundary
 */
public record LeaveBalance(
        YearMonth quotaMonth,
        int reservedDays,
        int quotaDays,
        int remainingDays,
        int frozenCrossMonthDays) {

    /** Validates the invariant that the displayed remainder is derived from reservation and quota. */
    public LeaveBalance {
        Objects.requireNonNull(quotaMonth, "quotaMonth");
        if (reservedDays < 0 || quotaDays < 0 || frozenCrossMonthDays < 0) {
            throw new IllegalArgumentException("Leave balance values cannot be negative");
        }
        if (remainingDays != Math.max(0, quotaDays - reservedDays)) {
            throw new IllegalArgumentException("Leave balance remainder is inconsistent");
        }
        if (frozenCrossMonthDays > reservedDays) {
            throw new IllegalArgumentException("Cross-month reservations cannot exceed reserved days");
        }
    }

    /**
     * Creates a balance without an optional cross-month count for callers that
     * only need the three primary dashboard values.
     *
     * @param quotaMonth selected local business month
     * @param reservedDays pending/approved frozen allocation count
     * @param quotaDays applicable monthly quota
     */
    public LeaveBalance(YearMonth quotaMonth, int reservedDays, int quotaDays) {
        this(quotaMonth, reservedDays, quotaDays, Math.max(0, quotaDays - reservedDays), 0);
    }
}
