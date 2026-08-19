package com.lab.labtimesheet.feature.project.exception;

import com.lab.labtimesheet.feature.project.controller.ProjectController;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.servlet.ModelAndView;

/**
 * Chuyển lỗi phân quyền và vòng đời Project chưa được xử lý thành hợp đồng lỗi hiển thị phía máy
 * chủ dùng chung, không tiết lộ thông tin.
 *
 * <p>Feature Reporting/UI cung cấp {@code error/generic}. Model ổn định gồm {@code errorStatus},
 * {@code errorTitle} và {@code errorMessage}; không trường nào lấy trực tiếp từ thông báo của exception.
 */
@ControllerAdvice(assignableTypes = ProjectController.class)
public class ProjectControllerAdvice {

    /**
     * Che giấu việc Project hoặc tài nguyên lồng bên trong được yêu cầu có tồn tại hay không.
     *
     * @return view lỗi chung với HTTP 404 và nội dung an toàn
     */
    @ExceptionHandler(ProjectAccessDeniedException.class)
    public ModelAndView accessDenied() {
        return genericError(
                HttpStatus.NOT_FOUND,
                "Project unavailable",
                "The requested Project could not be found or is not available to you.");
    }

    /**
     * Báo cáo yêu cầu Project cũ hoặc không hợp lệ chưa được xử lý mà không lộ chi tiết aggregate.
     * Lỗi kiểm tra biểu mẫu đã biết được controller xử lý trước khi tới fallback này.
     *
     * @return view lỗi chung với HTTP 409 và nội dung an toàn
     */
    @ExceptionHandler(ProjectRuleViolationException.class)
    public ModelAndView conflict() {
        return genericError(
                HttpStatus.CONFLICT,
                "Project request could not be completed",
                "Review the Project and try again.");
    }

    /** Tạo phản hồi lỗi chung với status và nội dung cố định do controller advice cung cấp. */
    private static ModelAndView genericError(HttpStatus status, String title, String message) {
        var error = new ModelAndView("error/generic");
        error.setStatus(status);
        error.addObject("errorStatus", status.value());
        error.addObject("errorTitle", title);
        error.addObject("errorMessage", message);
        return error;
    }
}
