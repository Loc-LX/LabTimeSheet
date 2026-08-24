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
// Bộ xử lý lỗi chung chỉ dành cho ProjectController.
// Khi Controller hoặc Service ném lỗi Project không được bắt tại form, Spring chuyển tới đây
// để trả trang lỗi an toàn thay vì lộ ID, trạng thái hoặc chi tiết Project không có quyền xem.
@ControllerAdvice(assignableTypes = ProjectController.class)
public class ProjectControllerAdvice {

    /**
     * Hides whether a requested Project or nested resource exists.
     *
     * @return the shared generic error view with HTTP 404 and safe copy
     */
    // [Ẩn Project không được phép truy cập]
    // Trả cùng một trang 404 cho cả trường hợp không tồn tại và không có quyền, tránh lộ dữ liệu Project.
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
    // [Trả lỗi xung đột nghiệp vụ]
    // Ví dụ dữ liệu vừa bị người khác thay đổi hoặc trạng thái Project không còn phù hợp với thao tác gửi lên.
    @ExceptionHandler(ProjectRuleViolationException.class)
    public ModelAndView conflict() {
        return genericError(
                HttpStatus.CONFLICT,
                "Project request could not be completed",
                "Review the Project and try again.");
    }

    // [Dựng model trang lỗi]
    // Đặt HTTP status và dữ liệu mà template error/generic đã thống nhất để render ra trình duyệt.
    private static ModelAndView genericError(HttpStatus status, String title, String message) {
        var error = new ModelAndView("error/generic");
        error.setStatus(status);
        error.addObject("errorStatus", status.value());
        error.addObject("errorTitle", title);
        error.addObject("errorMessage", message);
        return error;
    }
}
