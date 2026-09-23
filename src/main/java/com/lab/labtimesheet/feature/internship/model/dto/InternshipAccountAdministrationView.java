package com.lab.labtimesheet.feature.internship.model.dto;

import java.time.LocalDate;

import com.lab.labtimesheet.feature.identity.model.AccountStatus;
import com.lab.labtimesheet.feature.internship.model.InternshipStatus;
import com.lab.labtimesheet.platform.model.GlobalRole;

/** Non-secret identity and optional Intern-profile facts composed for Admin account screens. */
public record InternshipAccountAdministrationView(
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
