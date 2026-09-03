package com.lab.labtimesheet.feature.project.exception;

import com.lab.labtimesheet.feature.project.controller.ProjectController;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.ModelAndView;

/**
 * Maps uncaught Project authorization and lifecycle failures to the shared, non-disclosing
 * server-rendered error contract.
 *
 * <p>The Reporting/UI feature supplies {@code error/generic}. Its stable model contains
 * {@code errorStatus}, {@code errorTitle}, and {@code errorMessage}; none is populated from the
 * exception message.
 */
// Bộ xử lý lỗi chung chỉ cho màn hình Project.
// Khi Service hoặc Controller ném lỗi mà form không bắt được, lớp này trả trang lỗi thống nhất.
// Lỗi kiểm tra dữ liệu trên form vẫn do Controller xử lý và hiển thị lại form.
@ControllerAdvice(assignableTypes = ProjectController.class)
public class ProjectControllerAdvice {

    /**
     * Hides whether a requested Project or nested resource exists.
     *
     * @return the shared generic error view with HTTP 404 and safe copy
     */
    // Không có quyền xem project: trả cùng trang 404 như "không tồn tại" để không lộ project có thật.
    @ExceptionHandler(ProjectAccessDeniedException.class)
    public ModelAndView accessDenied() {
        return genericError(
                HttpStatus.NOT_FOUND,
                "Project unavailable",
                "The requested Project could not be found or is not available to you.");
    }

    /**
     * Reports an uncaught stale or invalid Project request without exposing aggregate details.
     * Known form validation failures are handled by the controller before reaching this fallback.
     *
     * @return the shared generic error view with HTTP 409 and safe copy
     */
    // Dữ liệu hoặc trạng thái project không còn khớp với thao tác (ví dụ ai đó vừa sửa trước).
    @ExceptionHandler(ProjectRuleViolationException.class)
    public ModelAndView conflict() {
        return genericError(
                HttpStatus.CONFLICT,
                "Project request could not be completed",
                "Review the Project and try again.");
    }

    // Dựng trang lỗi chung với mã HTTP và nội dung an toàn (không dùng nội dung exception gốc).
    private static ModelAndView genericError(HttpStatus status, String title, String message) {
        var error = new ModelAndView("error/generic");
        error.setStatus(status);
        error.addObject("errorStatus", status.value());
        error.addObject("errorTitle", title);
        error.addObject("errorMessage", message);
        return error;
    }
}
