package com.lab.labtimesheet.feature.project.model.dto;

/**
 * Thành viên Project hiện tại và đủ điều kiện được cung cấp cho dịch vụ Task mà không chia sẻ entity Project.
 *
 * @param membershipId mã khoảng thời gian tham gia hiện tại được dùng làm khóa ngoại của Task
 * @param userId mã tài khoản Intern dùng để phân quyền người thực hiện
 * @param displayName tên hiển thị hiện tại của Account
 */
public record ProjectTaskMemberView(long membershipId, long userId, String displayName) {
}
