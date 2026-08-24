package com.lab.labtimesheet.feature.project.exception;

/**
 * Signals a Project lookup or operation that must fail without revealing resource existence.
 */
// Tín hiệu nội bộ khi user không thể truy cập Project; Advice sẽ đổi nó thành trang lỗi chung.
public final class ProjectAccessDeniedException extends RuntimeException {

    /** Creates the internal denial signal; controllers replace its message with generic copy. */
    public ProjectAccessDeniedException() {
        super("Project access denied");
    }
}
