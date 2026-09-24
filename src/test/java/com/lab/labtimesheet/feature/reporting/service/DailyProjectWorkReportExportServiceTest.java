package com.lab.labtimesheet.feature.reporting.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.lab.labtimesheet.feature.attendance.model.dto.AttendanceReportDateContext;
import com.lab.labtimesheet.feature.reporting.model.dto.DailyProjectWorkReportLog;
import com.lab.labtimesheet.feature.reporting.model.dto.DailyProjectWorkReportMember;
import com.lab.labtimesheet.feature.reporting.model.dto.DailyProjectWorkReportProject;
import com.lab.labtimesheet.feature.reporting.model.dto.DailyProjectWorkReportTask;
import com.lab.labtimesheet.feature.reporting.model.dto.DailyProjectWorkReportView;
import com.lab.labtimesheet.feature.project.model.TaskStatus;
import com.lab.labtimesheet.feature.project.model.TaskVarianceState;
import com.lab.labtimesheet.feature.project.model.dto.TaskRemainingEffortForecastSummary;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.thymeleaf.spring6.SpringTemplateEngine;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/** Public byte-level parity contracts for the Daily XLSX/PDF presentations. */
class DailyProjectWorkReportExportServiceTest {

    private static final LocalDate REPORT_DATE = LocalDate.of(2026, 8, 27);
    private static final Instant ASSIGNED_AT = Instant.parse("2026-08-01T00:00:00Z");

    @Test
    void dailyXlsxPreservesIndependentHierarchyFactsAndNumericCells() throws Exception {
        byte[] workbookBytes = new ReportExportService(null).dailyXlsx(sampleReport());

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(workbookBytes))) {
            var sheet = workbook.getSheet("Daily Work");
            assertThat(sheet).isNotNull();
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue())
                    .isEqualTo("Daily Project Work Report");
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("2026-08-27");
            assertThat(sheet.getRow(1).getCell(3).getStringCellValue()).isEqualTo("Global day off");
            assertThat(sheet.getRow(2).getCell(1).getStringCellValue()).isEqualTo("All authorized Projects");
            assertThat(sheet.getRow(2).getCell(3).getNumericCellValue()).isEqualTo(120);

            var inProgressRow = findTaskRow(sheet, "Xây dựng báo cáo");
            assertThat(inProgressRow.getCell(0).getStringCellValue()).isEqualTo("Dự án Hà Nội");
            assertThat(inProgressRow.getCell(1).getStringCellValue()).isEqualTo("Nguyễn Mai");
            assertThat(inProgressRow.getCell(3).getStringCellValue()).isEqualTo("IN_PROGRESS");
            assertThat(inProgressRow.getCell(4).getStringCellValue()).isEqualTo("");
            assertThat(inProgressRow.getCell(5).getStringCellValue())
                    .isEqualTo("Mô tả lặp (45 minutes)\nMô tả lặp (45 minutes)");
            assertThat(inProgressRow.getCell(6).getNumericCellValue()).isEqualTo(90);
            assertThat(inProgressRow.getCell(7).getNumericCellValue()).isEqualTo(150);
            assertThat(inProgressRow.getCell(8).getNumericCellValue()).isEqualTo(480);
            assertThat(inProgressRow.getCell(9).getNumericCellValue()).isEqualTo(120);
            assertThat(inProgressRow.getCell(10).getNumericCellValue()).isEqualTo(270);
            assertThat(inProgressRow.getCell(11).getStringCellValue()).isEqualTo("Pending");

            var doneRow = findTaskRow(sheet, "Đóng hồ sơ");
            assertThat(doneRow.getCell(0).getStringCellValue()).isEqualTo("Dự án Hà Nội");
            assertThat(doneRow.getCell(1).getStringCellValue()).isEqualTo("Trần Bình");
            assertThat(doneRow.getCell(3).getStringCellValue()).isEqualTo("DONE");
            assertThat(doneRow.getCell(4).getStringCellValue()).isEqualTo("Deleted Task; retained log");
            assertThat(doneRow.getCell(5).getStringCellValue()).isEqualTo("Hoàn tất kiểm tra (30 minutes)");
            assertThat(doneRow.getCell(6).getNumericCellValue()).isEqualTo(30);
            assertThat(doneRow.getCell(7).getNumericCellValue()).isEqualTo(150);
            assertThat(doneRow.getCell(8).getNumericCellValue()).isEqualTo(120);
            assertThat(doneRow.getCell(9).getStringCellValue()).isEqualTo("N/A");
            assertThat(doneRow.getCell(10).getStringCellValue()).isEqualTo("N/A");
            assertThat(doneRow.getCell(11).getNumericCellValue()).isEqualTo(30);
            assertThat(doneRow.getCell(11).getCellStyle().getDataFormatString())
                    .isEqualTo("+0;-0;0");

            assertThat(findRow(sheet, "Member subtotal", "Nguyễn Mai").getCell(2).getNumericCellValue())
                    .isEqualTo(90);
            assertThat(findRow(sheet, "Member subtotal", "Trần Bình").getCell(2).getNumericCellValue())
                    .isEqualTo(30);
            assertThat(findRow(sheet, "Project subtotal").getCell(2).getNumericCellValue()).isEqualTo(120);
            assertThat(findRow(sheet, "Overall total").getCell(3).getNumericCellValue()).isEqualTo(120);
        }
    }

    @Test
    void dailyPdfPreservesVietnameseHierarchyAndPlanningFactsAsExtractableText() throws Exception {
        byte[] pdfBytes = new ReportExportService(actualTemplates()).dailyPdf(sampleReport());
        assertThat(new String(pdfBytes, StandardCharsets.ISO_8859_1)).contains("/FontFile");

        PdfReader reader = new PdfReader(pdfBytes);
        try {
            String text = "";
            for (int page = 1; page <= reader.getNumberOfPages(); page++) {
                text += new PdfTextExtractor(reader).getTextFromPage(page);
            }
            text = text.replaceAll("\\s+", " ");
            assertThat(countOccurrences(text, "Mô tả lặp")).isEqualTo(2);
            int maiStart = text.indexOf("Nguyễn Mai");
            int maiSubtotal = text.indexOf("Member subtotal: 90 minutes", maiStart);
            int binhStart = text.indexOf("Trần Bình");
            int binhSubtotal = text.indexOf("Member subtotal: 30 minutes", binhStart);
            assertThat(maiStart).isGreaterThanOrEqualTo(0);
            assertThat(maiSubtotal).isGreaterThan(maiStart);
            assertThat(binhStart).isGreaterThan(maiSubtotal);
            assertThat(binhSubtotal).isGreaterThan(binhStart);
            assertThat(text.substring(maiStart, binhStart)).contains("90 minutes");
            assertThat(text.substring(binhStart, binhSubtotal)).contains("30 minutes");
            assertThat(text).containsPattern("Project subtotal: 120 minutes");
            assertThat(text).containsPattern("Overall total minutes 120");
            assertThat(text).contains(
                    "Daily Project Work Report", "2026-08-27", "Global day off", "Dự án Hà Nội",
                    "Nguyễn Mai", "Trần Bình", "Xây dựng báo cáo", "Đóng hồ sơ", "Mô tả lặp",
                    "Hoàn tất kiểm tra", "IN_PROGRESS", "DONE", "Deleted Task", "90", "150",
                    "480", "120", "270", "Pending", "N/A", "+30");
        } finally {
            reader.close();
        }
    }

    @Test
    void dailyExportsRenderTruthfulSelectedProjectEmptyStateAndContext() throws Exception {
        DailyProjectWorkReportView empty = new DailyProjectWorkReportView(
                REPORT_DATE,
                new AttendanceReportDateContext(
                        REPORT_DATE, false, true, 2L, LocalDate.of(2026, 1, 1),
                        ZoneId.of("Asia/Ho_Chi_Minh")),
                42L,
                "Dự án Hà Nội",
                List.of(),
                List.of(),
                0L);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(
                new ReportExportService(null).dailyXlsx(empty)))) {
            var sheet = workbook.getSheet("Daily Work");
            assertThat(sheet.getRow(1).getCell(1).getStringCellValue()).isEqualTo("2026-08-27");
            assertThat(sheet.getRow(1).getCell(3).getStringCellValue()).isEqualTo("Global day off");
            assertThat(sheet.getRow(3).getCell(0).getStringCellValue())
                    .isEqualTo("No retained Task work for this Project");
            assertThat(sheet.getRow(4).getCell(3).getNumericCellValue()).isEqualTo(0);
        }
        PdfReader reader = new PdfReader(new ReportExportService(actualTemplates()).dailyPdf(empty));
        try {
            assertThat(new PdfTextExtractor(reader).getTextFromPage(1))
                    .contains("No retained Task work for this Project", "Global day off", "2026-08-27");
        } finally {
            reader.close();
        }
    }

    @Test
    void dailyExportsRenderTruthfulGlobalEmptyStateAndContext() throws Exception {
        DailyProjectWorkReportView empty = new DailyProjectWorkReportView(
                REPORT_DATE,
                new AttendanceReportDateContext(
                        REPORT_DATE, false, true, 2L, LocalDate.of(2026, 1, 1),
                        ZoneId.of("Asia/Ho_Chi_Minh")),
                null,
                null,
                List.of(),
                List.of(),
                0L);

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(
                new ReportExportService(null).dailyXlsx(empty)))) {
            var sheet = workbook.getSheet("Daily Work");
            assertThat(sheet.getRow(2).getCell(1).getStringCellValue())
                    .isEqualTo("All authorized Projects");
            assertThat(sheet.getRow(3).getCell(0).getStringCellValue())
                    .isEqualTo("No retained Task work for this date");
            assertThat(sheet.getRow(4).getCell(0).getStringCellValue())
                    .isEqualTo("No authorized Project has retained Task work logs on this Report date.");
            assertThat(sheet.getRow(4).getCell(3).getNumericCellValue()).isEqualTo(0);
        }

        PdfReader reader = new PdfReader(new ReportExportService(actualTemplates()).dailyPdf(empty));
        try {
            String text = new PdfTextExtractor(reader).getTextFromPage(1).replaceAll("\\s+", " ");
            assertThat(text).contains(
                    "No retained Task work for this date",
                    "No authorized Project has retained Task work logs on this Report date.",
                    "All authorized Projects",
                    "Global day off",
                    "2026-08-27",
                    "0");
        } finally {
            reader.close();
        }
    }

    private static int countOccurrences(String text, String expected) {
        int count = 0;
        int next = 0;
        while ((next = text.indexOf(expected, next)) >= 0) {
            count++;
            next += expected.length();
        }
        return count;
    }

    private static DailyProjectWorkReportView sampleReport() {
        DailyProjectWorkReportTask inProgress = new DailyProjectWorkReportTask(
                501L,
                "Xây dựng báo cáo",
                8L,
                TaskStatus.IN_PROGRESS,
                90L,
                150L,
                480,
                TaskVarianceState.PENDING,
                null,
                ASSIGNED_AT,
                ASSIGNED_AT,
                false,
                List.of(
                        new DailyProjectWorkReportLog(101L, REPORT_DATE, "Mô tả lặp", 45),
                        new DailyProjectWorkReportLog(102L, REPORT_DATE, "Mô tả lặp", 45)),
                new TaskRemainingEffortForecastSummary(120, 150L, ASSIGNED_AT));
        DailyProjectWorkReportTask done = new DailyProjectWorkReportTask(
                502L,
                "Đóng hồ sơ",
                8L,
                TaskStatus.DONE,
                30L,
                150L,
                120,
                TaskVarianceState.VALUE,
                30L,
                ASSIGNED_AT,
                ASSIGNED_AT,
                true,
                List.of(new DailyProjectWorkReportLog(103L, REPORT_DATE, "Hoàn tất kiểm tra", 30)),
                null);
        return new DailyProjectWorkReportView(
                REPORT_DATE,
                new AttendanceReportDateContext(
                        REPORT_DATE, false, true, 2L, LocalDate.of(2026, 1, 1),
                        ZoneId.of("Asia/Ho_Chi_Minh")),
                null,
                null,
                List.of(),
                List.of(new DailyProjectWorkReportProject(
                        42L,
                        "Dự án Hà Nội",
                        List.of(
                                new DailyProjectWorkReportMember(7L, "Nguyễn Mai", List.of(inProgress), 90L),
                                new DailyProjectWorkReportMember(8L, "Trần Bình", List.of(done), 30L)),
                        120L)),
                120L);
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

    private static org.apache.poi.ss.usermodel.Row findRow(
            org.apache.poi.ss.usermodel.Sheet sheet, String firstCell) {
        for (org.apache.poi.ss.usermodel.Row row : sheet) {
            if (row.getCell(0) != null && firstCell.equals(row.getCell(0).getStringCellValue())) {
                return row;
            }
        }
        throw new AssertionError("Missing row: " + firstCell);
    }

    private static org.apache.poi.ss.usermodel.Row findTaskRow(
            org.apache.poi.ss.usermodel.Sheet sheet, String taskTitle) {
        for (org.apache.poi.ss.usermodel.Row row : sheet) {
            if (row.getCell(2) != null
                    && row.getCell(2).getCellType() == CellType.STRING
                    && taskTitle.equals(row.getCell(2).getStringCellValue())) {
                return row;
            }
        }
        throw new AssertionError("Missing Task row: " + taskTitle);
    }

    private static org.apache.poi.ss.usermodel.Row findRow(
            org.apache.poi.ss.usermodel.Sheet sheet, String firstCell, String secondCell) {
        for (org.apache.poi.ss.usermodel.Row row : sheet) {
            if (row.getCell(0) != null && row.getCell(1) != null
                    && firstCell.equals(row.getCell(0).getStringCellValue())
                    && secondCell.equals(row.getCell(1).getStringCellValue())) {
                return row;
            }
        }
        throw new AssertionError("Missing row: " + firstCell + ": " + secondCell);
    }
}
