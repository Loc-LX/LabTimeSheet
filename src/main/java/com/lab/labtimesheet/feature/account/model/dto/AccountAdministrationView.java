package com.lab.labtimesheet.feature.account.model.dto;

import com.lab.labtimesheet.feature.account.model.AccountStatus;
import com.lab.labtimesheet.feature.account.model.GlobalRole;
import com.lab.labtimesheet.feature.account.model.InternshipStatus;
import java.time.LocalDate;

/**
 * Non-secret Account-owned facts shown to an active Admin.
 *
 * @param id account identifier
 * @param email normalized login identity
 * @param displayName user-facing name
 * @param role immutable global role
 * @param accountStatus authentication lifecycle state
 * @param studentCode Intern student code, or {@code null} for a non-Intern
 * @param internshipStartDate inclusive Intern start date, or {@code null} for a non-Intern
 * @param internshipEndDate inclusive Intern end date, or {@code null} for a non-Intern
 * @param internshipStatus Intern lifecycle state, or {@code null} for a non-Intern
 */
public record AccountAdministrationView(
        long id,
        String email,
        String displayName,
        GlobalRole role,
        AccountStatus accountStatus,
        String studentCode,
        LocalDate internshipStartDate,
        LocalDate internshipEndDate,
        InternshipStatus internshipStatus) {
}
