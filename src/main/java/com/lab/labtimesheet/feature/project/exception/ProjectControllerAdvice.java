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
// Khi handler/controller hoặc Service ném lỗi Project không được bắt tại form, DispatcherServlet chuyển exception
// qua HandlerExceptionResolver và tìm @ControllerAdvice này. Advice dựng ModelAndView, sau đó ViewResolver/Thymeleaf
// render error/generic thành HTTP response. Không phải exception nào cũng đến đây: CSRF/filter lỗi xảy ra trước
// DispatcherServlet, còn lỗi Bean Validation của POST được Controller xử lý bằng BindingResult và trả lại form.
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
        // Access denied dùng cùng response 404 với "không tồn tại" để không cho client suy ra Project ID hợp lệ,
        // owner hay membership. Đây là nhánh fallback cho access lỗi không được Controller xử lý cục bộ.
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
        // Rule violation không còn đủ ngữ cảnh để render lại form (ví dụ Project vừa đổi trạng thái hoặc dữ liệu
        // stale ở mutation khác) nên trả 409 generic. Riêng create, Controller bắt lỗi known field rule trước để
        // addFieldError và giữ input; lỗi không map được mới rơi xuống advice này.
        return genericError(
                HttpStatus.CONFLICT,
                "Project request could not be completed",
                "Review the Project and try again.");
    }

    // [Dựng model trang lỗi]
    // Đặt HTTP status và dữ liệu mà template error/generic đã thống nhất để render ra trình duyệt.
    private static ModelAndView genericError(HttpStatus status, String title, String message) {
        // Model chỉ chứa copy an toàn đã định nghĩa sẵn, tuyệt đối không dùng exception.getMessage() vì message có
        // thể chứa ID/SQL/state nội bộ. ModelAndView giữ status HTTP, tên view logic và attributes cho Thymeleaf.
        var error = new ModelAndView("error/generic");
        error.setStatus(status);
        error.addObject("errorStatus", status.value());
        error.addObject("errorTitle", title);
        error.addObject("errorMessage", message);
        return error;
    }
}
