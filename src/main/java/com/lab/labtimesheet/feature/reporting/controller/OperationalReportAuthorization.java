package com.lab.labtimesheet.feature.reporting.controller;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;

/**
 * Applies the reporting role boundary at controller entry before request-specific filters or
 * exporter input validation can do any work. The underlying report services repeat this check
 * after reloading the persisted application identity.
 */
final class OperationalReportAuthorization {

    private OperationalReportAuthorization() {
    }

    /**
     * Requires an authenticated Mentor or Intern for Attendance and Project/Task reports.
     *
     * @param authentication current Spring Security authentication
     * @throws AccessDeniedException when the caller is missing or has no operational report role
     */
    static void requireOperationalReportAccess(Authentication authentication) {
        boolean admin = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
        boolean operationalRole = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_MENTOR".equals(authority.getAuthority())
                        || "ROLE_INTERN".equals(authority.getAuthority()));
        if (admin || !operationalRole) {
            throw new AccessDeniedException("Operational report access is not available for this role");
        }
    }
}
