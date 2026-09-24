package com.lab.labtimesheet.feature.identity.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** Request-local email form for enumeration-safe password recovery. */
@Getter
@Setter
public class ForgotPasswordForm {
    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email address")
    @Size(max = 320, message = "Email must contain at most 320 characters")
    private String email;

    /**
     * Trims the email before it reaches the account service.
     *
     * @param email submitted address, or {@code null} while validation is collecting a required-field error
     */
    public void setEmail(String email) {
        this.email = email == null ? null : email.trim();
    }
}
