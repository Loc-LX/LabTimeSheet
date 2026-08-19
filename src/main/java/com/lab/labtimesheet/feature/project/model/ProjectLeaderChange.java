package com.lab.labtimesheet.feature.project.model;

import com.lab.labtimesheet.feature.project.model.entity.ProjectMembershipEntity;
import java.time.Instant;

/**
 * [I1-PRJ-03, I2-PRJ-01, I2-PRJ-02] Dữ liệu bàn giao trong transaction giữa việc đóng nhiệm kỳ hiện
 * tại và mở nhiệm kỳ thay thế.
 * Đối tượng này cho phép flush khoảng thời gian cũ trước khi PostgreSQL kiểm tra nhiệm kỳ mới.
 * Record chỉ giữ thành viên thay thế; membership của Leader cũ và các khóa phân công Task không
 * được chuyển theo bàn giao.
 *
 * @param replacement lượt tham gia đang hoạt động trong cùng Project được bổ nhiệm làm Leader
 * @param effectiveAt thời điểm kết thúc/bắt đầu chung của hai nhiệm kỳ liền kề
 */
public record ProjectLeaderChange(ProjectMembershipEntity replacement, Instant effectiveAt) {
}
