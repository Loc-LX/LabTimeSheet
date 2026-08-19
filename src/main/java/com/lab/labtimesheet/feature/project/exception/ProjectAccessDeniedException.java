package com.lab.labtimesheet.feature.project.exception;

/**
 * Báo hiệu tra cứu hoặc thao tác Project phải thất bại mà không tiết lộ tài nguyên có tồn tại hay không.
 */
public final class ProjectAccessDeniedException extends RuntimeException {

    /** Tạo tín hiệu từ chối nội bộ; controller thay thông báo bằng nội dung chung an toàn. */
    public ProjectAccessDeniedException() {
        super("Project access denied");
    }
}
