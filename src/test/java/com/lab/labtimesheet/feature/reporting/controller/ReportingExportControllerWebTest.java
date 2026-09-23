package com.lab.labtimesheet.feature.reporting.controller;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lab.labtimesheet.feature.reporting.model.dto.AttendanceReportView;
import com.lab.labtimesheet.feature.reporting.model.dto.ProjectTaskReportFilter;
import com.lab.labtimesheet.feature.reporting.model.dto.ProjectTaskReportView;
import com.lab.labtimesheet.feature.reporting.service.AttendanceReportService;
import com.lab.labtimesheet.feature.reporting.service.DailyProjectWorkReportService;
import com.lab.labtimesheet.feature.reporting.service.ProjectTaskReportService;
import com.lab.labtimesheet.feature.reporting.service.ReportExportService;
import com.lab.labtimesheet.platform.service.SmtpConfigurationService;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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
    private DailyProjectWorkReportService dailyReports;

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
                nullable(com.lab.labtimesheet.feature.project.model.TaskStatus.class),
                nullable(LocalDate.class), nullable(LocalDate.class), nullable(LocalDate.class), nullable(LocalDate.class)))
                .willReturn(projectTasks);
        given(exports.attendanceXlsx(any())).willReturn(new byte[] {1});
        given(exports.attendancePdf(any())).willReturn(new byte[] {1});
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
                        .param("dueTo", "2026-08-31")
                        .param("workFrom", "2026-08-01")
                        .param("workTo", "2026-08-31"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment")))
                .andExpect(header().string("Content-Disposition", containsString("2026-08-01-to-2026-08-31")))
                .andExpect(content().contentType("application/pdf"));
    }

    @Test
    void adminCanDownloadAttendanceReportInBothFormats() throws Exception {
        mvc.perform(get("/reports/attendance.xlsx")
                        .with(user("admin@example.test").roles("ADMIN"))
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-31"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
        mvc.perform(get("/reports/attendance.pdf")
                        .with(user("admin@example.test").roles("ADMIN"))
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-31"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"));
    }

    @Test
    void adminCannotDownloadProjectTaskReports() throws Exception {
        for (String endpoint : List.of("/reports/project-tasks.xlsx", "/reports/project-tasks.pdf")) {
            mvc.perform(get(endpoint)
                            .with(user("admin@example.test").roles("ADMIN")))
                    .andExpect(status().isForbidden());
        }

        verifyNoInteractions(projectTaskReports);
    }

    @ParameterizedTest(name = "rejects {0} for both Project/Task export formats")
    @MethodSource("invalidProjectTaskExportRanges")
    void rejectsInvalidProjectTaskExportRangesBeforeDatasetConstruction(
            String description, Map<String, String> parameters) throws Exception {
        for (String endpoint : List.of("/reports/project-tasks.xlsx", "/reports/project-tasks.pdf")) {
            var request = get(endpoint)
                    .with(user("mentor@example.test").roles("MENTOR"))
                    .param("projectId", "7");
            parameters.forEach(request::param);

            mvc.perform(request).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(projectTaskReports);
    }

    private static Stream<Arguments> invalidProjectTaskExportRanges() {
        return Stream.of(
                Arguments.of("missing ranges", Map.of()),
                Arguments.of("half-open due range", Map.of("dueFrom", "2026-08-01")),
                Arguments.of("half-open work range", Map.of(
                        "dueFrom", "2026-08-01", "dueTo", "2026-08-31", "workFrom", "2026-08-01")),
                Arguments.of("reversed due range", Map.of(
                        "dueFrom", "2026-08-31", "dueTo", "2026-08-01",
                        "workFrom", "2026-08-01", "workTo", "2026-08-31")),
                Arguments.of("reversed work range", Map.of(
                        "dueFrom", "2026-08-01", "dueTo", "2026-08-31",
                        "workFrom", "2026-08-31", "workTo", "2026-08-01")),
                Arguments.of("overlong due range", Map.of(
                        "dueFrom", "2026-01-01", "dueTo", "2027-01-02",
                        "workFrom", "2026-08-01", "workTo", "2026-08-31")),
                Arguments.of("overlong work range", Map.of(
                        "dueFrom", "2026-08-01", "dueTo", "2026-08-31",
                        "workFrom", "2026-01-01", "workTo", "2027-01-02")));
    }

    /**
     * Protects {@code ERR-006}. Observable break: the export is refactored to write into the
     * response as it builds, so a failure part way through leaves the caller holding a truncated
     * workbook delivered with a success status and an attachment header, and nothing tells them
     * the file is incomplete.
     *
     * <p>What makes the rule true today is the shape of the seam rather than a catch block.
     * {@code ReportExportService} assembles the whole document into a {@code ByteArrayOutputStream}
     * inside try-with-resources and returns {@code byte[]}, and the controller returns
     * {@code ResponseEntity<byte[]>}. A failure therefore happens before any status line, header or
     * byte reaches the client, and the workbook and its stream are closed on the way out. This test
     * pins that ordering: when generation fails the request fails, and no response is produced at
     * all.
     *
     * <p>The rule's remaining clause, that no partial report is persisted, has nothing to assert
     * against by construction. {@code GOV-007} excludes a persisted {@code Report} entity, so the
     * export writes to no store and there is no partial row a failure could leave behind.
     */
    @Test
    void failedExportGenerationSurfacesAnErrorInsteadOfATruncatedDownload() {
        given(exports.attendanceXlsx(any()))
                .willThrow(new IllegalStateException("Unable to create attendance workbook"));

        assertThatThrownBy(() -> mvc.perform(get("/reports/attendance.xlsx")
                        .with(user("intern@example.test").roles("INTERN"))
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-31")))
                .rootCause()
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Unable to create attendance workbook");
    }
}
