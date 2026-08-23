package com.lab.labtimesheet.feature.project.exception;

/**
 * Signals that a Project lifecycle, eligibility, membership, or leadership rule rejected a
 * mutation without committing a partial aggregate change.
 */
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
