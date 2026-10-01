package com.lab.labtimesheet.feature.reporting.controller;

import com.lab.labtimesheet.feature.project.exception.ProjectAccessDeniedException;
import com.lab.labtimesheet.feature.project.model.dto.ProjectActorView;
import com.lab.labtimesheet.feature.project.service.ProjectQueryService;
import com.lab.labtimesheet.platform.authorization.AuthorizationCapability;
import com.lab.labtimesheet.platform.authorization.AuthorizationColumn;
import com.lab.labtimesheet.platform.authorization.AuthorizationPolicy;
import com.lab.labtimesheet.platform.authorization.AuthorizationRequest;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import java.util.Set;

/**
 * Adds the server-derived current-Leader Daily-report capability to every server-rendered page.
 *
 * <p>The optional provider keeps narrow MVC slices that render the shared shell without the full
 * Project feature context usable. In the application context the Project query boundary is always
 * present and supplies the current leadership snapshot.</p>
 */
@ControllerAdvice
@RequiredArgsConstructor
public class DailyProjectWorkReportNavigationAdvice {

    private final ObjectProvider<ProjectQueryService> projectQueries;
    private final ObjectProvider<AuthorizationPolicy> authorizationPolicies;

    /**
     * Supplies a Boolean capability for the shared shell. The full ordered Project list is
     * loaded only by the Daily landing controller, where a selector actually needs it.
     *
     * @param authentication authenticated caller, or null for a public page
     * @param request current MVC request, used to avoid capability queries for mutations/exports
     * @return policy input for a visible Admin, Mentor, or current-Leader Daily entry
     */
    @ModelAttribute("dailyReportAllowed")
    public boolean authorizationRequest(
            Authentication authentication, HttpServletRequest request) {
        if (authentication == null || !authentication.isAuthenticated()
                || request == null || isErrorRequest(request) || !isCapabilityRequest(request)) {
            return false;
        }
        AuthorizationPolicy authorizationPolicy = authorizationPolicies.getIfAvailable();
        if (authorizationPolicy == null) {
            return false;
        }
        ProjectQueryService queries = projectQueries.getIfAvailable();
        if (queries == null) {
            return false;
        }
        try {
            ProjectActorView actor = queries.authenticatedActor(authentication.getName());
            if (actor == null) {
                return false;
            }
            if ("ADMIN".equals(actor.role())) {
                return authorizationPolicy.allows(AuthorizationCapability.DAILY_PROJECT_REPORT,
                        request(AuthorizationColumn.ADMIN, null));
            }
            if ("MENTOR".equals(actor.role())) {
                return authorizationPolicy.allows(AuthorizationCapability.DAILY_PROJECT_REPORT,
                        request(AuthorizationColumn.OWNING_MENTOR, null));
            }
            if (!"INTERN".equals(actor.role())) {
                return false;
            }
            return queries.listCurrentLeaderProjectsForDailyReport(actor.userId()).stream()
                    .filter(project -> authorizationPolicy.allows(
                            AuthorizationCapability.DAILY_PROJECT_REPORT,
                            request(AuthorizationColumn.CURRENT_LEADER, project.status())))
                    .findFirst().isPresent();
        } catch (ProjectAccessDeniedException | IllegalArgumentException denied) {
            // Navigation must fail closed when the active account or Project snapshot is unavailable.
            return false;
        }
    }

    private static AuthorizationRequest request(AuthorizationColumn column, String scopeState) {
        return new AuthorizationRequest(Set.of(column), scopeState, null, null);
    }

    private static boolean isCapabilityRequest(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        return path == null || (!path.endsWith(".xlsx") && !path.endsWith(".pdf"));
    }

    private static boolean isErrorRequest(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        String errorPath = (contextPath == null ? "" : contextPath) + "/error";
        return request.getDispatcherType() == DispatcherType.ERROR
                || errorPath.equals(request.getRequestURI());
    }

}
