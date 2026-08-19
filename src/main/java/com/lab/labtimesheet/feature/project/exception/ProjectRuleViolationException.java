package com.lab.labtimesheet.feature.project.exception;

/**
 * Báo hiệu quy tắc vòng đời, điều kiện, thành viên hoặc Leader của Project đã từ chối thay đổi mà
 * không commit một phần aggregate.
 */
public final class ProjectRuleViolationException extends RuntimeException {

    /**
     * Tạo lỗi quy tắc nghiệp vụ; thông báo chỉ được hiển thị trong luồng biểu mẫu an toàn đã biết.
     *
     * @param message nội dung kiểm tra có thể hành động, không chứa mã cần bảo vệ
     */
    public ProjectRuleViolationException(String message) {
        super(message);
    }
}
