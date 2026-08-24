package com.lab.labtimesheet.feature.project.model.dto;

/**
 * Immutable scalar route from an exit request to its owning Project.
 *
 * <p>The request entity is first loaded under its pessimistic lock after the Account and Project
 * lock order has been established.</p>
 *
 * @param projectId owning Project identifier
 * @param requesterUserId immutable Intern account that created the request
 */
// DTO nội bộ cho route một exit request về Project và người tạo request.
// Service lấy nó trước để khóa đúng phạm vi dữ liệu trước khi approve, reject hoặc cancel request.
public record ProjectExitRequestRoute(long projectId, long requesterUserId) {
}
