package com.lab.labtimesheet.feature.project.model;

import com.lab.labtimesheet.feature.project.model.entity.ProjectMembershipEntity;
import java.time.Instant;

/**
 * Dữ liệu bàn giao trong transaction giữa việc đóng nhiệm kỳ hiện tại và mở nhiệm kỳ thay thế.
 * Đối tượng này cho phép flush khoảng thời gian cũ trước khi PostgreSQL kiểm tra nhiệm kỳ mới.
 *
 * @param replacement lượt tham gia đang hoạt động trong cùng Project được bổ nhiệm làm Leader
 * @param effectiveAt thời điểm kết thúc/bắt đầu chung của hai nhiệm kỳ liền kề
 */
public record ProjectLeaderChange(ProjectMembershipEntity replacement, Instant effectiveAt) {
}
