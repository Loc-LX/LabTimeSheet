package com.lab.labtimesheet.feature.account.model.dto;

import java.time.LocalDate;

import com.lab.labtimesheet.feature.account.model.GlobalRole;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.format.annotation.DateTimeFormat;

/** Validated, non-secret Admin input for creating an immutable-role account. */
public class CreateAccountForm {
    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email address")
    @Size(max = 320, message = "Email must contain at most 320 characters")
    private String email;

    @NotBlank(message = "Display name is required")
    @Size(max = 120, message = "Display name must contain at most 120 characters")
    private String displayName;

    @NotNull(message = "Role is required")
    private GlobalRole role;

    @Size(max = 64, message = "Student code must contain at most 64 characters")
    private String studentCode;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate internshipStart;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate internshipEnd;

    /**
     * Validates the role-dependent internship fields and their inclusive date ordering.
     *
     * @return {@code true} when Intern details are complete, or absent for non-Intern roles
     */
    @AssertTrue(message = "Intern details are required for Intern accounts and must use a valid date range")
    public boolean isInternDetailsValid() {
        if (role == null) {
            return true;
        }
        if (role != GlobalRole.INTERN) {
            return !hasText(studentCode) && internshipStart == null && internshipEnd == null;
        }
        return hasText(studentCode) && internshipStart != null && internshipEnd != null
                && !internshipEnd.isBefore(internshipStart);
    }

    /**
     * Converts validated browser input to the account service command.
     *
     * @return normalized service command
     */
    public CreateAccountCommand toCommand() {
        return new CreateAccountCommand(email, displayName, role, clean(studentCode), internshipStart, internshipEnd);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String clean(String value) {
        return hasText(value) ? value.trim() : null;
    }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email == null ? null : email.trim(); }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName == null ? null : displayName.trim(); }
    public GlobalRole getRole() { return role; }
    public void setRole(GlobalRole role) { this.role = role; }
    public String getStudentCode() { return studentCode; }
    public void setStudentCode(String studentCode) { this.studentCode = studentCode; }
    public LocalDate getInternshipStart() { return internshipStart; }
    public void setInternshipStart(LocalDate internshipStart) { this.internshipStart = internshipStart; }
    public LocalDate getInternshipEnd() { return internshipEnd; }
    public void setInternshipEnd(LocalDate internshipEnd) { this.internshipEnd = internshipEnd; }
}
