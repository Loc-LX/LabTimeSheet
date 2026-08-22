package com.lab.labtimesheet.feature.reporting.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.lab.labtimesheet.feature.account.model.dto.EligibleInternOption;
import com.lab.labtimesheet.feature.reporting.model.dto.AttendanceReportRow;
import com.lab.labtimesheet.feature.reporting.model.dto.AttendanceReportView;
import com.lab.labtimesheet.feature.reporting.model.dto.ProjectTaskReportFilter;
import com.lab.labtimesheet.feature.reporting.model.dto.ProjectTaskReportView;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.util.List;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.spring6.SpringTemplateEngine;

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
}
