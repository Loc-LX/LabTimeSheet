package com.lab.labtimesheet.feature.project.model.dto;

import java.time.LocalDate;
import java.time.Instant;

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
 * @param canDelete whether the viewer owns a truly empty PLANNED Project that may be deleted
 * @param viewerIsCurrentLeader whether the viewer is the stored current Leader of this open Project
 * @param cancellationReason retained explanation for cancellation
 * @param cancelledByMentorName Mentor who cancelled the Project
 * @param cancelledAt server timestamp at cancellation
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
        boolean canDelete,
        boolean viewerIsCurrentLeader,
        String cancellationReason,
        String cancelledByMentorName,
        Instant cancelledAt) {

    /** Backward-compatible constructor before cancellation details were rendered. */
    public ProjectDetail(long id, String name, String description, String status, LocalDate startDate,
                         LocalDate endDate, String mentorName, String leaderName, boolean canManage,
                         boolean canDelete, boolean viewerIsCurrentLeader) {
        this(id, name, description, status, startDate, endDate, mentorName, leaderName,
                canManage, canDelete, viewerIsCurrentLeader, null, null, null);
    }

    /**
     * Preserves callers that supply management and Leader capabilities but no deletion proof.
     *
     * @param id Project identifier
     * @param name display name
     * @param description optional description
     * @param status lifecycle status
     * @param startDate inclusive Project start date
     * @param endDate inclusive Project end date
     * @param mentorName owning Mentor display name
     * @param leaderName current Leader display name, or null
     * @param canManage whether the viewer is the owner and the Project remains mutable
     * @param viewerIsCurrentLeader whether the viewer is the current Leader
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
            boolean canManage,
            boolean viewerIsCurrentLeader) {
        this(id, name, description, status, startDate, endDate, mentorName, leaderName,
                canManage, false, viewerIsCurrentLeader, null, null, null);
    }

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
                mentorName, leaderName, canManage, false, false, null, null, null);
    }
}
