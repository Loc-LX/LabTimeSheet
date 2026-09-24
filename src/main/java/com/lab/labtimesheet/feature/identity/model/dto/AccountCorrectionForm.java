package com.lab.labtimesheet.feature.identity.model.dto;

import java.time.LocalDate;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

/**
 * Safe Admin form input for the narrow account identity-correction contract.
 *
 * <p>The form intentionally excludes display name and global role. Blank values mean unchanged
 * for optional corrections; AccountService remains the authority for role-dependent fields,
 * uniqueness, lifecycle, and required email delivery.</p>
 */
@Getter
@Setter
public class AccountCorrectionForm {

    @Email(message = "Enter a valid email address")
    @Size(max = 320, message = "Email must contain at most 320 characters")
    private String email;

    @Size(max = 64, message = "Student code must contain at most 64 characters")
    private String studentCode;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate internshipStart;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate internshipEnd;

    /**
     * Creates a form retaining the non-secret editable values from an account projection.
     *
     * @param currentEmail current login identity
     * @param emailEditable whether the account lifecycle permits email correction
     * @param currentStudentCode current Student Code, if the account is an Intern
     * @param studentCodeEditable whether the internship lifecycle permits Student Code correction
     * @param currentInternshipStart current inclusive internship start date
     * @param currentInternshipEnd current inclusive internship end date
     * @param internshipDatesEditable whether the internship lifecycle permits date correction
     */
    public AccountCorrectionForm(
            String currentEmail,
            boolean emailEditable,
            String currentStudentCode,
            boolean studentCodeEditable,
            LocalDate currentInternshipStart,
            LocalDate currentInternshipEnd,
            boolean internshipDatesEditable) {
        email = emailEditable ? currentEmail : null;
        studentCode = studentCodeEditable ? currentStudentCode : null;
        internshipStart = internshipDatesEditable ? currentInternshipStart : null;
        internshipEnd = internshipDatesEditable ? currentInternshipEnd : null;
    }

    /** Creates an empty correction form for binding and validation tests. */
    public AccountCorrectionForm() {
    }

    /**
     * Converts blank-safe browser values to the public Account service command.
     *
     * @return normalized identity correction command
     */
    public AccountIdentityCorrection toCorrection() {
        return new AccountIdentityCorrection(clean(email), clean(studentCode), internshipStart, internshipEnd);
    }

    /**
     * Trims the optional email while retaining null for an omitted correction.
     *
     * @param email browser-supplied email value
     */
    public void setEmail(String email) {
        this.email = email == null ? null : email.trim();
    }

    /**
     * Trims the optional Student Code while retaining null for an omitted correction.
     *
     * @param studentCode browser-supplied Student Code value
     */
    public void setStudentCode(String studentCode) {
        this.studentCode = studentCode == null ? null : studentCode.trim();
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

}
