package com.lab.labtimesheet.feature.reporting.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.times;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lab.labtimesheet.feature.integration.service.SmtpConfigurationService;
import com.lab.labtimesheet.feature.project.exception.ProjectAccessDeniedException;
import com.lab.labtimesheet.feature.reporting.model.dto.DailyProjectWorkReportView;
import com.lab.labtimesheet.feature.reporting.service.AttendanceReportService;
import com.lab.labtimesheet.feature.reporting.service.DailyProjectWorkReportService;
import com.lab.labtimesheet.feature.reporting.service.ProjectTaskReportService;
import com.lab.labtimesheet.feature.reporting.service.ReportExportService;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Public MVC contracts for Daily XLSX/PDF authorization and attachment boundaries. */
@WebMvcTest(ReportExportController.class)
class DailyProjectWorkReportExportControllerWebTest {

    private static final LocalDate REPORT_DATE = LocalDate.of(2026, 8, 27);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AttendanceReportService attendanceReports;

    @MockitoBean
    private ProjectTaskReportService projectTaskReports;

    @MockitoBean
    private DailyProjectWorkReportService dailyReports;

    @MockitoBean
    private ReportExportService exports;

    @MockitoBean
    private SmtpConfigurationService smtpConfiguration;

    @Test
    void dailyXlsxBuildsExactlyOneAuthorizedViewAndReturnsSafeDateFilename() throws Exception {
        DailyProjectWorkReportView report = org.mockito.Mockito.mock(DailyProjectWorkReportView.class);
        given(report.reportDate()).willReturn(REPORT_DATE);
        given(dailyReports.build("mentor@example.test", 42L, REPORT_DATE)).willReturn(report);
        given(exports.dailyXlsx(report)).willReturn(new byte[] {1, 2, 3});

        mvc.perform(get("/reports/daily.xlsx")
                        .with(user("mentor@example.test").roles("MENTOR"))
                        .param("projectId", "42")
                        .param("date", REPORT_DATE.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string(
                        "Content-Disposition",
                        containsString("daily-project-work-report-2026-08-27.xlsx")));

        verify(dailyReports).build("mentor@example.test", 42L, REPORT_DATE);
        verify(exports).dailyXlsx(report);
    }

    @Test
    void dailyPdfBuildsExactlyOneAuthorizedViewAndReturnsSafeDateFilename() throws Exception {
        DailyProjectWorkReportView report = org.mockito.Mockito.mock(DailyProjectWorkReportView.class);
        given(report.reportDate()).willReturn(REPORT_DATE);
        given(dailyReports.build("mentor@example.test", null, REPORT_DATE)).willReturn(report);
        given(exports.dailyPdf(report)).willReturn(new byte[] {4, 5, 6});

        mvc.perform(get("/reports/daily.pdf")
                        .with(user("mentor@example.test").roles("MENTOR"))
                        .param("reportDate", REPORT_DATE.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"))
                .andExpect(header().string(
                        "Content-Disposition",
                        containsString("daily-project-work-report-2026-08-27.pdf")));

        verify(dailyReports).build("mentor@example.test", null, REPORT_DATE);
        verify(exports).dailyPdf(report);
    }

    @Test
    void currentLeaderCanUseTheSameAuthorizedDatasetForXlsxAndPdf() throws Exception {
        DailyProjectWorkReportView report = org.mockito.Mockito.mock(DailyProjectWorkReportView.class);
        given(report.reportDate()).willReturn(REPORT_DATE);
        given(dailyReports.build("leader@example.test", 42L, REPORT_DATE)).willReturn(report);
        given(exports.dailyXlsx(report)).willReturn(new byte[] {1});
        given(exports.dailyPdf(report)).willReturn(new byte[] {2});

        mvc.perform(get("/reports/daily.xlsx")
                        .with(user("leader@example.test").roles("INTERN"))
                        .param("projectId", "42")
                        .param("date", REPORT_DATE.toString()))
                .andExpect(status().isOk());
        mvc.perform(get("/reports/daily.pdf")
                        .with(user("leader@example.test").roles("INTERN"))
                        .param("projectId", "42")
                        .param("date", REPORT_DATE.toString()))
                .andExpect(status().isOk());

        verify(dailyReports, times(2)).build("leader@example.test", 42L, REPORT_DATE);
        verify(exports).dailyXlsx(report);
        verify(exports).dailyPdf(report);
    }

    @Test
    void missingDateDelegatesNullToDailyServiceForCurrentBusinessDateDefault() throws Exception {
        DailyProjectWorkReportView report = org.mockito.Mockito.mock(DailyProjectWorkReportView.class);
        given(report.reportDate()).willReturn(REPORT_DATE);
        given(dailyReports.build("mentor@example.test", null, null)).willReturn(report);
        given(exports.dailyXlsx(report)).willReturn(new byte[] {7, 8, 9});

        mvc.perform(get("/reports/daily.xlsx")
                        .with(user("mentor@example.test").roles("MENTOR")))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Content-Disposition",
                        containsString("daily-project-work-report-2026-08-27.xlsx")));

        verify(dailyReports).build("mentor@example.test", null, null);
        verify(exports).dailyXlsx(report);
    }

    @Test
    void unsupportedRoleAndUnauthorizedProjectAreRejectedBeforeExporterInvocation() throws Exception {
        given(dailyReports.build(anyString(), any(), any()))
                .willThrow(new ProjectAccessDeniedException());

        mvc.perform(get("/reports/daily.xlsx")
                        .with(user("intern@example.test").roles("INTERN"))
                        .param("projectId", "42")
                        .param("date", REPORT_DATE.toString()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/reports/daily.pdf")
                        .with(user("mentor@example.test").roles("MENTOR"))
                        .param("projectId", "999")
                        .param("date", REPORT_DATE.toString()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/reports/daily.xlsx")
                        .with(user("admin@example.test").roles("ADMIN"))
                        .param("date", REPORT_DATE.toString()))
                .andExpect(status().isNotFound());

        verifyNoInteractions(exports);
    }

    @Test
    void futureDateIsRejectedBeforeExporterInvocation() throws Exception {
        given(dailyReports.build(anyString(), any(), any()))
                .willThrow(new IllegalArgumentException("Report date must not be in the future"));

        mvc.perform(get("/reports/daily.xlsx")
                        .with(user("mentor@example.test").roles("MENTOR"))
                        .param("date", REPORT_DATE.plusDays(1).toString()))
                .andExpect(status().isBadRequest());

        verify(dailyReports).build("mentor@example.test", null, REPORT_DATE.plusDays(1));
        verifyNoInteractions(exports);
    }

    @Test
    void malformedDateIsRejectedBeforeDatasetOrExporterInvocation() throws Exception {
        mvc.perform(get("/reports/daily.pdf")
                        .with(user("mentor@example.test").roles("MENTOR"))
                        .param("date", "not-a-date"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(dailyReports, exports);
    }
}
