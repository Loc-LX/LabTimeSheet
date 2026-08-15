package com.lab.labtimesheet.feature.account.model.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Validated browser input for creating the first administrator.
 * The password is deliberately never copied into redirected state or repopulated after validation failure.
 */
@Getter
public class BootstrapForm {
    @NotBlank(message = "Email is required")
    @Email(message = "Enter a valid email address")
    @Size(max = 320, message = "Email must contain at most 320 characters")
    private String email;

    @NotBlank(message = "Display name is required")
    @Size(max = 120, message = "Display name must contain at most 120 characters")
    private String displayName;

    @NotBlank(message = "Password is required")
    @Size(min = 12, max = 128, message = "Password must contain 12 through 128 characters")
    @Setter
    private String password;

    public void setEmail(String email) {
        this.email = email == null ? null : email.trim();
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName == null ? null : displayName.trim();
    }

}
