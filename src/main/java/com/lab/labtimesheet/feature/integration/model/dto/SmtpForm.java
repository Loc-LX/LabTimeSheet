package com.lab.labtimesheet.feature.integration.model.dto;

import com.lab.labtimesheet.feature.integration.model.SecurityMode;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Validated Admin input for an SMTP draft. The cleartext password exists only for the current request and is
 * cleared before the form is rendered again.
 */
public class SmtpForm {
    @NotBlank(message = "Host is required")
    @Size(max = 255, message = "Host must contain at most 255 characters")
    private String host;

    @Min(value = 1, message = "Port must be between 1 and 65535")
    @Max(value = 65535, message = "Port must be between 1 and 65535")
    private int port = 587;

    @NotNull(message = "Security mode is required")
    private SecurityMode securityMode = SecurityMode.STARTTLS;

    @Size(max = 320, message = "Username must contain at most 320 characters")
    private String username;

    @Size(max = 1024, message = "Password is too long")
    private String password;

    @NotBlank(message = "From address is required")
    @Email(message = "Enter a valid email address")
    @Size(max = 320, message = "From address must contain at most 320 characters")
    private String fromAddress;

    @NotBlank(message = "From name is required")
    @Size(max = 120, message = "From name must contain at most 120 characters")
    private String fromName;

    /**
     * Ensures SMTP authentication is either fully configured or completely absent.
     *
     * @return {@code true} when username and password presence agree
     */
    @AssertTrue(message = "SMTP username and password must be supplied together")
    public boolean isAuthenticationComplete() {
        return hasText(username) == hasText(password);
    }

    /**
     * Converts validated browser input into the service command. The password remains request-local until the
     * service encrypts it.
     *
     * @return SMTP draft command
     */
    public SmtpDraft toDraft() {
        return new SmtpDraft(host, port, securityMode, clean(username), emptyToNull(password), fromAddress, fromName);
    }

    /**
     * Builds a safe form representation of an existing draft without decrypting or exposing its password.
     *
     * @param status current non-secret setup status
     * @return form populated only with non-secret values
     */
    public static SmtpForm from(SmtpSetupStatus status) {
        SmtpForm form = new SmtpForm();
        if (status.draftId() != null) {
            form.host = status.host();
            form.port = status.port();
            form.securityMode = status.securityMode();
            form.username = status.username();
            form.fromAddress = status.fromAddress();
            form.fromName = status.fromName();
        }
        return form;
    }

    /** Clears the request-local cleartext password before rendering. */
    public void clearPassword() {
        password = null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String clean(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public SecurityMode getSecurityMode() { return securityMode; }
    public void setSecurityMode(SecurityMode securityMode) { this.securityMode = securityMode; }
    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }
    public String getFromAddress() { return fromAddress; }
    public void setFromAddress(String fromAddress) { this.fromAddress = fromAddress; }
    public String getFromName() { return fromName; }
    public void setFromName(String fromName) { this.fromName = fromName; }
}
