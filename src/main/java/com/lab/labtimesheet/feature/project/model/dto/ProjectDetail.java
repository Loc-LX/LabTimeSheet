package com.lab.labtimesheet.feature.project.model.dto;

import java.time.LocalDate;

/**
 * Authorized Project detail for server-rendered pages.
 *
 * @param id Project identifier
 * @param name display name
 * @param description optional description
 * @param status lifecycle status
 * @param startDate inclusive Project start date
 * @param endDate inclusive Project end date
 * @param mentorName owning Mentor display name
 * @param leaderName current Leader display name, or null after completion closes leadership
 * @param canManage whether the viewer is the owner and the Project remains mutable
 * @param viewerIsCurrentLeader whether the viewer is the stored current Leader of this open Project
 */
// DTO dữ liệu chi tiết Project đã qua kiểm tra quyền xem.
// ProjectQueryService dùng nó để truyền trạng thái, Leader và thành viên cần thiết sang màn Overview.
public record ProjectDetail(
        long id,
        String name,
        String description,
        String status,
        LocalDate startDate,
        LocalDate endDate,
        String mentorName,
        String leaderName,
        boolean canManage,
        boolean viewerIsCurrentLeader) {

    /**
     * Backward-compatible constructor for callers that do not render Leader-specific actions.
     *
     * <p>Those callers receive the safe default of no current-Leader capability; the canonical
     * constructor is populated by the Project query boundary for real page requests.</p>
     */
    public ProjectDetail(
            long id,
            String name,
            String description,
            String status,
            LocalDate startDate,
            LocalDate endDate,
            String mentorName,
            String leaderName,
            boolean canManage) {
        this(id, name, description, status, startDate, endDate,
                mentorName, leaderName, canManage, false);
    }
}
