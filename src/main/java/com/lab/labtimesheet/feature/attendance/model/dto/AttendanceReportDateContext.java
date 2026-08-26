package com.lab.labtimesheet.feature.attendance.model.dto;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Effective attendance/calendar annotation for a report date.
 *
 * <p>This is context only. A report must continue to include retained Task work on policy
 * non-workdays and global days off; no attendance row is required or inferred.</p>
 *
 * @param reportDate selected local business date
 * @param configuredWorkday whether the effective policy lists the date's weekday as a workday
 * @param globalDayOff whether a locally authoritative calendar event marks the date as day off
 * @param policyId effective attendance policy version identifier
 * @param policyEffectiveFrom effective date of the applied policy version
 * @param policyZoneId effective policy timezone used for business-date semantics
 */
public record AttendanceReportDateContext(
        LocalDate reportDate,
        boolean configuredWorkday,
        boolean globalDayOff,
        long policyId,
        LocalDate policyEffectiveFrom,
        ZoneId policyZoneId) {

    /**
     * Returns the precedence-ordered human label used by report presentation.
     *
     * @return global day off, configured workday, or policy non-workday
     */
    public String label() {
        if (globalDayOff) {
            return "Global day off";
        }
        return configuredWorkday ? "Configured attendance workday" : "Policy non-workday";
    }
}
