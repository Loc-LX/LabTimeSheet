package com.lab.labtimesheet.feature.reporting.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

import com.lab.labtimesheet.feature.project.model.dto.ProjectActorView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectSummary;
import com.lab.labtimesheet.feature.project.service.ProjectQueryService;
import com.lab.labtimesheet.platform.authorization.AuthorizationCatalogue;
import com.lab.labtimesheet.platform.authorization.AuthorizationColumn;
import com.lab.labtimesheet.platform.authorization.AuthorizationPolicy;
import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** Regression tests for policy-backed Daily-report navigation (RPT-011, B.8, TST-011). */
@SuppressWarnings("unchecked")
class DailyProjectWorkReportNavigationAdviceTest {

    private final ProjectQueryService projectQueries = mock(ProjectQueryService.class);
    private final ObjectProvider<ProjectQueryService> provider = mock(ObjectProvider.class);
    private final ObjectProvider<AuthorizationPolicy> policies = mock(ObjectProvider.class);
    private DailyProjectWorkReportNavigationAdvice advice;

    @BeforeEach
    void setUp() {
        given(provider.getIfAvailable()).willReturn(projectQueries);
        given(policies.getIfAvailable()).willReturn(new AuthorizationPolicy(new AuthorizationCatalogue()));
        advice = new DailyProjectWorkReportNavigationAdvice(provider, policies);
    }

    @Test
    void currentLeaderNavigationUsesActualProjectStateAndPolicyColumn() {
        given(projectQueries.authenticatedActor("intern@example.test"))
                .willReturn(new ProjectActorView(7L, "INTERN"));
        given(projectQueries.listCurrentLeaderProjectsForDailyReport(7L)).willReturn(List.of(
                new ProjectSummary(42L, "Portal", "ACTIVE", LocalDate.now(), LocalDate.now())));

        boolean allowed = advice.authorizationRequest(internAuthentication(), request("GET", "/dashboard"));

        assertThat(allowed).isTrue();
    }

    @Test
    void mutationsAndExportsDoNotPerformNavigationQueries() {
        assertThat(advice.authorizationRequest(internAuthentication(), request("POST", "/projects/42/tasks")))
                .isFalse();
        assertThat(advice.authorizationRequest(internAuthentication(), request("GET", "/reports/daily.xlsx")))
                .isFalse();
        assertThat(advice.authorizationRequest(internAuthentication(), request("GET", "/reports/daily.pdf")))
                .isFalse();
        verifyNoInteractions(projectQueries);
    }

    @Test
    void activeAdminNavigationUsesAdminPolicyColumn() {
        given(projectQueries.authenticatedActor("admin@example.test"))
                .willReturn(new ProjectActorView(1L, "ADMIN"));
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                "admin@example.test", "N/A", List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));

        boolean allowed = advice.authorizationRequest(authentication, request("GET", "/dashboard"));

        assertThat(allowed).isTrue();
    }

    private static HttpServletRequest request(String method, String path) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        given(request.getMethod()).willReturn(method);
        given(request.getRequestURI()).willReturn(path);
        return request;
    }

    private static Authentication internAuthentication() {
        return new UsernamePasswordAuthenticationToken(
                "intern@example.test", "N/A", List.of(new SimpleGrantedAuthority("ROLE_INTERN")));
    }
}
