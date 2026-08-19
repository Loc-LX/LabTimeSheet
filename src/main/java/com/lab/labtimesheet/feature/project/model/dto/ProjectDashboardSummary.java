package com.lab.labtimesheet.feature.project.model.dto;

/**
 * Số liệu Project hiện tại trên dashboard, giới hạn theo phạm vi vai trò.
 *
 * @param activeProjectCount số Project đang hoạt động hiển thị trong phạm vi của người dùng
 * @param distinctActiveMemberCount số thành viên đang hoạt động, đủ điều kiện và không trùng nhau của Mentor; các vai trò khác nhận giá trị 0
 */
public record ProjectDashboardSummary(long activeProjectCount, long distinctActiveMemberCount) {
}
