package com.lab.labtimesheet.feature.attendance.model.dto;

/**
 * Mentor leave decision over a single pending request.
 *
 * @param approved whether the request is approved (otherwise rejected)
 * @param decisionNote optional Mentor note recorded with the decision
 */
public record LeaveDecisionCommand(boolean approved, String decisionNote) {
}