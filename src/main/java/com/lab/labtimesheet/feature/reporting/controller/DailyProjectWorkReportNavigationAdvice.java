package com.lab.labtimesheet.feature.reporting.controller;

import com.lab.labtimesheet.feature.project.exception.ProjectAccessDeniedException;
import com.lab.labtimesheet.feature.project.model.dto.ProjectActorView;
import com.lab.labtimesheet.feature.project.service.ProjectQueryService;
import com.lab.labtimesheet.feature.reporting.model.dto.DailyProjectWorkReportNavigation;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

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

    /**
     * Supplies a Boolean capability for the shared shell. The full ordered Project list is
     * loaded only by the Daily landing controller, where a selector actually needs it.
     *
     * @param authentication authenticated caller, or null for a public page
     * @param request current MVC request, used to avoid capability queries for mutations/exports
     * @return immutable navigation capability state
     */
    @ModelAttribute("dailyReportNavigation")
    public DailyProjectWorkReportNavigation navigation(
            Authentication authentication, HttpServletRequest request) {
        if (authentication == null || !hasInternAuthority(authentication)
                || request == null || !isCapabilityRequest(request)) {
            return emptyNavigation();
        }
        ProjectQueryService queries = projectQueries.getIfAvailable();
        if (queries == null) {
            return emptyNavigation();
        }
        try {
            ProjectActorView actor = queries.authenticatedActor(authentication.getName());
            if (actor == null || !"INTERN".equals(actor.role())) {
                return emptyNavigation();
            }
            return new DailyProjectWorkReportNavigation(
                    queries.hasCurrentLeaderProjectForDailyReport(actor.userId()));
        } catch (ProjectAccessDeniedException | IllegalArgumentException denied) {
            // Navigation must fail closed when the active account or Project snapshot is unavailable.
            return emptyNavigation();
        }
    }

    private static DailyProjectWorkReportNavigation emptyNavigation() {
        return new DailyProjectWorkReportNavigation(false);
    }

    private static boolean isCapabilityRequest(HttpServletRequest request) {
        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            return false;
        }
        String path = request.getRequestURI();
        return path == null || (!path.endsWith(".xlsx") && !path.endsWith(".pdf"));
    }

    private static boolean hasInternAuthority(Authentication authentication) {
        if (authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals)) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_INTERN"::equals);
    }
}
