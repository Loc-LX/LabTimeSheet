package com.lab.labtimesheet.feature.attendance.model.dto;

/**
 * A Mentor's approve-or-reject decision for one pending correction.
 *
 * @param approved {@code true} approves the proposed checkout, {@code false} rejects it
 * @param decisionNote optional Mentor note recorded verbatim on the immutable transition
 */
public record CorrectionDecisionCommand(boolean approved, String decisionNote) {
}