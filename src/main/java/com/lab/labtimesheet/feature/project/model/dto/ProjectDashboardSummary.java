package com.lab.labtimesheet.feature.project.model.dto;

/**
 * Role-scoped current Project metrics for the dashboard.
 *
 * @param activeProjectCount number of active Projects visible in the actor's current scope
 * @param distinctActiveMemberCount distinct eligible active members for a Mentor; zero for other roles
 */
public record ProjectDashboardSummary(long activeProjectCount, long distinctActiveMemberCount) {
}
