package com.lab.labtimesheet.feature.account.model.dto;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Validated activation submission. Password fields remain request-local and are never repopulated by the view.
 */
public class ActivationForm {
    @NotBlank(message = "This activation link is invalid or no longer usable")
    private String token;

    @NotBlank(message = "Password is required")
    @Size(min = 12, max = 128, message = "Password must contain 12 through 128 characters")
    private String password;

    @NotBlank(message = "Password confirmation is required")
    private String confirmPassword;

    /**
     * Confirms both password entries agree without exposing either value.
     *
     * @return {@code true} when confirmation matches
     */
    @AssertTrue(message = "Passwords do not match")
    public boolean isPasswordConfirmed() {
        return password != null && password.equals(confirmPassword);
    }

    /** Clears both cleartext password values before rendering an error response. */
    public void clearPasswords() {
        password = null;
        confirmPassword = null;
    }

    public String getToken() { return token; }
    public void setToken(String token) { this.token = token; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getConfirmPassword() { return confirmPassword; }
    public void setConfirmPassword(String confirmPassword) { this.confirmPassword = confirmPassword; }
}
