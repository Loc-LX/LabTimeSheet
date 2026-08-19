package com.lab.labtimesheet.feature.account.model.dto;

import java.time.LocalDate;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

/** Validated, non-secret Admin input for editing an immutable-role account's fields. */
@Getter
public class EditAccountForm {
    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email address")
    @Size(max = 320, message = "Email must contain at most 320 characters")
    private String email;

    @NotBlank(message = "Display name is required")
    @Size(max = 120, message = "Display name must contain at most 120 characters")
    private String displayName;

    @Size(max = 64, message = "Student code must contain at most 64 characters")
    @Setter
    private String studentCode;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @Setter
    private LocalDate internshipStart;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @Setter
    private LocalDate internshipEnd;

    @NotNull(message = "Account version is required")
    @Setter
    private Long userVersion;

    @Setter
    private Long profileVersion;

    /**
     * Pre-fills the form from an account-detail projection so the edit page mirrors the current
     * values and carries the optimistic-lock versions the submit flow must honor.
     *
     * @param detail current account detail
     * @return pre-filled edit form
     */
    public static EditAccountForm from(AccountAdminDetail detail) {
        EditAccountForm form = new EditAccountForm();
        form.setEmail(detail.email());
        form.setDisplayName(detail.displayName());
        form.setStudentCode(detail.studentCode());
        form.setInternshipStart(detail.internshipStartDate());
        form.setInternshipEnd(detail.internshipEndDate());
        form.setUserVersion(detail.userVersion());
        form.setProfileVersion(detail.profileVersion());
        return form;
    }

    public void setEmail(String email) {
        this.email = email == null ? null : email.trim();
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName == null ? null : displayName.trim();
    }
}