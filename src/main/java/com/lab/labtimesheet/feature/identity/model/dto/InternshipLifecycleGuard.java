package com.lab.labtimesheet.feature.identity.model.dto;

/**
 * Producer-owned facts required before an Admin can close an Intern lifecycle.
 *
 * <p>Project and Task services calculate these values from their own aggregates. A read-only snapshot may explain
 * UI readiness, but a terminal mutation must recompute the DTO while retaining the Account/profile and Project
 * locks through the Account transition. The Account service never imports foreign persistence.</p>
 *
 * @param currentLeader whether the Intern is a current Project Leader in any Project
 * @param unfinishedTaskCount number of unfinished, non-deleted Tasks currently owned by the Intern
 */
public record InternshipLifecycleGuard(boolean currentLeader, long unfinishedTaskCount) {

    /** Validates that the producer supplied a real aggregate count. */
    public InternshipLifecycleGuard {
        if (unfinishedTaskCount < 0) {
            throw new IllegalArgumentException("Unfinished Task count cannot be negative");
        }
    }

    /**
     * Returns whether both terminal-action readiness conditions are satisfied.
     *
     * @return true only when the Intern is not a current Leader and owns no unfinished Tasks
     */
    public boolean readyForTerminalAction() {
        return !currentLeader && unfinishedTaskCount == 0;
    }
}
