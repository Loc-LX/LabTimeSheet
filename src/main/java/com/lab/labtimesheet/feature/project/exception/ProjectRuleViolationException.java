package com.lab.labtimesheet.feature.project.exception;

/**
 * Signals that a Project lifecycle, eligibility, membership, or leadership rule rejected a
 * mutation without committing a partial aggregate change.
 */
// Tín hiệu một business rule của Project bị vi phạm, ví dụ thêm trùng member hoặc đổi Leader không hợp lệ.
// Transaction sẽ rollback để database không lưu dở một phần thay đổi.
public final class ProjectRuleViolationException extends RuntimeException {

    /**
     * Creates a domain-rule failure whose message may be shown only by a known safe form flow.
     *
     * @param message actionable domain validation message without protected identifiers
     */
    public ProjectRuleViolationException(String message) {
        super(message);
    }
}
