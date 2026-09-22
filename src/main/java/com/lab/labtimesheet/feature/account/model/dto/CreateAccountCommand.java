package com.lab.labtimesheet.feature.account.model.dto;

import java.time.LocalDate;

import com.lab.labtimesheet.platform.model.GlobalRole;

/**
 * Account-service creation input; internship fields are required only for the Intern role.
 *
 * @param email account email
 * @param displayName user-facing name
 * @param role immutable global role
 * @param studentCode Intern student code, otherwise {@code null}
 * @param internshipStart inclusive Intern start date, otherwise {@code null}
 * @param internshipEnd inclusive Intern end date, otherwise {@code null}
 */
public record CreateAccountCommand(
        String email,
        String displayName,
        GlobalRole role,
        String studentCode,
        LocalDate internshipStart,
        LocalDate internshipEnd) {
}
