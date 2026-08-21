package com.lab.labtimesheet.feature.project.model.dto;

import java.util.Set;

/**
 * Non-entity Project facts needed when an account terminal transition is evaluated.
 *
 * @param currentLeader whether the Intern currently leads a planned or active Project
 * @param membershipIds all retained Project membership identifiers belonging to the Intern
 */
public record ProjectInternshipGuard(boolean currentLeader, Set<Long> membershipIds) {

    public ProjectInternshipGuard {
        membershipIds = Set.copyOf(membershipIds);
    }
}
