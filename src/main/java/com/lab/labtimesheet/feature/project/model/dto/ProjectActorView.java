package com.lab.labtimesheet.feature.project.model.dto;

/**
 * Active authenticated actor information exposed to Project web consumers.
 *
 * @param userId stable account identifier
 * @param role immutable global role name
 */
// DTO nhỏ chứa ID và role của account đã đăng nhập, dùng để quyết định quyền trong các màn Project.
// ProjectQueryService tạo object này sau khi xác nhận account còn ACTIVE.
public record ProjectActorView(long userId, String role) {
}
