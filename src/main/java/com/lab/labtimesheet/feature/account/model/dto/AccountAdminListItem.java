package com.lab.labtimesheet.feature.account.model.dto;

import java.time.Instant;
import java.time.LocalDate;

import com.lab.labtimesheet.feature.account.model.AccountStatus;
import com.lab.labtimesheet.feature.account.model.GlobalRole;
import com.lab.labtimesheet.feature.account.model.InternshipStatus;

/**
 * Non-secret account row for the Admin account listing, including the owning internship summary.
 *
 * @param id account identifier
 * @param email normalized email address
 * @param displayName user-facing name
 * @param role immutable global role
 * @param status current authentication lifecycle state
 * @param internshipStatus internship lifecycle state, {@code null} for non-Intern roles
 * @param internshipEndDate inclusive internship end date, {@code null} for non-Intern roles
 * @param lastLoginAt most recent successful authentication, {@code null} when never signed in
 */
public record AccountAdminListItem(
        long id,
        String email,
        String displayName,
        GlobalRole role,
        AccountStatus status,
        InternshipStatus internshipStatus,
        LocalDate internshipEndDate,
        Instant lastLoginAt) {
}