package com.lab.labtimesheet.feature.reporting.model.dto;

import com.lab.labtimesheet.feature.project.model.dto.ProjectSummary;
import java.util.List;

/**
 * Server-derived capability state for the Daily Project Work Report sidebar entry.
 *
 * <p>The list is already restricted to open Projects currently led by the authenticated Intern.
 * Templates consume only this immutable result and never infer capability from membership or a
 * global role alone.</p>
 *
 * @param projects current eligible Projects led by the authenticated Intern
 */
public record DailyProjectWorkReportNavigation(List<ProjectSummary> projects) {

    /** Defensive copy prevents view rendering from changing the authorization snapshot. */
    public DailyProjectWorkReportNavigation {
        projects = List.copyOf(projects);
    }

    /** @return true when the actor has at least one current eligible Leader Project */
    public boolean available() {
        return !projects.isEmpty();
    }
}
