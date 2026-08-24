package com.lab.labtimesheet.feature.project.model.dto;

import java.util.List;

/**
 * One bounded, role-authorized Project-list page and its continuation state.
 *
 * <p>The page number is one-based for the MVC route, while the service converts it to the
 * zero-based Spring {@code Pageable} representation. The continuation flags are derived from
 * the same stable Project ordering as the rows and allow callers to reach every authorized page
 * without requesting an unbounded collection.</p>
 *
 * @param projects authorized Project summaries in descending update order
 * @param pageNumber one-based page number exposed to the UI
 * @param pageSize maximum number of rows requested from persistence
 * @param hasPrevious whether a preceding page exists
 * @param hasNext whether a following page exists
 */
// DTO chứa một trang danh sách Project và thông tin phân trang cho giao diện.
// Controller gắn object này vào Model để Thymeleaf hiển thị nút chuyển trang đúng giới hạn.
public record ProjectListPage(
        List<ProjectSummary> projects,
        int pageNumber,
        int pageSize,
        boolean hasPrevious,
        boolean hasNext) {

    /**
     * Copies the page rows and validates the externally visible pagination metadata.
     *
     * @param projects authorized page rows
     * @param pageNumber one-based page number
     * @param pageSize positive bounded page size
     * @param hasPrevious preceding-page flag
     * @param hasNext following-page flag
     */
    public ProjectListPage {
        if (projects == null) {
            throw new IllegalArgumentException("Project page rows are required");
        }
        if (pageNumber < 1 || pageSize < 1) {
            throw new IllegalArgumentException("Project page metadata must be positive");
        }
        projects = List.copyOf(projects);
    }
}
