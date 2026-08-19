package com.lab.labtimesheet.feature.project.model;

/**
 * Thông tin đủ điều kiện do Account cung cấp, được aggregate Project dùng mà không import kiểu
 * persistence của Account.
 *
 * @param userId mã tài khoản Intern
 * @param eligible true chỉ khi cả tài khoản và đợt thực tập đều đang hoạt động tại thời điểm kiểm tra
 */
public record ProjectInternEligibility(long userId, boolean eligible) {

    /**
     * Từ chối mã không hợp lệ trước khi chúng được đưa vào lịch sử thành viên Project.
     *
     * @param userId mã tài khoản Intern
     * @param eligible kết quả xác định đủ điều kiện từ Account service
     */
    public ProjectInternEligibility {
        if (userId <= 0) {
            throw new IllegalArgumentException("Intern user ID must be positive");
        }
    }

    /**
     * Trả về kết quả xác định đủ điều kiện từ Account service.
     *
     * @return true khi Intern được phép tham gia thao tác Project đang yêu cầu
     */
    public boolean isEligible() {
        return eligible;
    }
}
