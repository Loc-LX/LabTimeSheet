package com.lab.labtimesheet.feature.identity.model.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Request-local password-reset form; cleartext values are never retained after an error response. */
@Getter
@Setter
public class PasswordResetForm {
    @NotBlank(message = "This password-reset link is invalid or no longer usable")
    private String token;

    @NotBlank(message = "Password is required")
    @Size(min = 12, max = 128, message = "Password must contain 12 through 128 characters")
    private String password;

    @NotBlank(message = "Password confirmation is required")
    private String confirmPassword;

    /**
     * Checks whether both cleartext password values match while the request-local form is being validated.
     *
     * @return {@code true} only when both values are non-null and equal
     */
    @AssertTrue(message = "Passwords do not match")
    public boolean isPasswordConfirmed() {
        return password != null && password.equals(confirmPassword);
    }

    /** Clears both cleartext values before a form is rendered again. */
    public void clearPasswords() {
        password = null;
        confirmPassword = null;
    }
}
