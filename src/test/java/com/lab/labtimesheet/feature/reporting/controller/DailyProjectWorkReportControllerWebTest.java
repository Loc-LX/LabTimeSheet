package com.lab.labtimesheet.feature.reporting.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.lab.labtimesheet.feature.attendance.model.dto.AttendanceReportDateContext;
import com.lab.labtimesheet.feature.attendance.service.AttendanceApplicationService;
import com.lab.labtimesheet.feature.integration.service.SmtpConfigurationService;
import com.lab.labtimesheet.feature.project.model.dto.ProjectActorView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectSummary;
import com.lab.labtimesheet.feature.project.service.ProjectQueryService;
import com.lab.labtimesheet.feature.reporting.model.dto.DailyProjectWorkReportLog;
import com.lab.labtimesheet.feature.reporting.model.dto.DailyProjectWorkReportMember;
import com.lab.labtimesheet.feature.reporting.model.dto.DailyProjectWorkReportProject;
import com.lab.labtimesheet.feature.reporting.model.dto.DailyProjectWorkReportTask;
import com.lab.labtimesheet.feature.reporting.model.dto.DailyProjectWorkReportView;
import com.lab.labtimesheet.feature.reporting.service.DailyProjectWorkReportService;
import com.lab.labtimesheet.feature.task.model.TaskStatus;
import com.lab.labtimesheet.feature.task.model.TaskVarianceState;
import com.lab.labtimesheet.feature.task.model.dto.TaskRemainingEffortForecastSummary;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Public MVC contract for the active Daily Project Work Report HTML route. */
@WebMvcTest(DailyProjectWorkReportController.class)
class DailyProjectWorkReportControllerWebTest {

    private static final LocalDate REPORT_DATE = LocalDate.of(2026, 8, 20);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private DailyProjectWorkReportService reports;

    @MockitoBean
    private ProjectQueryService projectQueries;

    @MockitoBean
    private AttendanceApplicationService attendance;

    @MockitoBean
    private SmtpConfigurationService smtpConfiguration;

    @Test
    void rendersAnInformativeEmptyDailyReportWithDateContext() throws Exception {
        given(reports.build(anyString(), isNull(), isNull())).willReturn(emptyReport());

        mvc.perform(get("/reports/daily")
                        .with(user("mentor@example.test").roles("MENTOR")))
                .andExpect(status().isOk())
                .andExpect(view().name("reports/daily"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Daily Project Work Report")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "href=\"/reports/daily\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Report date")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Configured attendance workday")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("No retained Task work")));
    }

    @Test
    void rendersHistoricalAuthorLogsCurrentStatusPlanningFactsAndNeutralMissingValues() throws Exception {
        DailyProjectWorkReportTask task = new DailyProjectWorkReportTask(
                501L,
                "Prepare handover",
                8L,
                TaskStatus.IN_PROGRESS,
                90L,
                150L,
                480,
                TaskVarianceState.PENDING,
                null,
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-01T00:00:00Z"),
                false,
                List.of(new DailyProjectWorkReportLog(101L, REPORT_DATE, "repeatable description", 90)),
                new TaskRemainingEffortForecastSummary(
                        120, 90L, Instant.parse("2026-08-01T00:00:00Z")));
        DailyProjectWorkReportTask deletedDoneTask = new DailyProjectWorkReportTask(
                502L,
                "Closed archive",
                8L,
                TaskStatus.DONE,
                30L,
                150L,
                120,
                TaskVarianceState.VALUE,
                30L,
                Instant.parse("2026-08-01T00:00:00Z"),
                Instant.parse("2026-08-01T00:00:00Z"),
                true,
                List.of(new DailyProjectWorkReportLog(102L, REPORT_DATE, "N/A", 30)),
                null);
        DailyProjectWorkReportView report = new DailyProjectWorkReportView(
                REPORT_DATE,
                new AttendanceReportDateContext(
                        REPORT_DATE, false, true, 2L, LocalDate.of(2026, 1, 1),
                        ZoneId.of("Asia/Ho_Chi_Minh")),
                42L,
                "Portal",
                List.of(),
                List.of(new DailyProjectWorkReportProject(
                        42L,
                        "Portal",
                        List.of(
                                new DailyProjectWorkReportMember(7L, "Mai Intern", List.of(task), 90L),
                                new DailyProjectWorkReportMember(
                                        8L, "Nhi Intern", List.of(deletedDoneTask), 30L)),
                        120L)),
                120L);
        given(reports.build(anyString(), any(), any())).willReturn(report);

        mvc.perform(get("/reports/daily")
                        .with(user("mentor@example.test").roles("MENTOR"))
                        .param("reportDate", REPORT_DATE.toString())
                        .param("projectId", "42"))
                .andExpect(status().isOk())
                .andExpect(view().name("reports/daily"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Mai Intern")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Nhi Intern")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("repeatable description")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "class=\"field-help\">Current status:</span>")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(">IN_PROGRESS</span>")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(">DONE</span>")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("120")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Pending")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("+30")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Deleted Task; retained log")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(">N/A<")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("forecast note"))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("attendance present"))));
    }

    @Test
    void mapsUnsupportedRoleToNonDisclosingNotFoundWithoutRenderingDataset() throws Exception {
        mvc.perform(get("/reports/daily")
                        .with(user("admin@example.test").roles("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/generic"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Project unavailable")));

        verify(reports, org.mockito.Mockito.never()).build(anyString(), any(), any());
        verify(projectQueries, org.mockito.Mockito.never()).authenticatedActor(anyString());
        verifyNoInteractions(attendance);
    }

    @Test
    void mapsFutureDateValidationToBadRequest() throws Exception {
        given(reports.build(anyString(), any(), any()))
                .willThrow(new IllegalArgumentException("Report date must not be in the future"));

        mvc.perform(get("/reports/daily")
                        .with(user("mentor@example.test").roles("MENTOR"))
                        .param("reportDate", REPORT_DATE.plusDays(1).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("error/generic"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Report request is invalid")));

        verify(reports).build("mentor@example.test", null, REPORT_DATE.plusDays(1));
    }

    @Test
    void rendersKeyboardAccessibleDownloadControlsWithSelectedScopeAndDate() throws Exception {
        given(reports.build(anyString(), any(), any())).willReturn(selectedEmptyReport());

        mvc.perform(get("/reports/daily")
                        .with(user("mentor@example.test").roles("MENTOR"))
                        .param("projectId", "42")
                        .param("reportDate", REPORT_DATE.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "href=\"/reports/daily.xlsx?projectId=42&amp;date=2026-08-20\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "href=\"/reports/daily.pdf?projectId=42&amp;date=2026-08-20\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Download XLSX")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Download PDF")));
    }

    @Test
    void rendersCurrentLeaderReportWithLockedProjectScopeAndPreservedProjectId() throws Exception {
        given(reports.build("leader@example.test", 42L, REPORT_DATE)).willReturn(lockedEmptyReport());

        mvc.perform(get("/reports/daily")
                        .with(user("leader@example.test").roles("INTERN"))
                        .param("projectId", "42")
                        .param("reportDate", REPORT_DATE.toString()))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Current Leader scope: one Project")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "name=\"projectId\" value=\"42\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("id=\"daily-report-project\""))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("href=\"/reports/daily\""))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "href=\"/reports/daily.xlsx?projectId=42&amp;date=2026-08-20\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "href=\"/reports/daily.pdf?projectId=42&amp;date=2026-08-20\"")));
    }

    @Test
    void adminDailyRouteRemainsNonDisclosingAndInternWithoutLeaderIsDenied() throws Exception {
        mvc.perform(get("/reports/daily")
                        .with(user("admin@example.test").roles("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/generic"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("href=\"/reports/daily\""))));

        mvc.perform(get("/reports/daily")
                        .with(user("intern@example.test").roles("INTERN")))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/generic"))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("href=\"/reports/daily\""))));

        verify(reports, org.mockito.Mockito.never()).build(anyString(), any(), any());
    }

    @Test
    void currentLeaderWithOneEligibleProjectIsRedirectedToLockedReportAndKeepsDate() throws Exception {
        ProjectSummary project = project(42L, "Portal", "ACTIVE");
        given(attendance.currentBusinessDate()).willReturn(REPORT_DATE);
        given(projectQueries.authenticatedActor("leader@example.test"))
                .willReturn(new ProjectActorView(7L, "INTERN"));
        given(projectQueries.hasCurrentLeaderProjectForDailyReport(7L)).willReturn(true);
        given(projectQueries.listCurrentLeaderProjectsForDailyReport(7L))
                .willReturn(List.of(project));

        mvc.perform(get("/reports/daily")
                        .with(user("leader@example.test").roles("INTERN"))
                        .param("reportDate", REPORT_DATE.toString()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl(
                        "/reports/daily?projectId=42&reportDate=2026-08-20"));
    }

    @Test
    void currentLeaderWithMultipleEligibleProjectsGetsOnlyThoseProjectsInAccessibleSelector() throws Exception {
        ProjectSummary first = project(42L, "Portal", "ACTIVE");
        ProjectSummary second = project(43L, "Research", "PLANNED");
        given(attendance.currentBusinessDate()).willReturn(REPORT_DATE);
        given(projectQueries.authenticatedActor("leader@example.test"))
                .willReturn(new ProjectActorView(7L, "INTERN"));
        given(projectQueries.hasCurrentLeaderProjectForDailyReport(7L)).willReturn(true);
        given(projectQueries.listCurrentLeaderProjectsForDailyReport(7L))
                .willReturn(List.of(first, second));

        mvc.perform(get("/reports/daily")
                        .with(user("leader@example.test").roles("INTERN"))
                        .param("date", REPORT_DATE.toString()))
                .andExpect(status().isOk())
                .andExpect(view().name("reports/daily"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Choose a Project to open its Daily Project Work Report")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Portal")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Research")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "name=\"reportDate\" type=\"date\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "value=\"2026-08-20\"")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Download XLSX"))));

        verify(reports, org.mockito.Mockito.never()).build(anyString(), any(), any());
    }

    @Test
    void currentLeaderFutureDateCannotReachRedirectOrSelector() throws Exception {
        given(attendance.currentBusinessDate()).willReturn(REPORT_DATE);
        given(projectQueries.authenticatedActor("leader@example.test"))
                .willReturn(new ProjectActorView(7L, "INTERN"));
        given(projectQueries.hasCurrentLeaderProjectForDailyReport(7L)).willReturn(true);

        mvc.perform(get("/reports/daily")
                        .with(user("leader@example.test").roles("INTERN"))
                        .param("date", REPORT_DATE.plusDays(1).toString()))
                .andExpect(status().isBadRequest())
                .andExpect(view().name("error/generic"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Report request is invalid")));

        verify(projectQueries, org.mockito.Mockito.never())
                .listCurrentLeaderProjectsForDailyReport(7L);
        verify(reports, org.mockito.Mockito.never()).build(anyString(), any(), any());
    }

    @Test
    void adminIsDeniedBeforeDailyAliasAndTypedValueValidation() throws Exception {
        mvc.perform(get("/reports/daily")
                        .with(user("admin@example.test").roles("ADMIN"))
                        .param("date", REPORT_DATE.toString())
                        .param("reportDate", REPORT_DATE.plusDays(1).toString()))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/generic"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Project unavailable")));
        mvc.perform(get("/reports/daily")
                        .with(user("admin@example.test").roles("ADMIN"))
                        .param("date", "not-a-date")
                        .param("projectId", "not-a-number"))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/generic"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Project unavailable")));

        verifyNoInteractions(reports, projectQueries, attendance);
    }

    @Test
    void currentLeaderWithNoEligibleProjectKeepsNonDisclosingUnavailableResponse() throws Exception {
        given(projectQueries.authenticatedActor("leader@example.test"))
                .willReturn(new ProjectActorView(7L, "INTERN"));
        given(projectQueries.hasCurrentLeaderProjectForDailyReport(7L)).willReturn(false);
        given(projectQueries.listCurrentLeaderProjectsForDailyReport(7L))
                .willReturn(List.of());

        mvc.perform(get("/reports/daily")
                        .with(user("leader@example.test").roles("INTERN")))
                .andExpect(status().isNotFound())
                .andExpect(view().name("error/generic"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Project unavailable")));

        verify(reports, org.mockito.Mockito.never()).build(anyString(), any(), any());
    }

    @Test
    void unqualifiedInternIsDeniedBeforeDateContextForPastCurrentAndFutureDates()
            throws Exception {
        given(attendance.currentBusinessDate()).willReturn(REPORT_DATE);
        given(projectQueries.authenticatedActor("intern@example.test"))
                .willReturn(new ProjectActorView(7L, "INTERN"));
        given(projectQueries.hasCurrentLeaderProjectForDailyReport(7L)).willReturn(false);
        given(projectQueries.listCurrentLeaderProjectsForDailyReport(7L))
                .willReturn(List.of());

        for (LocalDate requestedDate : List.of(
                REPORT_DATE.minusDays(1), REPORT_DATE, REPORT_DATE.plusDays(1))) {
            mvc.perform(get("/reports/daily")
                            .with(user("intern@example.test").roles("INTERN"))
                            .param("date", requestedDate.toString()))
                    .andExpect(status().isNotFound())
                    .andExpect(view().name("error/generic"))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString(
                            "Project unavailable")));
        }

        verify(projectQueries, org.mockito.Mockito.atLeast(3))
                .hasCurrentLeaderProjectForDailyReport(7L);
        verify(projectQueries, org.mockito.Mockito.never())
                .listCurrentLeaderProjectsForDailyReport(7L);
        verify(attendance, org.mockito.Mockito.never()).currentBusinessDate();
        verify(reports, org.mockito.Mockito.never()).build(anyString(), any(), any());
    }

    private static ProjectSummary project(long id, String name, String status) {
        return new ProjectSummary(
                id, name, status, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
    }

    private static DailyProjectWorkReportView emptyReport() {
        return new DailyProjectWorkReportView(
                REPORT_DATE,
                new AttendanceReportDateContext(
                        REPORT_DATE, true, false, 1L, LocalDate.of(1970, 1, 1),
                        ZoneId.of("Asia/Ho_Chi_Minh")),
                null,
                null,
                List.of(),
                List.of(),
                0L);
    }

    private static DailyProjectWorkReportView selectedEmptyReport() {
        return new DailyProjectWorkReportView(
                REPORT_DATE,
                new AttendanceReportDateContext(
                        REPORT_DATE, true, false, 1L, LocalDate.of(1970, 1, 1),
                        ZoneId.of("Asia/Ho_Chi_Minh")),
                42L,
                "Portal",
                List.of(),
                List.of(),
                0L);
    }

    private static DailyProjectWorkReportView lockedEmptyReport() {
        return new DailyProjectWorkReportView(
                REPORT_DATE,
                new AttendanceReportDateContext(
                        REPORT_DATE, true, false, 1L, LocalDate.of(1970, 1, 1),
                        ZoneId.of("Asia/Ho_Chi_Minh")),
                42L,
                "Portal",
                List.of(),
                List.of(),
                0L,
                true);
    }
}
