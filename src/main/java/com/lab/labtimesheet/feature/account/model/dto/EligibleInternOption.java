package com.lab.labtimesheet.feature.account.model.dto;

import java.time.LocalDate;

/**
 * Immutable non-secret selection data for an eligible Intern.
 * The numeric user ID is the internal form submission identity; displayed fields are not authorization identifiers.
 *
 * @param userId persistent account identifier submitted by a consuming form
 * @param displayName user-facing Intern name
 * @param studentCode university student code shown to distinguish Interns
 * @param internshipStart inclusive internship eligibility start date
 * @param internshipEnd inclusive internship eligibility end date
 */
public record EligibleInternOption(
        long userId,
        String displayName,
        String studentCode,
        LocalDate internshipStart,
        LocalDate internshipEnd) {
}
