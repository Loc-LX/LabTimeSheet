package com.lab.labtimesheet.feature.reporting.service;

import com.lab.labtimesheet.feature.reporting.model.dto.ProjectTaskReportData;
import java.time.LocalDate;

/**
 * Project/Task report dataset boundary implemented by the Project and Task features.
 *
 * <p>The Project feature owns membership, ownership, and leadership queries and enforces that the
 * viewer is the Admin, the owning Mentor, the current Leader, or a member of the Project before any
 * rows are returned. The Task feature owns the work-log query that supplies per-Task and per-member
 * logged minutes within the requested work-date range.
 */
public interface ProjectTaskReportDataProvider {

    /**
     * Returns the authorized Project/Task report dataset for the viewer.
     *
     * @param viewerUserId authenticated account identifier of the viewer
     * @param viewerIsAdmin whether the viewer is a global Admin
     * @param projectId target Project identifier
     * @param from inclusive first work date
     * @param to inclusive last work date
     * @return report rows and visibility flags for the target Project
     * @throws org.springframework.security.access.AccessDeniedException when the viewer may not view
     *     the Project report
     */
    ProjectTaskReportData data(long viewerUserId, boolean viewerIsAdmin, long projectId, LocalDate from, LocalDate to);
}