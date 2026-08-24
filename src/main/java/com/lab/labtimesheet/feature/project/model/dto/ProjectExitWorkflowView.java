package com.lab.labtimesheet.feature.project.model.dto;

import com.lab.labtimesheet.feature.project.model.ProjectExitRequestType;

/**
 * Render-ready pending-exit warning and capability snapshot.
 *
 * <p>These values remain informational. Transfer and Mentor-decision services repeat every
 * authorization, membership, leadership, Task-count, and state check under their mutation locks.</p>
 *
 * @param requestId pending request identifier
 * @param targetMembershipId target membership interval
 * @param requestType whether the Intern requested own exit or the Leader requested removal
 * @param reason retained reason supplied with the request
 * @param targetName current display name for the target
 * @param replacementRequired whether the target is still current Leader
 * @param unfinishedTaskCount current unfinished Task count
 * @param readyForMentorDecision whether the current snapshot satisfies approval guards
 * @param canOpenTransfer whether the authenticated current Leader may open transfer controls
 */
// DTO dữ liệu của trang xử lý exit: yêu cầu, tình trạng Task và các thành viên có thể nhận chuyển giao.
// Controller chỉ render object này; quyết định approve/reject vẫn do ProjectService thực hiện.
// targetName được dùng làm Username; targetStudentCode và targetEmail được hiển thị riêng để không gộp nhiều loại định danh vào một tiêu đề.
public record ProjectExitWorkflowView(
        long requestId,
        long targetMembershipId,
        ProjectExitRequestType requestType,
        String reason,
        String targetName,
        String targetStudentCode,
        String targetEmail,
        boolean replacementRequired,
        long unfinishedTaskCount,
        boolean readyForMentorDecision,
        boolean canOpenTransfer) {
}
