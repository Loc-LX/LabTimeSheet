package com.lab.labtimesheet.feature.attendance.model.dto;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Frozen quota-consuming leave-day projection.
 *
 * @param leaveDate exact local workday allocated to the request
 * @param quotaMonth first local date of the month whose quota is consumed
 * @param policyVersionId attached historical Attendance policy version
 * @param monthlyQuotaSnapshot quota value captured when this day was allocated
 * @param approvalWithdrawnAt server timestamp when approval was withdrawn, or {@code null}
 */
public record LeaveAllocation(
        LocalDate leaveDate,
        LocalDate quotaMonth,
        long policyVersionId,
        int monthlyQuotaSnapshot,
        Instant approvalWithdrawnAt) {

    /** Backwards-compatible constructor for allocations without approval withdrawal. */
    public LeaveAllocation(
            LocalDate leaveDate,
            LocalDate quotaMonth,
            long policyVersionId,
            int monthlyQuotaSnapshot) {
        this(leaveDate, quotaMonth, policyVersionId, monthlyQuotaSnapshot, null);
    }

    /** Returns whether approval has been withdrawn from this date. */
    public boolean isApprovalWithdrawn() {
        return approvalWithdrawnAt != null;
    }
}
