package com.lab.labtimesheet.feature.account.model.dto;

import java.util.Objects;
import java.util.Optional;

import com.lab.labtimesheet.feature.account.model.AccountStatus;
import com.lab.labtimesheet.feature.account.model.GlobalRole;
import com.lab.labtimesheet.feature.account.model.InternshipStatus;

/**
 * Immutable Account-owned lifecycle snapshot for a Project invitation or membership-exit mutation.
 *
 * <p>The Account service creates this DTO only after locking every requested account in ascending ID order. An
 * Intern's profile is then locked in that same ordered pass and its status is present; Mentor/Admin actor accounts
 * have an empty profile status. The locks are retained by the caller's transaction, so consumers must make their
 * role-specific authorization and Project mutation in that same transaction. No JPA entity, credential, or
 * persistence implementation crosses the feature boundary.</p>
 *
 * @param userId account identifier
 * @param role immutable global role
 * @param accountStatus authentication lifecycle state captured under lock
 * @param internshipStatus profile lifecycle state for an Intern, otherwise empty
 */
public record LockedAccountMutationEligibility(
        long userId,
        GlobalRole role,
        AccountStatus accountStatus,
        Optional<InternshipStatus> internshipStatus) {

    /** Validates the immutable cross-feature lifecycle snapshot and explicit profile-status contract. */
    public LockedAccountMutationEligibility {
        if (userId <= 0) {
            throw new IllegalArgumentException("Account user ID must be positive");
        }
        Objects.requireNonNull(role, "Global role is required");
        Objects.requireNonNull(accountStatus, "Account status is required");
        Objects.requireNonNull(internshipStatus, "Internship status is required");
        if (role == GlobalRole.INTERN && internshipStatus.isEmpty()) {
            throw new IllegalArgumentException("Intern accounts require an internship status");
        }
        if (role != GlobalRole.INTERN && internshipStatus.isPresent()) {
            throw new IllegalArgumentException("Only Intern accounts have an internship status");
        }
    }

    /**
     * Returns whether the captured account is an active Intern eligible for a Project mutation.
     *
     * @return true only while the account and its locked Intern profile are active
     */
    public boolean eligibleForProjectMutation() {
        return role == GlobalRole.INTERN
                && accountStatus == AccountStatus.ACTIVE
                && internshipStatus.orElse(null) == InternshipStatus.ACTIVE;
    }
}
