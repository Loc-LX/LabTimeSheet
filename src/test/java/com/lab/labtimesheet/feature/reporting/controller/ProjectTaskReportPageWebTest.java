package com.lab.labtimesheet.feature.reporting.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.lab.labtimesheet.feature.account.model.AccountStatus;
import com.lab.labtimesheet.feature.account.model.GlobalRole;
import com.lab.labtimesheet.feature.account.model.dto.AccountIdentity;
import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.attendance.service.AttendanceApplicationService;
import com.lab.labtimesheet.feature.integration.service.SmtpConfigurationService;
import com.lab.labtimesheet.feature.reporting.model.dto.ProjectTaskReportData;
import com.lab.labtimesheet.feature.reporting.model.dto.ProjectTaskReportMember;
import com.lab.labtimesheet.feature.reporting.model.dto.ProjectTaskReportTask;
import com.lab.labtimesheet.feature.reporting.service.ProjectTaskReportDataProvider;
import com.lab.labtimesheet.feature.reporting.service.ProjectTaskReportService;
import com.lab.labtimesheet.feature.task.model.TaskStatus;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ProjectTaskReportController.class)
@Import(ProjectTaskReportService.class)
class ProjectTaskReportPageWebTest {

    private static final LocalDate FROM = LocalDate.of(2026, 8, 3);
    private static final LocalDate TO = LocalDate.of(2026, 8, 7);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private ProjectTaskReportDataProvider reports;

    @MockitoBean
    private AccountService accounts;

    @MockitoBean
    private AttendanceApplicationService attendance;

    @MockitoBean
    private SmtpConfigurationService smtpConfiguration;

    @Test
    void adminSeesPerMemberDetailAndMemberHoursChart() throws Exception {
        given(accounts.requireIdentityByEmail("admin@example.test"))
                .willReturn(new AccountIdentity(
                        1, "admin@example.test", "Admin", GlobalRole.ADMIN, AccountStatus.ACTIVE));
        given(attendance.currentBusinessDate()).willReturn(TO);
        given(reports.data(1, true, 10, FROM, TO))
                .willReturn(new ProjectTaskReportData(
                        10, "Titan", true, false,
                        List.of(
                                new ProjectTaskReportTask(TaskStatus.TODO, 0),
                                new ProjectTaskReportTask(TaskStatus.IN_PROGRESS, 120),
                                new ProjectTaskReportTask(TaskStatus.BLOCKED, 60),
                                new ProjectTaskReportTask(TaskStatus.DONE, 180)),
                        List.of(
                                new ProjectTaskReportMember(20, "Alice", 180, 2),
                                new ProjectTaskReportMember(21, "Bob", 180, 1))));

        mvc.perform(get("/reports/projects").with(user("admin@example.test").roles("ADMIN"))
                        .param("projectId", "10").param("from", "2026-08-03").param("to", "2026-08-07"))
                .andExpect(status().isOk())
                .andExpect(view().name("reports/projects"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Project report")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Titan")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("25.00%")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("6.00")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Alice")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Bob")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("3.00")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Member hours")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-chart=")));

        verify(reports).data(1, true, 10, FROM, TO);
    }

    @Test
    void internMemberSeesAggregateOnlyWithoutPerMemberDetailOrChart() throws Exception {
        given(accounts.requireIdentityByEmail("intern@example.test"))
                .willReturn(new AccountIdentity(
                        5, "intern@example.test", "Intern", GlobalRole.INTERN, AccountStatus.ACTIVE));
        given(attendance.currentBusinessDate()).willReturn(TO);
        given(reports.data(5, false, 10, FROM, TO))
                .willReturn(new ProjectTaskReportData(
                        10, "Titan", false, false,
                        List.of(
                                new ProjectTaskReportTask(TaskStatus.DONE, 0),
                                new ProjectTaskReportTask(TaskStatus.DONE, 0)),
                        List.of()));

        mvc.perform(get("/reports/projects").with(user("intern@example.test").roles("INTERN"))
                        .param("projectId", "10").param("from", "2026-08-03").param("to", "2026-08-07"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("100.00%")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Alice"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Member hours"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("data-chart="))));

        verify(reports).data(5, false, 10, FROM, TO);
    }

    @Test
    void currentLeaderSeesPerMemberDetail() throws Exception {
        given(accounts.requireIdentityByEmail("mentor@example.test"))
                .willReturn(new AccountIdentity(
                        2, "mentor@example.test", "Mentor", GlobalRole.MENTOR, AccountStatus.ACTIVE));
        given(attendance.currentBusinessDate()).willReturn(TO);
        given(reports.data(2, false, 10, FROM, TO))
                .willReturn(new ProjectTaskReportData(
                        10, "Titan", false, true,
                        List.of(new ProjectTaskReportTask(TaskStatus.IN_PROGRESS, 60)),
                        List.of(new ProjectTaskReportMember(20, "Alice", 60, 1))));

        mvc.perform(get("/reports/projects").with(user("mentor@example.test").roles("MENTOR"))
                        .param("projectId", "10").param("from", "2026-08-03").param("to", "2026-08-07"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Alice")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("1.00")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-chart=")));

        verify(reports).data(2, false, 10, FROM, TO);
    }

    @Test
    void missingProjectIdReturnsBadRequest() throws Exception {
        given(accounts.requireIdentityByEmail("admin@example.test"))
                .willReturn(new AccountIdentity(
                        1, "admin@example.test", "Admin", GlobalRole.ADMIN, AccountStatus.ACTIVE));

        mvc.perform(get("/reports/projects").with(user("admin@example.test").roles("ADMIN")))
                .andExpect(status().isBadRequest());

        verify(reports, org.mockito.Mockito.never())
                .data(anyLong(), anyBoolean(), anyLong(), any(), any());
    }
}