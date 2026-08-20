package com.lab.labtimesheet.feature.account.model.dto;

/**
 * Producer-owned facts required before an Admin can close an Intern lifecycle.
 *
 * <p>Project and Task services calculate these values from their own locked aggregates. The Account service never
 * imports those repositories or treats this DTO as a substitute for their authorization checks.</p>
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
