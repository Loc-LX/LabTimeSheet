package com.lab.labtimesheet.feature.internship.service;

import com.lab.labtimesheet.feature.internship.model.dto.InternshipLifecycleGuard;

/**
 * Supplies producer-owned Project and Task facts required by the Internship lifecycle.
 *
 * <p>The Internship module owns this contract and decides how the facts affect a terminal action.
 * Implementations may read another module's state, but never receive an Internship decision from
 * their caller.</p>
 */
public interface InternshipLifecycleReadiness {

    /** Returns a read-only snapshot for explaining terminal readiness in the account UI. */
    InternshipLifecycleGuard preview(long internUserId);

    /**
     * Returns terminal-readiness facts after locking their producer-owned state.
     *
     * <p>The caller must already hold the target Account and Intern-profile locks. The
     * implementation retains its locks in the caller's transaction so the result stays stable
     * through the Internship state change.</p>
     */
    InternshipLifecycleGuard lockForTerminalAction(long internUserId);
}
