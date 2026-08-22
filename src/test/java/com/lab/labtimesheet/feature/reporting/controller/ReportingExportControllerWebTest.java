package com.lab.labtimesheet.feature.reporting.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lab.labtimesheet.feature.integration.service.SmtpConfigurationService;
import com.lab.labtimesheet.feature.reporting.model.dto.AttendanceReportView;
import com.lab.labtimesheet.feature.reporting.model.dto.ProjectTaskReportFilter;
import com.lab.labtimesheet.feature.reporting.model.dto.ProjectTaskReportView;
import com.lab.labtimesheet.feature.reporting.service.AttendanceReportService;
import com.lab.labtimesheet.feature.reporting.service.ProjectTaskReportService;
import com.lab.labtimesheet.feature.reporting.service.ReportExportService;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;

/** RED contract for the report download routes and their safe response metadata. */
@WebMvcTest({AttendanceReportController.class, ProjectTaskReportController.class, ReportExportController.class})
class ReportingExportControllerWebTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AttendanceReportService attendanceReports;

    @MockitoBean
    private ProjectTaskReportService projectTaskReports;

    @MockitoBean
    private ReportExportService exports;

    @MockitoBean
    private SmtpConfigurationService smtpConfiguration;

    @BeforeEach
    void stubExportInputs() {
        AttendanceReportView attendance = new AttendanceReportView(
                7L, "Mai Intern", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), true,
                List.of(), 0L, 0L, 0L, "N/A", "N/A", List.of(), List.of());
        given(attendanceReports.build(
                any(Principal.class), nullable(Long.class), nullable(LocalDate.class), nullable(LocalDate.class)))
                .willReturn(attendance);
        ProjectTaskReportView projectTasks = new ProjectTaskReportView(
                new ProjectTaskReportFilter(null, null, null, null, null),
                List.of(), List.of(), List.of(), 0L, 0L, "N/A");
        given(projectTaskReports.build(
                anyString(), nullable(Long.class), nullable(Long.class),
                nullable(com.lab.labtimesheet.feature.task.model.TaskStatus.class),
                nullable(LocalDate.class), nullable(LocalDate.class), nullable(LocalDate.class), nullable(LocalDate.class)))
                .willReturn(projectTasks);
        given(exports.attendanceXlsx(any())).willReturn(new byte[] {1});
        given(exports.projectTaskPdf(any())).willReturn(new byte[] {1});
    }

    @Test
    void attendanceXlsxDownloadUsesAttachmentAndWorkbookContentType() throws Exception {
        mvc.perform(get("/reports/attendance.xlsx")
                        .with(user("intern@example.test").roles("INTERN"))
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-31"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(content().contentType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @Test
    void projectTaskPdfDownloadUsesAttachmentAndPdfContentType() throws Exception {
                mvc.perform(get("/reports/project-tasks.pdf")
                        .with(user("mentor@example.test").roles("MENTOR"))
                        .param("projectId", "7")
                        .param("dueFrom", "2026-08-01")
                        .param("dueTo", "2026-08-31"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString("2026-08-01-to-2026-08-31")))
                .andExpect(content().contentType("application/pdf"));
    }
}
