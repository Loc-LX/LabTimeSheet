package com.lab.labtimesheet.feature.reporting.controller;

import com.lab.labtimesheet.feature.project.exception.ProjectAccessDeniedException;
import com.lab.labtimesheet.feature.project.model.dto.ProjectActorView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectSummary;
import com.lab.labtimesheet.feature.project.service.ProjectQueryService;
import com.lab.labtimesheet.platform.authorization.AuthorizationCapability;
import com.lab.labtimesheet.platform.authorization.AuthorizationColumn;
import com.lab.labtimesheet.platform.authorization.AuthorizationPolicy;
import com.lab.labtimesheet.platform.authorization.AuthorizationRequest;
import java.util.Set;
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
     * Requires an authenticated Admin, Mentor, or Intern for Attendance reports.
     *
     * @param authentication current Spring Security authentication
     * @throws AccessDeniedException when the caller is missing or has no Attendance-report role
     */
    static void requireAttendanceReportAccess(Authentication authentication) {
        boolean allowedRole = authentication != null && authentication.getAuthorities().stream()
                .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority())
                        || "ROLE_MENTOR".equals(authority.getAuthority())
                        || "ROLE_INTERN".equals(authority.getAuthority()));
        if (!allowedRole) {
            throw new AccessDeniedException("Attendance report access is not available for this role");
        }
    }

    /**
     * Applies the coarse Project/Task-report policy check before request-specific filters run.
     *
     * @param authentication current Spring Security authentication
     * @throws AccessDeniedException when the caller is missing or has no operational report role
     */
    static void requireOperationalReportAccess(
            Authentication authentication, ProjectQueryService projects, AuthorizationPolicy policy) {
        ProjectActorView actor = activeActor(authentication, projects);
        AuthorizationColumn column = switch (actor.role()) {
            case "ADMIN" -> AuthorizationColumn.ADMIN;
            case "MENTOR" -> AuthorizationColumn.OWNING_MENTOR;
            case "INTERN" -> AuthorizationColumn.ACTIVE_MEMBER_ASSIGNEE;
            default -> null;
        };
        AuthorizationRequest request = new AuthorizationRequest(
                column == null ? Set.of() : Set.of(column), null, null, null);
        if (column == null || !policy.allows(AuthorizationCapability.PROJECT_TASK_REPORT, request)) {
            throw new AccessDeniedException("Operational report access is not available for this role");
        }
    }

    /**
     * Requires a policy-granted Daily-report scope before request parameters are interpreted.
     * Current-Leader scope and Project state come from the Project service.
     *
     * @param authentication current Spring Security authentication
     * @throws ProjectAccessDeniedException when the caller is not an owning-Mentor or Intern
     */
    static void requireDailyReportAccess(
            Authentication authentication, ProjectQueryService projects, AuthorizationPolicy policy) {
        ProjectActorView actor = activeActor(authentication, projects);
        if ("ADMIN".equals(actor.role())) {
            requireDailyPolicy(policy, AuthorizationColumn.ADMIN, null);
            return;
        }
        if ("MENTOR".equals(actor.role())) {
            requireDailyPolicy(policy, AuthorizationColumn.OWNING_MENTOR, null);
            return;
        }
        if ("INTERN".equals(actor.role())) {
            boolean allowed = projects.listCurrentLeaderProjectsForDailyReport(actor.userId()).stream()
                    .anyMatch(project -> policy.allows(
                            AuthorizationCapability.DAILY_PROJECT_REPORT,
                            new AuthorizationRequest(
                                    Set.of(AuthorizationColumn.CURRENT_LEADER), project.status(), null, null)));
            if (allowed) {
                return;
            }
        }
        throw new ProjectAccessDeniedException();
    }

    private static ProjectActorView activeActor(
            Authentication authentication, ProjectQueryService projects) {
        if (authentication == null || authentication.getName() == null) {
            throw new AccessDeniedException("Operational report access is not available for this role");
        }
        try {
            return projects.authenticatedActor(authentication.getName());
        } catch (ProjectAccessDeniedException denied) {
            throw new AccessDeniedException("Operational report access is not available for this role", denied);
        }
    }

    private static void requireDailyPolicy(
            AuthorizationPolicy policy, AuthorizationColumn column, ProjectSummary project) {
        String scopeState = project == null ? null : project.status();
        if (!policy.allows(AuthorizationCapability.DAILY_PROJECT_REPORT,
                new AuthorizationRequest(Set.of(column), scopeState, null, null))) {
            throw new ProjectAccessDeniedException();
        }
    }
}
