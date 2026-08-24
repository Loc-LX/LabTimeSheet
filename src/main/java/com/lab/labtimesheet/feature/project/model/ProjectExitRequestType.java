package com.lab.labtimesheet.feature.project.model;

/**
 * Participant shape of a pending membership-exit request.
 */
// Phân biệt Intern tự xin rời với Mentor yêu cầu loại một thành viên khác.
// ProjectExitRequestEntity dùng loại này để kiểm tra người gửi và target có hợp lệ không.
public enum ProjectExitRequestType {
    /** Current Leader requests removal of another current member. */
    LEADER_REMOVAL,
    /** Current member requests to leave the Project. */
    MEMBER_LEAVE
}
