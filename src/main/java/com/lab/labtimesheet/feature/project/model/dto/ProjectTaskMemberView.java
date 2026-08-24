package com.lab.labtimesheet.feature.project.model.dto;

import java.time.Instant;

/**
 * Current eligible Project member exposed to Task services without sharing Project entities.
 *
 * @param membershipId active membership-interval identifier used by Task foreign keys
 * @param userId Intern account identifier used for actor authorization
 * @param displayName current Account display name
 * @param joinedAt inclusive start instant of the retained membership interval
 */
// DTO thành viên hiện tại dùng làm lựa chọn assignee hoặc người nhận chuyển Task.
// Chỉ chứa dữ liệu cần cho feature Task, không đưa cả ProjectEntity qua ranh giới feature.
public record ProjectTaskMemberView(
        long membershipId,
        long userId,
        String displayName,
        Instant joinedAt) {

}
