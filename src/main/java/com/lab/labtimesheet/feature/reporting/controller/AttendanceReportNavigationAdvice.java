package com.lab.labtimesheet.feature.reporting.controller;

import com.lab.labtimesheet.feature.identity.model.AccountStatus;
import com.lab.labtimesheet.feature.identity.model.dto.AccountIdentity;
import com.lab.labtimesheet.feature.identity.service.AccountService;
import com.lab.labtimesheet.platform.authorization.AuthorizationColumn;
import com.lab.labtimesheet.platform.authorization.AuthorizationRequest;
import com.lab.labtimesheet.platform.model.GlobalRole;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Resolves the signed-in actor's report scope for the shared layout's policy question. */
@ControllerAdvice
@RequiredArgsConstructor
public class AttendanceReportNavigationAdvice {

    private final ObjectProvider<AccountService> accountServices;

    /**
     * Supplies active identity scope to the layout, which asks the shared policy whether to show
     * the Attendance report link. This context is presentation only; the service checks again.
     *
     * @param authentication authenticated principal or null for public pages
     * @param request current MVC request, used to avoid identity reads on mutations and error dispatches
     * @return policy input for the active Admin, Mentor, or Intern; otherwise null
     */
    @ModelAttribute("attendanceReportAuthorization")
    public AuthorizationRequest authorizationRequest(
            Authentication authentication, HttpServletRequest request) {
        if (authentication == null || !authentication.isAuthenticated()
                || request == null || !"GET".equalsIgnoreCase(request.getMethod())
                || request.getDispatcherType() == DispatcherType.ERROR
                || isExportOrErrorPath(request.getRequestURI())) {
            return null;
        }
        AccountService accounts = accountServices.getIfAvailable();
        if (accounts == null) {
            return null;
        }
        AccountIdentity identity;
        try {
            identity = accounts.requireIdentityByEmail(authentication.getName());
        } catch (IllegalArgumentException denied) {
            return null;
        }
        if (identity == null || identity.status() != AccountStatus.ACTIVE) {
            return null;
        }
        AuthorizationColumn column = switch (identity.role()) {
            case ADMIN -> AuthorizationColumn.ADMIN;
            case MENTOR -> AuthorizationColumn.OWNING_MENTOR;
            case INTERN -> AuthorizationColumn.ACTIVE_MEMBER_ASSIGNEE;
        };
        return new AuthorizationRequest(Set.of(column), identity.status().name(), null, null);
    }

    private static boolean isExportOrErrorPath(String path) {
        return path == null || path.equals("/error") || path.endsWith(".xlsx") || path.endsWith(".pdf");
    }
}
