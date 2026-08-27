package com.lab.labtimesheet.feature.reporting.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.lab.labtimesheet.feature.integration.service.SmtpConfigurationService;
import com.lab.labtimesheet.feature.reporting.model.dto.ProjectTaskReportFilter;
import com.lab.labtimesheet.feature.reporting.model.dto.ProjectTaskReportView;
import com.lab.labtimesheet.feature.reporting.service.ProjectTaskReportService;
import java.util.List;
import org.junit.jupiter.api.Test;
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
    void deniesAdminBeforeCallingProjectTaskReportService() throws Exception {
        mvc.perform(get("/reports/project-tasks")
                        .with(user("admin@example.test").roles("ADMIN")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(reports);
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
