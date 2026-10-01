package com.lab.labtimesheet.feature.reporting.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.lab.labtimesheet.feature.reporting.model.dto.ProjectTaskReportFilter;
import com.lab.labtimesheet.feature.reporting.model.dto.ProjectTaskReportView;
import com.lab.labtimesheet.feature.reporting.service.ProjectTaskReportService;
import com.lab.labtimesheet.feature.project.model.dto.ProjectActorView;
import com.lab.labtimesheet.feature.project.service.ProjectQueryService;
import com.lab.labtimesheet.platform.authorization.AuthorizationPolicy;
import com.lab.labtimesheet.platform.service.SmtpConfigurationService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Production-shaped MVC RED for the authorized Project/Task report route. */
@WebMvcTest(ProjectTaskReportController.class)
class ProjectTaskReportControllerWebTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ProjectTaskReportService reports;

    @MockitoBean
    private SmtpConfigurationService smtpConfiguration;

    @MockitoBean
    private ProjectQueryService projectQueries;

    @MockitoBean
    private AuthorizationPolicy authorizationPolicy;

    @BeforeEach
    void allowReportPolicyForRouteSlice() {
        given(projectQueries.authenticatedActor(org.mockito.ArgumentMatchers.anyString()))
                .willReturn(new ProjectActorView(2L, "MENTOR"));
        given(authorizationPolicy.allows(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .willReturn(true);
        given(reports.build(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.nullable(Long.class),
                org.mockito.ArgumentMatchers.nullable(Long.class),
                org.mockito.ArgumentMatchers.nullable(com.lab.labtimesheet.feature.project.model.TaskStatus.class),
                org.mockito.ArgumentMatchers.nullable(java.time.LocalDate.class),
                org.mockito.ArgumentMatchers.nullable(java.time.LocalDate.class),
                org.mockito.ArgumentMatchers.nullable(java.time.LocalDate.class),
                org.mockito.ArgumentMatchers.nullable(java.time.LocalDate.class)))
                .willReturn(new ProjectTaskReportView(
                        new ProjectTaskReportFilter(null, null, null, null, null), List.of(), List.of(), List.of(),
                        0, 0, "N/A"));
    }

    @Test
    void rendersProjectTaskReportForAnAuthenticatedMentor() throws Exception {
        given(reports.build(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull()))
                .willReturn(new ProjectTaskReportView(
                        new ProjectTaskReportFilter(null, null, null, null, null),
                        List.of(),
                        List.of(),
                        List.of(),
                        0,
                        0,
                        "N/A"));

        mvc.perform(get("/reports/project-tasks")
                        .with(user("mentor@example.test").roles("MENTOR")))
                .andExpect(status().isOk())
                .andExpect(view().name("reports/project-tasks"));
    }

    @Test
    void servesAdminProjectTaskReportRoute() throws Exception {
        given(projectQueries.authenticatedActor("admin@example.test"))
                .willReturn(new ProjectActorView(1L, "ADMIN"));
        mvc.perform(get("/reports/project-tasks")
                        .with(user("admin@example.test").roles("ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void adminAuthorityTakesPrecedenceOverAnAdditionalInternAuthority() throws Exception {
        given(projectQueries.authenticatedActor("admin@example.test"))
                .willReturn(new ProjectActorView(1L, "ADMIN"));
        mvc.perform(get("/reports/project-tasks")
                        .with(user("admin@example.test").roles("ADMIN", "INTERN")))
                .andExpect(status().isOk());
    }

    @Test
    void rendersFilterControlsAndExplicitNaForAnEmptyAuthorizedScope() throws Exception {
        given(reports.build(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull(),
                org.mockito.ArgumentMatchers.isNull()))
                .willReturn(new ProjectTaskReportView(
                        new ProjectTaskReportFilter(null, null, null, null, null),
                        List.of(),
                        List.of(),
                        List.of(),
                        0,
                        0,
                        "N/A"));

        mvc.perform(get("/reports/project-tasks")
                        .with(user("mentor@example.test").roles("MENTOR")))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("id=\"task-report-project\"")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("id=\"task-report-status\"")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("Work date from")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("Logged minutes")))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .string(org.hamcrest.Matchers.containsString("N/A")));
    }
}
