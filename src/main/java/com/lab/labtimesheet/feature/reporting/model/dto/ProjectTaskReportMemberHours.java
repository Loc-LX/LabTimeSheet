package com.lab.labtimesheet.feature.reporting.model.dto;

/**
 * One authorized retained-membership work total in a Project/Task report.
 *
 * <p>The membership identifier is the Project-scoped attribution key. This row is emitted only
 * for Admins, the visible Project's owning Mentor, or its current Leader; ordinary members
 * receive aggregate report totals without this breakdown.</p>
 *
 * @param membershipId retained Project membership identifier
 * @param displayName current or retained member display name supplied by Project
 * @param totalMinutes filtered retained Task work-log minutes
 */
public record ProjectTaskReportMemberHours(long membershipId, String displayName, long totalMinutes) {
}
