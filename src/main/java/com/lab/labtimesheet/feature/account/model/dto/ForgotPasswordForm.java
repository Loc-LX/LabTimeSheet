package com.lab.labtimesheet.feature.account.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/**
 * Validated forgot-password submission. Only the email address is collected; the response stays generic to avoid
 * account enumeration.
 */
@Getter
@Setter
public class ForgotPasswordForm {
    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email address")
    private String email;
}