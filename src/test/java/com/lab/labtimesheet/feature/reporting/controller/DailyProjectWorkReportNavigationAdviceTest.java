package com.lab.labtimesheet.feature.reporting.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.lab.labtimesheet.feature.project.model.dto.ProjectActorView;
import com.lab.labtimesheet.feature.project.service.ProjectQueryService;
import com.lab.labtimesheet.feature.reporting.model.dto.DailyProjectWorkReportNavigation;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** Regression tests for efficient server-side Leader Daily navigation capability production. */
@SuppressWarnings("unchecked")
class DailyProjectWorkReportNavigationAdviceTest {

    private final ProjectQueryService projectQueries = mock(ProjectQueryService.class);
    private final ObjectProvider<ProjectQueryService> provider = mock(ObjectProvider.class);
    private DailyProjectWorkReportNavigationAdvice advice;

    @BeforeEach
    void setUp() {
        given(provider.getIfAvailable()).willReturn(projectQueries);
        advice = new DailyProjectWorkReportNavigationAdvice(provider);
    }

    @Test
    void unrelatedInternGetUsesExistsCapabilityWithoutLoadingProjectList() {
        given(projectQueries.authenticatedActor("intern@example.test"))
                .willReturn(new ProjectActorView(7L, "INTERN"));
        given(projectQueries.hasCurrentLeaderProjectForDailyReport(7L)).willReturn(true);

        DailyProjectWorkReportNavigation navigation = advice.navigation(
                internAuthentication(), request("GET", "/dashboard"));

        assertThat(navigation.available()).isTrue();
        verify(projectQueries).hasCurrentLeaderProjectForDailyReport(7L);
        verify(projectQueries, never()).listCurrentLeaderProjectsForDailyReport(7L);
    }

    @Test
    void mutationsAndExportsDoNotPerformLeaderCapabilityQueries() {
        DailyProjectWorkReportNavigation mutation = advice.navigation(
                internAuthentication(), request("POST", "/projects/42/tasks"));
        DailyProjectWorkReportNavigation xlsx = advice.navigation(
                internAuthentication(), request("GET", "/reports/daily.xlsx"));
        DailyProjectWorkReportNavigation pdf = advice.navigation(
                internAuthentication(), request("GET", "/reports/daily.pdf"));

        assertThat(mutation.available()).isFalse();
        assertThat(xlsx.available()).isFalse();
        assertThat(pdf.available()).isFalse();
        verifyNoInteractions(projectQueries);
    }

    @Test
    void adminAuthorityNeverQueriesLeaderCapabilityEvenIfAnotherRoleIsPresent() {
        Authentication authentication = new UsernamePasswordAuthenticationToken(
                "admin@example.test",
                "N/A",
                List.of(
                        new SimpleGrantedAuthority("ROLE_ADMIN"),
                        new SimpleGrantedAuthority("ROLE_INTERN")));

        DailyProjectWorkReportNavigation navigation = advice.navigation(
                authentication, request("GET", "/dashboard"));

        assertThat(navigation.available()).isFalse();
        verifyNoInteractions(projectQueries);
    }

    private static HttpServletRequest request(String method, String path) {
        HttpServletRequest request = mock(HttpServletRequest.class);
        given(request.getMethod()).willReturn(method);
        given(request.getRequestURI()).willReturn(path);
        return request;
    }

    private static Authentication internAuthentication() {
        return new UsernamePasswordAuthenticationToken(
                "intern@example.test",
                "N/A",
                List.of(new SimpleGrantedAuthority("ROLE_INTERN")));
    }
}
