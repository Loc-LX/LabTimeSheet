package com.lab.labtimesheet.feature.project.model;

import com.lab.labtimesheet.feature.project.model.entity.ProjectMembershipEntity;
import java.time.Instant;

/**
 * [I2-PRJ-03] Dữ liệu trung gian cho luồng thay Leader rồi đóng membership Leader cũ.
 *
 * <p>Aggregate chỉ tạo record này sau khi đã kiểm tra replacement là membership hiện tại hợp lệ.
 * Service sẽ flush nhiệm kỳ cũ, mở nhiệm kỳ mới, rồi mới đóng membership cũ trong cùng transaction.
 *
 * @param departing membership hiện tại của Leader sắp rời Project
 * @param replacement membership hiện tại được chọn làm Leader mới
 * @param effectiveAt thời điểm liền kề giữa nhiệm kỳ cũ và mới
 */
public record ProjectLeaderRemoval(
        ProjectMembershipEntity departing,
        ProjectMembershipEntity replacement,
        Instant effectiveAt) {
}
