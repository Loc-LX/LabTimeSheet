package com.lab.labtimesheet.feature.project.model;

import com.lab.labtimesheet.feature.project.model.entity.ProjectMembershipEntity;
import java.time.Instant;

/**
 * [I2-PRJ-04] Dữ liệu trung gian cho việc chuyển Task rồi đóng membership của member thường.
 *
 * <p>Aggregate chỉ tạo dữ liệu này sau khi đã xác nhận member đang hiện tại và Leader nhận Task
 * cũng đang hiện tại. Service gọi boundary Task trước, sau đó mới hoàn tất đóng membership.
 *
 * @param departing membership của Intern sắp rời Project
 * @param transferTarget membership Leader hiện tại nhận các Task chưa hoàn thành
 * @param effectiveAt thời điểm đóng membership do server cấp
 */
public record ProjectMemberRemoval(
        ProjectMembershipEntity departing,
        ProjectMembershipEntity transferTarget,
        Instant effectiveAt) {
}
