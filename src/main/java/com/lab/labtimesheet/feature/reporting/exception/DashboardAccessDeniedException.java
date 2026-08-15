package com.lab.labtimesheet.feature.reporting.exception;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Non-disclosing denial raised when an authenticated identity cannot access a role dashboard.
 *
 * <p>Callers must not include protected record identifiers or lifecycle details in the message.
 */
@ResponseStatus(HttpStatus.FORBIDDEN)
public class DashboardAccessDeniedException extends AccessDeniedException {

    /**
     * Creates a safe dashboard denial.
     *
     * @param message generic reason suitable for server-side diagnosis without protected details
     */
    public DashboardAccessDeniedException(String message) {
        super(message);
    }
}
