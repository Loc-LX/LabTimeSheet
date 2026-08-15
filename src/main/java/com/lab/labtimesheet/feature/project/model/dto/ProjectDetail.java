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
 */
public record ProjectDetail(
        long id,
        String name,
        String description,
        String status,
        LocalDate startDate,
        LocalDate endDate,
        String mentorName,
        String leaderName,
        boolean canManage) {
}
