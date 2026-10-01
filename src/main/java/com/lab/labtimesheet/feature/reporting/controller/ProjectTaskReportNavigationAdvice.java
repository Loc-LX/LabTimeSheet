package com.lab.labtimesheet.feature.reporting.controller;

import com.lab.labtimesheet.feature.project.exception.ProjectAccessDeniedException;
import com.lab.labtimesheet.feature.project.model.dto.ProjectActorView;
import com.lab.labtimesheet.feature.project.service.ProjectQueryService;
import com.lab.labtimesheet.platform.authorization.AuthorizationColumn;
import com.lab.labtimesheet.platform.authorization.AuthorizationCapability;
import com.lab.labtimesheet.platform.authorization.AuthorizationPolicy;
import com.lab.labtimesheet.platform.authorization.AuthorizationRequest;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Supplies a candidate policy scope for the shared Project/Task report navigation entry. */
@ControllerAdvice
@RequiredArgsConstructor
public class ProjectTaskReportNavigationAdvice {

    private final ObjectProvider<ProjectQueryService> projectQueries;
    private final ObjectProvider<AuthorizationPolicy> authorizationPolicies;

    /**
     * Resolves an active identity before the shell asks the policy about the report link.
     * The report service resolves exact per-Project membership before returning any dataset.
     *
     * @param authentication authenticated caller, or null for a public page
     * @param request current request used to skip mutations, exports, and error dispatches
     * @return candidate policy input, or null when there is no active identity
     */
    @ModelAttribute("projectTaskReportAllowed")
    public boolean authorizationRequest(
            Authentication authentication, HttpServletRequest request) {
        if (authentication == null || !authentication.isAuthenticated()
                || request == null || !"GET".equalsIgnoreCase(request.getMethod())
                || request.getDispatcherType() == DispatcherType.ERROR
                || isExportOrErrorPath(request.getRequestURI())) {
            return false;
        }
        AuthorizationPolicy policy = authorizationPolicies.getIfAvailable();
        if (policy == null) {
            return false;
        }
        ProjectQueryService queries = projectQueries.getIfAvailable();
        if (queries == null) {
            return false;
        }
        try {
            ProjectActorView actor = queries.authenticatedActor(authentication.getName());
            AuthorizationColumn column = switch (actor.role()) {
                case "ADMIN" -> AuthorizationColumn.ADMIN;
                case "MENTOR" -> AuthorizationColumn.OWNING_MENTOR;
                case "INTERN" -> AuthorizationColumn.ACTIVE_MEMBER_ASSIGNEE;
                default -> null;
            };
            return column != null && policy.allows(AuthorizationCapability.PROJECT_TASK_REPORT,
                    new AuthorizationRequest(Set.of(column), null, null, null));
        } catch (ProjectAccessDeniedException | IllegalArgumentException denied) {
            return false;
        }
    }

    private static boolean isExportOrErrorPath(String path) {
        return path == null || path.equals("/error") || path.endsWith(".xlsx") || path.endsWith(".pdf");
    }
}
