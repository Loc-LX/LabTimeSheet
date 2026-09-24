package com.lab.labtimesheet.feature.reporting.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.identity.service.BootstrapService;
import com.lab.labtimesheet.feature.reporting.service.ProjectTaskReportService;
import com.lab.labtimesheet.feature.reporting.service.ReportExportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminDashboardWebTest {

    private final MockMvc mvc;
    private final BootstrapService bootstrap;

    @MockitoBean
    private ProjectTaskReportService projectTaskReports;

    @MockitoBean
    private ReportExportService exports;

    @Autowired
    AdminDashboardWebTest(MockMvc mvc, BootstrapService bootstrap) {
        this.mvc = mvc;
        this.bootstrap = bootstrap;
    }

    @Test
    @WithMockUser(username = "admin@example.test", roles = "ADMIN")
    void adminDashboardUsesAccountLifecycleSummariesOnly() throws Exception {
        bootstrap();

        mvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("System overview")))
                .andExpect(content().string(containsString("Active accounts</div><div class=\"metric-value\">1")))
                .andExpect(content().string(containsString("Pending activation</div><div class=\"metric-value\">0")))
                .andExpect(content().string(containsString("Active internships</div><div class=\"metric-value\">0")))
                .andExpect(content().string(not(containsString("Active Projects"))))
                .andExpect(content().string(containsString("Create account")))
                .andExpect(content().string(containsString("No pending activations")))
                .andExpect(content().string(containsString("Attendance reports")))
                .andExpect(content().string(not(containsString("Create Project"))))
                .andExpect(content().string(not(containsString("Check in"))));
    }

    @Test
    @WithMockUser(username = "admin@example.test", roles = "ADMIN")
    void adminCanOpenAndDownloadAttendanceReportsButNotProjectTaskReports() throws Exception {
        bootstrap();

        mvc.perform(get("/reports/attendance"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("internId=0"))))
                .andExpect(content().string(containsString("/reports/attendance.xlsx?from=")))
                .andExpect(content().string(containsString("/reports/attendance.pdf?from=")));
        mvc.perform(get("/reports/attendance.xlsx"))
                .andExpect(status().isOk());
        mvc.perform(get("/reports/attendance.pdf"))
                .andExpect(status().isOk());
        mvc.perform(get("/reports/project-tasks"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/reports/project-tasks.xlsx"))
                .andExpect(status().isForbidden());
        mvc.perform(get("/reports/project-tasks.pdf"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@example.test", roles = {"ADMIN", "INTERN"})
    void adminPrecedenceDeniesMalformedProjectTaskHtmlAndExportsBeforeBinding() throws Exception {
        bootstrap();

        for (String endpoint : new String[] {
                "/reports/project-tasks",
                "/reports/project-tasks.xlsx",
                "/reports/project-tasks.pdf"}) {
            mvc.perform(get(endpoint).param("dueFrom", "not-a-date"))
                    .andExpect(status().isForbidden());
        }

        org.mockito.Mockito.verifyNoInteractions(projectTaskReports, exports);
    }

    @Test
    @WithMockUser(username = "admin@example.test", roles = {"ADMIN", "INTERN"})
    void adminPrecedenceKeepsAttendanceNavigationAndHidesOperationalReports() throws Exception {
        bootstrap();

        mvc.perform(get("/dashboard"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Attendance reports")))
                .andExpect(content().string(not(containsString("Project and Task reports"))))
                .andExpect(content().string(not(containsString("Daily Project Work Report"))));
    }

    @Test
    @WithMockUser(username = "intern@example.test", roles = "ADMIN")
    void adminAuthorityDoesNotAuthorizeUnknownAccount() throws Exception {
        bootstrap();

        mvc.perform(get("/dashboard"))
                .andExpect(status().isForbidden());
    }

    @Test
    void dashboardRequiresAuthentication() throws Exception {
        bootstrap();
        mvc.perform(get("/dashboard"))
                .andExpect(status().is3xxRedirection());
    }

    private void bootstrap() {
        bootstrap.bootstrap("admin@example.test", "An Admin", "correct-horse-battery-staple");
    }
}
