package com.lab.labtimesheet.feature.project.model.dto;

/**
 * Thông tin người dùng đang hoạt động và đã xác thực được cung cấp cho phần web của Project.
 *
 * @param userId mã tài khoản ổn định
 * @param role tên vai trò toàn cục bất biến
 */
public record ProjectActorView(long userId, String role) {
}
