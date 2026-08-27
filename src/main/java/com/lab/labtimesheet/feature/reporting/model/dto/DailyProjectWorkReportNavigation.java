package com.lab.labtimesheet.feature.reporting.model.dto;

/**
 * Server-derived capability state for the Daily Project Work Report sidebar entry.
 *
 * <p>The capability is intentionally Boolean-only. The shared shell needs to know whether an
 * entry is available, while the full ordered Project list belongs only to the Daily landing
 * controller. Templates consume this server-derived result and never infer capability from
 * membership or a global role alone.</p>
 *
 * @param available true when at least one current eligible Leader Project exists
 */
public record DailyProjectWorkReportNavigation(boolean available) {
}
