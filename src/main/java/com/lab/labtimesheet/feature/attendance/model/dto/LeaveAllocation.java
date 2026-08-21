package com.lab.labtimesheet.feature.attendance.model.dto;

import java.time.LocalDate;

/**
 * Frozen quota-consuming leave-day projection.
 *
 * @param leaveDate exact local workday allocated to the request
 * @param quotaMonth first local date of the month whose quota is consumed
 * @param policyVersionId attached historical Attendance policy version
 * @param monthlyQuotaSnapshot quota value captured when this day was allocated
 */
public record LeaveAllocation(
        LocalDate leaveDate, LocalDate quotaMonth, long policyVersionId, int monthlyQuotaSnapshot) {}
