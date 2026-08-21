package com.lab.labtimesheet.feature.account.model.dto;

import java.time.Instant;
import java.time.LocalDate;

import com.lab.labtimesheet.feature.account.model.AccountStatus;
import com.lab.labtimesheet.feature.account.model.GlobalRole;
import com.lab.labtimesheet.feature.account.model.InternshipStatus;

/**
 * Non-secret account detail for the Admin account-detail page, including lifecycle timestamps and
 * the owning internship when the role is Intern.
 *
 * @param id account identifier
 * @param email normalized email address
 * @param displayName user-facing name
 * @param role immutable global role
 * @param status current authentication lifecycle state
 * @param activatedAt moment the first password was assigned
 * @param lockedAt moment the account was locked, {@code null} unless locked
 * @param deactivatedAt moment the account was deactivated, {@code null} unless deactivated
 * @param lastLoginAt most recent successful authentication
 * @param createdByEmail email of the Admin who created the account, {@code null} for the bootstrap Admin
 * @param studentCode Intern student code, {@code null} for non-Intern roles
 * @param internshipStartDate inclusive internship start date, {@code null} for non-Intern roles
 * @param internshipEndDate inclusive internship end date, {@code null} for non-Intern roles
 * @param internshipStatus internship lifecycle state, {@code null} for non-Intern roles
 * @param userVersion optimistic-lock version of the account row for the edit form
 * @param profileVersion optimistic-lock version of the Intern profile row, {@code null} for non-Intern roles
 */
public record AccountAdminDetail(
        long id,
        String email,
        String displayName,
        GlobalRole role,
        AccountStatus status,
        Instant activatedAt,
        Instant lockedAt,
        Instant deactivatedAt,
        Instant lastLoginAt,
        String createdByEmail,
        String studentCode,
        LocalDate internshipStartDate,
        LocalDate internshipEndDate,
        InternshipStatus internshipStatus,
        long userVersion,
        Long profileVersion) {
}