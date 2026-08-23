package com.lab.labtimesheet.feature.account.model.dto;

import java.time.LocalDate;

/**
 * Admin-authorized account identity and Intern-profile corrections.
 *
 * <p>{@code null} leaves a field unchanged. Email delivery is required when an email is supplied; display name
 * and global role are deliberately absent because neither is an editable identity-correction field.</p>
 *
 * @param email replacement login email, or {@code null}
 * @param studentCode replacement Student Code, or {@code null}
 * @param internshipStart replacement inclusive start date, or {@code null}
 * @param internshipEnd replacement inclusive end date, or {@code null}
 */
public record AccountIdentityCorrection(
        String email,
        String studentCode,
        LocalDate internshipStart,
        LocalDate internshipEnd) {
}
