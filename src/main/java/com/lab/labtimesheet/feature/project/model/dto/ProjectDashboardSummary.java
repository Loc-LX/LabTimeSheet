package com.lab.labtimesheet.feature.project.model.dto;

/**
 * Role-scoped current Project metrics for the dashboard.
 *
 * @param activeProjectCount number of active Projects visible in the actor's current scope
 * @param distinctActiveMemberCount distinct eligible active members for a Mentor; zero for other roles
 */
// DTO các con số tổng hợp cho dashboard theo role.
// Repository chỉ đếm dữ liệu cần thiết nên UI không phải tải danh sách Project lớn để tự tính.
public record ProjectDashboardSummary(long activeProjectCount, long distinctActiveMemberCount) {
}
