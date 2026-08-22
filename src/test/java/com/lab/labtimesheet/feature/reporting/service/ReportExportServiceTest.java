package com.lab.labtimesheet.feature.reporting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.lab.labtimesheet.feature.account.model.dto.EligibleInternOption;
import com.lab.labtimesheet.feature.project.model.dto.ProjectSummary;
import com.lab.labtimesheet.feature.project.model.dto.ProjectTaskMemberView;
import com.lab.labtimesheet.feature.reporting.model.dto.AttendanceReportRow;
import com.lab.labtimesheet.feature.reporting.model.dto.AttendanceReportView;
import com.lab.labtimesheet.feature.reporting.model.dto.ProjectTaskReportFilter;
import com.lab.labtimesheet.feature.reporting.model.dto.ProjectTaskReportMemberHours;
import com.lab.labtimesheet.feature.reporting.model.dto.ProjectTaskReportRow;
import com.lab.labtimesheet.feature.reporting.model.dto.ProjectTaskReportView;
import com.lab.labtimesheet.feature.task.model.TaskStatus;
import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.context.Context;
import org.thymeleaf.context.WebContext;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.web.servlet.JakartaServletWebApplication;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;

/** Focused workbook/PDF byte contracts for the shared report exporter. */
class ReportExportServiceTest {

    @Test
    void attendanceWorkbookRetainsSummaryRowsAndVietnameseDisplayText() throws Exception {
        AttendanceReportView report = new AttendanceReportView(
                7L,
                "Nguyễn Mai",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                true,
                List.of(new AttendanceReportRow(
                        "14/08/2026", "09:05", "15:55", "16:00", "08:30–15:30 (Asia/Ho_Chi_Minh)",
                        "Present", "415 min", true)),
                20,
                19,
                1,
                "95.00%",
                "92.50%",
                List.<EligibleInternOption>of(),
                List.of());

        byte[] workbookBytes = new ReportExportService(null).attendanceXlsx(report);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(workbookBytes))) {
            var sheet = workbook.getSheet("Attendance");
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue())
                    .isEqualTo("Attendance compliance report");
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("Nguyễn Mai");
            assertThat(sheet.getRow(4).getCell(3).getStringCellValue()).isEqualTo("95.00%");
            assertThat(sheet.getRow(7).getCell(5).getStringCellValue()).isEqualTo("Present");
        }
    }

    @Test
    void projectTaskWorkbookUsesNaForEmptyDataset() throws Exception {
        ProjectTaskReportView report = new ProjectTaskReportView(
                new ProjectTaskReportFilter(null, null, null, null, null),
                List.of(), List.of(), List.of(), 0, 0, "N/A");

        byte[] workbookBytes = new ReportExportService(null).projectTaskXlsx(report);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(workbookBytes))) {
            assertThat(workbook.getSheet("Project Tasks").getRow(4).getCell(2).getStringCellValue())
                    .isEqualTo("N/A");
        }
    }

    @Test
    void pdfUsesPrintTemplateAndStartsWithPdfSignature() {
        SpringTemplateEngine templates = Mockito.mock(SpringTemplateEngine.class);
        given(templates.process(Mockito.eq("reports/print"), Mockito.any()))
                .willReturn("<html><head><style>body { font-family: 'Liberation Sans'; }</style></head>"
                        + "<body><p>Nguyễn Mai</p></body></html>");

        byte[] pdf = new ReportExportService(templates).attendancePdf(new AttendanceReportView(
                7L, "Nguyễn Mai", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1), true,
                List.of(), 0, 0, 0, "N/A", "N/A", List.of(), List.of()));

        assertThat(pdf).startsWith("%PDF".getBytes());
    }

    @Test
    void actualPrintTemplateRendersEmptyAttendancePdf() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCacheable(false);
        SpringTemplateEngine templates = new SpringTemplateEngine();
        templates.setTemplateResolver(resolver);

        byte[] pdf = new ReportExportService(templates).attendancePdf(new AttendanceReportView(
                7L, "Nguyễn Mai", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1), true,
                List.of(), 0, 0, 0, "N/A", "N/A", List.of(), List.of()));

        assertThat(pdf).startsWith("%PDF".getBytes());
    }

    @Test
    void actualPrintTemplateEmbedsFontAndPreservesVietnameseText() throws Exception {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCacheable(false);
        SpringTemplateEngine templates = new SpringTemplateEngine();
        templates.setTemplateResolver(resolver);

        byte[] pdf = new ReportExportService(templates).attendancePdf(new AttendanceReportView(
                7L, "Nguyễn Mai", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 1), true,
                List.of(), 0, 0, 0, "N/A", "N/A", List.of(), List.of()));

        assertThat(new String(pdf, java.nio.charset.StandardCharsets.ISO_8859_1))
                .contains("/FontFile");
        PdfReader reader = new PdfReader(pdf);
        try {
            String text = new PdfTextExtractor(reader).getTextFromPage(1);
            assertThat(text).contains("Nguyễn Mai");
        } finally {
            reader.close();
        }
    }

    @Test
    void sharedAttendanceDatasetKeepsTotalsAcrossHtmlWorkbookAndPdf() throws Exception {
        AttendanceReportView report = new AttendanceReportView(
                7L, "Nguyễn Mai", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31), true,
                List.of(), 20, 19, 1, "95.00%", "92.50%", List.of(), List.of());
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCacheable(false);
        SpringTemplateEngine templates = new SpringTemplateEngine();
        templates.setTemplateResolver(resolver);
        Context context = new Context();
        context.setVariable("reportKind", "attendance");
        context.setVariable("report", report);

        String html = templates.process("reports/print", context);
        assertThat(html).contains("20").contains("19").contains("1").contains("95.00%").contains("92.50%");
        try (XSSFWorkbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(new ReportExportService(templates).attendanceXlsx(report)))) {
            var summary = workbook.getSheet("Attendance").getRow(4);
            assertThat(summary.getCell(0).getNumericCellValue()).isEqualTo(20);
            assertThat(summary.getCell(1).getNumericCellValue()).isEqualTo(19);
            assertThat(summary.getCell(2).getNumericCellValue()).isEqualTo(1);
        }
        PdfReader reader = new PdfReader(new ReportExportService(templates).attendancePdf(report));
        try {
            String pdfText = new PdfTextExtractor(reader).getTextFromPage(1);
            assertThat(pdfText).contains("20").contains("19").contains("1").contains("95.00%").contains("92.50%");
        } finally {
            reader.close();
        }
    }

    @Test
    void nonEmptyVietnameseProjectTaskDatasetKeepsActualHtmlWorkbookAndPdfParity() throws Exception {
        LocalDate dueDate = LocalDate.of(2026, 9, 30);
        Instant createdAt = Instant.parse("2026-08-22T04:00:00Z");
        ProjectTaskReportFilter filter = new ProjectTaskReportFilter(
                7L, 41L, TaskStatus.IN_PROGRESS,
                LocalDate.of(2026, 9, 1), dueDate,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31));
        ProjectTaskReportRow row = new ProjectTaskReportRow(
                7L, "Dự án Hà Nội", 42L, "Xây dựng báo cáo Nguyễn",
                "Nguyễn Mai", 41L, TaskStatus.IN_PROGRESS, dueDate,
                41L, 41L, createdAt, createdAt, 45L);
        EnumMap<TaskStatus, Long> statusCounts = new EnumMap<>(TaskStatus.class);
        for (TaskStatus status : TaskStatus.values()) statusCounts.put(status, 0L);
        statusCounts.put(TaskStatus.IN_PROGRESS, 1L);
        ProjectTaskReportView report = new ProjectTaskReportView(
                filter,
                List.of(new ProjectSummary(7L, "Dự án Hà Nội", "ACTIVE",
                        LocalDate.of(2026, 8, 1), LocalDate.of(2026, 12, 31))),
                List.of(new ProjectTaskMemberView(41L, 7L, "Nguyễn Mai", createdAt)),
                List.of(row),
                1L, 0L, "0.0%", statusCounts, 90L, true,
                List.of(new ProjectTaskReportMemberHours(41L, "Nguyễn Mai", 60L)));

        SpringTemplateEngine templates = actualTemplates();
        MockServletContext servletContext = new MockServletContext();
        WebContext context = new WebContext(
                JakartaServletWebApplication.buildApplication(servletContext).buildExchange(
                        new MockHttpServletRequest(servletContext), new MockHttpServletResponse()),
                Locale.ENGLISH);
        context.setVariable("report", report);
        context.setVariable("statuses", List.of(TaskStatus.values()));
        context.setVariable("smtpRestricted", false);
        String html = templates.process("reports/project-tasks", context);
        assertThat(html)
                .contains("name=\"projectId\"", "name=\"memberMembershipId\"")
                .containsPattern("(?s)<option value=\"7\"\\s+selected=\"selected\">Dự án Hà Nội</option>")
                .containsPattern("(?s)<option value=\"41\"\\s+selected=\"selected\">Nguyễn Mai</option>")
                .containsPattern("(?s)<option value=\"IN_PROGRESS\"\\s+selected=\"selected\">IN_PROGRESS</option>")
                .contains("value=\"2026-09-01\"", "value=\"2026-09-30\"")
                .contains("value=\"2026-08-01\"", "value=\"2026-08-31\"")
                .contains("Dự án Hà Nội", "Xây dựng báo cáo Nguyễn", "IN_PROGRESS", "0.0%", "Nguyễn Mai", "60", "90", "45");

        ReportExportService exports = new ReportExportService(templates);
        try (XSSFWorkbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(exports.projectTaskXlsx(report)))) {
            var sheet = workbook.getSheet("Project Tasks");
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("Dự án Hà Nội");
            assertThat(sheet.getRow(4).getCell(0).getNumericCellValue()).isEqualTo(1);
            assertThat(sheet.getRow(4).getCell(2).getStringCellValue()).isEqualTo("0.0%");
            assertThat(sheet.getRow(4).getCell(4).getNumericCellValue()).isEqualTo(1);
            assertThat(sheet.getRow(4).getCell(6).getNumericCellValue()).isEqualTo(90);
            assertThat(sheet.getRow(10).getCell(1).getStringCellValue()).isEqualTo("Xây dựng báo cáo Nguyễn");
            assertThat(sheet.getRow(10).getCell(3).getStringCellValue()).isEqualTo("IN_PROGRESS");
            assertThat(sheet.getRow(10).getCell(5).getNumericCellValue()).isEqualTo(45);
        }
        PdfReader reader = new PdfReader(exports.projectTaskPdf(report));
        try {
            String pdfText = new PdfTextExtractor(reader).getTextFromPage(1);
            assertThat(pdfText).contains("Dự án Hà Nội", "Xây dựng báo cáo Nguyễn", "IN_PROGRESS", "0.0%", "45", "90");
        } finally {
            reader.close();
        }

        ProjectTaskReportView empty = new ProjectTaskReportView(
                new ProjectTaskReportFilter(7L, 41L, TaskStatus.IN_PROGRESS,
                        null, null, null, null),
                report.projectOptions(), report.memberOptions(), List.of(),
                0L, 0L, "N/A", emptyStatusCounts(), 0L, true,
                List.of(new ProjectTaskReportMemberHours(41L, "Nguyễn Mai", 0L)));
        String emptyHtml = renderProjectTasksHtml(templates, empty);
        assertThat(emptyHtml).contains("N/A");
        try (XSSFWorkbook workbook = new XSSFWorkbook(
                new ByteArrayInputStream(exports.projectTaskXlsx(empty)))) {
            assertThat(workbook.getSheet("Project Tasks").getRow(4).getCell(2).getStringCellValue())
                    .isEqualTo("N/A");
        }
        PdfReader emptyReader = new PdfReader(exports.projectTaskPdf(empty));
        try {
            assertThat(new PdfTextExtractor(emptyReader).getTextFromPage(1)).contains("N/A");
        } finally {
            emptyReader.close();
        }
    }

    private static SpringTemplateEngine actualTemplates() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode("HTML");
        resolver.setCacheable(false);
        SpringTemplateEngine templates = new SpringTemplateEngine();
        templates.setTemplateResolver(resolver);
        return templates;
    }

    private static String renderProjectTasksHtml(SpringTemplateEngine templates, ProjectTaskReportView report) {
        MockServletContext servletContext = new MockServletContext();
        WebContext context = new WebContext(
                JakartaServletWebApplication.buildApplication(servletContext).buildExchange(
                        new MockHttpServletRequest(servletContext), new MockHttpServletResponse()),
                Locale.ENGLISH);
        context.setVariable("report", report);
        context.setVariable("statuses", List.of(TaskStatus.values()));
        context.setVariable("smtpRestricted", false);
        return templates.process("reports/project-tasks", context);
    }

    private static EnumMap<TaskStatus, Long> emptyStatusCounts() {
        EnumMap<TaskStatus, Long> counts = new EnumMap<>(TaskStatus.class);
        for (TaskStatus status : TaskStatus.values()) counts.put(status, 0L);
        return counts;
    }
}
