package com.lab.labtimesheet.feature.project.model.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Context Project chỉ gồm DTO về phân quyền và vòng đời, được feature Task sử dụng.
 *
 * @param projectId mã Project
 * @param mentorUserId mã người dùng Mentor sở hữu
 * @param status trạng thái vòng đời
 * @param startDate ngày bắt đầu Project, được tính cả ngày này
 * @param endDate ngày kết thúc Project, được tính cả ngày này
 * @param currentLeaderMembershipId mã lượt tham gia của Leader hiện tại, hoặc null sau khi hoàn tất
 * @param activeMembers các lượt tham gia hiện tại đủ điều kiện, rỗng sau khi hoàn tất
 */
public record ProjectTaskContext(
        long projectId,
        long mentorUserId,
        String status,
        LocalDate startDate,
        LocalDate endDate,
        Long currentLeaderMembershipId,
        List<ProjectTaskMemberView> activeMembers) {

    /**
     * Sao chép an toàn danh sách thành viên để bên dùng không thể thay đổi thông tin phân quyền sau khi đã đọc.
     *
     * @param projectId mã Project
     * @param mentorUserId mã người dùng Mentor sở hữu
     * @param status trạng thái vòng đời
     * @param startDate ngày bắt đầu Project, được tính cả ngày này
     * @param endDate ngày kết thúc Project, được tính cả ngày này
     * @param currentLeaderMembershipId mã lượt tham gia của Leader hiện tại, hoặc null sau khi hoàn tất
     * @param activeMembers các lượt tham gia hiện tại đủ điều kiện, được sao chép và không bao giờ null
     */
    public ProjectTaskContext {
        activeMembers = List.copyOf(activeMembers);
    }
}
