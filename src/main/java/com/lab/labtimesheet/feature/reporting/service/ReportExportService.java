package com.lab.labtimesheet.feature.reporting.service;

import com.lab.labtimesheet.feature.reporting.model.dto.AttendanceReportRow;
import com.lab.labtimesheet.feature.reporting.model.dto.AttendanceReportView;
import com.lab.labtimesheet.feature.reporting.model.dto.DailyProjectWorkReportMember;
import com.lab.labtimesheet.feature.reporting.model.dto.DailyProjectWorkReportProject;
import com.lab.labtimesheet.feature.reporting.model.dto.DailyProjectWorkReportTask;
import com.lab.labtimesheet.feature.reporting.model.dto.DailyProjectWorkReportView;
import com.lab.labtimesheet.feature.reporting.model.dto.ProjectTaskReportMemberHours;
import com.lab.labtimesheet.feature.reporting.model.dto.ProjectTaskReportRow;
import com.lab.labtimesheet.feature.reporting.model.dto.ProjectTaskReportView;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.openpdf.pdf.ITextRenderer;
import org.openpdf.text.pdf.BaseFont;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring6.SpringTemplateEngine;

/**
 * Renders the already-authorized Reporting DTOs into downloadable workbook and PDF bytes.
 *
 * <p>This class deliberately does not query a repository or re-evaluate authorization. The
 * report services build one immutable dataset for HTML and this exporter only changes its
 * presentation format, keeping filters, rows, totals, and {@code N/A} values aligned.</p>
 */
@Service
@RequiredArgsConstructor
public class ReportExportService {

    private static final String FONT_RESOURCE = "liberation/LiberationSans-Regular.ttf";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd/MM/uuuu");
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm").withZone(ZoneId.of("Asia/Ho_Chi_Minh"));

    private final SpringTemplateEngine templates;

    /**
     * Creates an XLSX attendance/compliance report.
     *
     * @param report authorized attendance dataset also rendered by HTML
     * @return complete XLSX document bytes
     */
    public byte[] attendanceXlsx(AttendanceReportView report) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Attendance");
            CellStyle heading = headingStyle(workbook);
            CellStyle title = titleStyle(workbook);
            writeAttendance(sheet, report, heading, title);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create attendance workbook", exception);
        }
    }

    /**
     * Creates an XLSX Project/Task report.
     *
     * @param report authorized Project/Task dataset also rendered by HTML
     * @return complete XLSX document bytes
     */
    public byte[] projectTaskXlsx(ProjectTaskReportView report) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Project Tasks");
            CellStyle heading = headingStyle(workbook);
            CellStyle title = titleStyle(workbook);
            writeProjectTasks(sheet, report, heading, title);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create Project/Task workbook", exception);
        }
    }

    /**
     * Creates an XLSX Daily Project Work Report from the already-authorized immutable dataset.
     *
     * <p>Minute values and DONE variance values are written as numeric cells. Textual planning
     * states such as {@code N/A} and {@code Pending} remain text so spreadsheet consumers cannot
     * mistake an unavailable fact for zero.</p>
     *
     * @param report authorized Daily Project Work Report dataset
     * @return complete Daily XLSX document bytes
     */
    public byte[] dailyXlsx(DailyProjectWorkReportView report) {
        try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Daily Work");
            CellStyle heading = headingStyle(workbook);
            CellStyle title = titleStyle(workbook);
            CellStyle signedVariance = signedVarianceStyle(workbook);
            writeDaily(sheet, report, heading, title, signedVariance);
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to create Daily Project Work workbook", exception);
        }
    }

    /**
     * Creates a print-safe attendance PDF using the dedicated XHTML template.
     *
     * @param report authorized attendance dataset also rendered by HTML
     * @return complete PDF document bytes
     */
    public byte[] attendancePdf(AttendanceReportView report) {
        return pdf("reports/print", "attendance", report);
    }

    /**
     * Creates a print-safe Project/Task PDF using the dedicated XHTML template.
     *
     * @param report authorized Project/Task dataset also rendered by HTML
     * @return complete PDF document bytes
     */
    public byte[] projectTaskPdf(ProjectTaskReportView report) {
        return pdf("reports/print", "project-tasks", report);
    }

    /**
     * Creates a print-safe Daily Project Work Report PDF using its dedicated XHTML template.
     *
     * @param report authorized Daily Project Work Report dataset
     * @return complete Daily PDF document bytes with embedded Unicode-capable font
     */
    public byte[] dailyPdf(DailyProjectWorkReportView report) {
        return pdf("reports/daily-print", "daily", report);
    }

    private byte[] pdf(String templateName, String reportKind, Object report) {
        Context context = new Context(Locale.ENGLISH);
        context.setVariable("reportKind", reportKind);
        context.setVariable("report", report);
        if (report instanceof ProjectTaskReportView projectTaskReport) {
            context.setVariable("projectFilterName", projectFilterName(projectTaskReport));
            context.setVariable("memberFilterName", memberName(projectTaskReport));
            context.setVariable("statusFilterName", projectTaskReport.filter().status() == null
                    ? "All statuses" : projectTaskReport.filter().status().name());
            context.setVariable("dueFilterRange", dateRange(
                    projectTaskReport.filter().dueFrom(), projectTaskReport.filter().dueTo()));
            context.setVariable("workFilterRange", dateRange(
                    projectTaskReport.filter().workFrom(), projectTaskReport.filter().workTo()));
        }
        String markup = templates.process(templateName, context);
        try (ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            ITextRenderer renderer = new ITextRenderer();
            renderer.getFontResolver().addFont(FONT_RESOURCE, BaseFont.IDENTITY_H, BaseFont.EMBEDDED);
            renderer.setDocumentFromString(markup);
            renderer.layout();
            renderer.createPDF(output);
            renderer.finishPDF();
            return output.toByteArray();
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Unable to create report PDF", exception);
        }
    }

    private static void writeAttendance(
            Sheet sheet, AttendanceReportView report, CellStyle heading, CellStyle title) {
        Row titleRow = sheet.createRow(0);
        titleRow.createCell(0).setCellValue("Attendance compliance report");
        titleRow.getCell(0).setCellStyle(title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 6));
        Row scope = sheet.createRow(1);
        scope.createCell(0).setCellValue("Intern");
        scope.createCell(1).setCellValue(report.targetName());
        scope.createCell(2).setCellValue("Period");
        scope.createCell(3).setCellValue(DATE.format(report.from()) + " – " + DATE.format(report.to()));

        Row summaryHeading = sheet.createRow(3);
        String[] summaryLabels = {
            "Expected workdays", "Present workdays", "Absent workdays", "Attendance rate", "Compliance rate"
        };
        for (int index = 0; index < summaryLabels.length; index++) {
            Cell cell = summaryHeading.createCell(index);
            cell.setCellValue(summaryLabels[index]);
            cell.setCellStyle(heading);
        }
        Row summary = sheet.createRow(4);
        summary.createCell(0).setCellValue(report.expectedWorkdays());
        summary.createCell(1).setCellValue(report.presentWorkdays());
        summary.createCell(2).setCellValue(report.absentWorkdays());
        summary.createCell(3).setCellValue(report.attendanceRate());
        summary.createCell(4).setCellValue(report.complianceRate());

        Row header = sheet.createRow(6);
        String[] columns = {"Work date", "Check in", "Raw checkout", "Effective checkout", "Schedule", "Result", "Elapsed"};
        for (int index = 0; index < columns.length; index++) {
            Cell cell = header.createCell(index);
            cell.setCellValue(columns[index]);
            cell.setCellStyle(heading);
        }
        int rowNumber = 7;
        for (AttendanceReportRow reportRow : report.rows()) {
            Row row = sheet.createRow(rowNumber++);
            String[] values = {
                reportRow.workDate(), reportRow.checkIn(), reportRow.rawCheckout(), reportRow.effectiveCheckout(),
                reportRow.schedule(), reportRow.result(), reportRow.workedMinutes()
            };
            for (int index = 0; index < values.length; index++) {
                row.createCell(index).setCellValue(values[index]);
            }
        }
        autosize(sheet, columns.length);
    }

    private static void writeProjectTasks(
            Sheet sheet, ProjectTaskReportView report, CellStyle heading, CellStyle title) {
        Row titleRow = sheet.createRow(0);
        titleRow.createCell(0).setCellValue("Project and Task report");
        titleRow.getCell(0).setCellStyle(title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 7));
        Row filter = sheet.createRow(1);
        filter.createCell(0).setCellValue("Project");
        filter.createCell(1).setCellValue(projectFilterName(report));
        filter.createCell(2).setCellValue("Member");
        filter.createCell(3).setCellValue(memberName(report));
        filter.createCell(4).setCellValue("Status");
        filter.createCell(5).setCellValue(report.filter().status() == null
                ? "All statuses" : report.filter().status().name());
        Row filterRanges = sheet.createRow(2);
        filterRanges.createCell(0).setCellValue("Due");
        filterRanges.createCell(1).setCellValue(dateRange(
                report.filter().dueFrom(), report.filter().dueTo()));
        filterRanges.createCell(2).setCellValue("Work");
        filterRanges.createCell(3).setCellValue(dateRange(
                report.filter().workFrom(), report.filter().workTo()));

        Row summaryHeading = sheet.createRow(3);
        String[] summaryLabels = {
            "Tasks", "Done", "Completion", "TODO", "In progress", "Blocked", "Logged minutes"
        };
        for (int index = 0; index < summaryLabels.length; index++) {
            Cell cell = summaryHeading.createCell(index);
            cell.setCellValue(summaryLabels[index]);
            cell.setCellStyle(heading);
        }
        Row summary = sheet.createRow(4);
        summary.createCell(0).setCellValue(report.totalTasks());
        summary.createCell(1).setCellValue(report.doneTasks());
        summary.createCell(2).setCellValue(report.completionRate());
        summary.createCell(3).setCellValue(report.todoTasks());
        summary.createCell(4).setCellValue(report.inProgressTasks());
        summary.createCell(5).setCellValue(report.blockedTasks());
        summary.createCell(6).setCellValue(report.totalLoggedMinutes());

        int rowNumber = 6;
        if (report.detailedMemberHours()) {
            Row memberHeading = sheet.createRow(rowNumber++);
            memberHeading.createCell(0).setCellValue("Member");
            memberHeading.createCell(1).setCellValue("Logged minutes");
            memberHeading.getCell(0).setCellStyle(heading);
            memberHeading.getCell(1).setCellStyle(heading);
            for (ProjectTaskReportMemberHours member : report.memberHours()) {
                Row row = sheet.createRow(rowNumber++);
                row.createCell(0).setCellValue(member.displayName());
                row.createCell(1).setCellValue(member.totalMinutes());
            }
            rowNumber++;
        }
        Row header = sheet.createRow(rowNumber++);
        String[] columns = {"Project", "Task", "Assignee", "Status", "Due", "Logged minutes", "Created"};
        for (int index = 0; index < columns.length; index++) {
            Cell cell = header.createCell(index);
            cell.setCellValue(columns[index]);
            cell.setCellStyle(heading);
        }
        for (ProjectTaskReportRow reportRow : report.rows()) {
            Row row = sheet.createRow(rowNumber++);
            row.createCell(0).setCellValue(reportRow.projectName());
            row.createCell(1).setCellValue(reportRow.title());
            row.createCell(2).setCellValue(reportRow.assigneeName());
            row.createCell(3).setCellValue(reportRow.status().name());
            row.createCell(4).setCellValue(reportRow.dueDate() == null ? "N/A" : DATE.format(reportRow.dueDate()));
            row.createCell(5).setCellValue(reportRow.loggedMinutes());
            row.createCell(6).setCellValue(DATE_TIME.format(reportRow.createdAt()));
        }
        autosize(sheet, columns.length);
    }

    private static void writeDaily(
            Sheet sheet,
            DailyProjectWorkReportView report,
            CellStyle heading,
            CellStyle title,
            CellStyle signedVariance) {
        Row titleRow = sheet.createRow(0);
        titleRow.createCell(0).setCellValue("Daily Project Work Report");
        titleRow.getCell(0).setCellStyle(title);
        sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 11));

        Row contextRow = sheet.createRow(1);
        contextRow.createCell(0).setCellValue("Report date");
        contextRow.createCell(1).setCellValue(report.reportDate().toString());
        contextRow.createCell(2).setCellValue("Day context");
        contextRow.createCell(3).setCellValue(report.dayContext().label());

        Row scopeRow = sheet.createRow(2);
        scopeRow.createCell(0).setCellValue("Project scope");
        scopeRow.createCell(1).setCellValue(report.selectedProject()
                ? report.selectedProjectName()
                : "All authorized Projects");
        scopeRow.createCell(2).setCellValue("Overall total");
        scopeRow.createCell(3).setCellValue(report.overallTotalMinutes());

        if (!report.hasRows()) {
            Row emptyTitle = sheet.createRow(3);
            emptyTitle.createCell(0).setCellValue(report.emptyTitle());
            Row emptyDescription = sheet.createRow(4);
            emptyDescription.createCell(0).setCellValue(report.emptyDescription());
            emptyDescription.createCell(3).setCellValue(0L);
            autosize(sheet, 12);
            return;
        }

        String[] columns = {
            "Project", "Author", "Task", "Current status", "Task state", "Work descriptions",
            "Selected-date minutes", "Lifetime actual minutes", "Original estimate minutes",
            "Forecast remaining minutes", "Forecast total minutes", "DONE variance"
        };
        Row header = sheet.createRow(3);
        for (int index = 0; index < columns.length; index++) {
            Cell cell = header.createCell(index);
            cell.setCellValue(columns[index]);
            cell.setCellStyle(heading);
        }

        int rowNumber = 4;
        for (DailyProjectWorkReportProject project : report.projects()) {
            for (DailyProjectWorkReportMember member : project.members()) {
                for (DailyProjectWorkReportTask task : member.tasks()) {
                    Row row = sheet.createRow(rowNumber++);
                    row.createCell(0).setCellValue(project.projectName());
                    row.createCell(1).setCellValue(member.displayName());
                    row.createCell(2).setCellValue(task.title());
                    row.createCell(3).setCellValue(task.status().name());
                    row.createCell(4).setCellValue(task.deleted() ? "Deleted Task; retained log" : "");
                    row.createCell(5).setCellValue(task.logs().stream()
                            .map(log -> log.description() + " (" + log.minutes() + " minutes)")
                            .collect(Collectors.joining("\n")));
                    row.createCell(6).setCellValue(task.selectedDateMinutes());
                    row.createCell(7).setCellValue(task.lifetimeActualMinutes());
                    setOptionalNumber(row, 8, task.estimatedMinutes(), task.estimateDisplay());
                    if (task.latestForecast() == null) {
                        row.createCell(9).setCellValue("N/A");
                        row.createCell(10).setCellValue("N/A");
                    } else {
                        row.createCell(9).setCellValue(task.latestForecast().remainingMinutes());
                        row.createCell(10).setCellValue(task.latestForecast().forecastTotalMinutes());
                    }
                    if (task.varianceMinutes() == null) {
                        row.createCell(11).setCellValue(task.varianceDisplay());
                    } else {
                        Cell variance = row.createCell(11);
                        variance.setCellValue(task.varianceMinutes());
                        variance.setCellStyle(signedVariance);
                    }
                }
                Row memberTotal = sheet.createRow(rowNumber++);
                memberTotal.createCell(0).setCellValue("Member subtotal");
                memberTotal.createCell(1).setCellValue(member.displayName());
                memberTotal.createCell(2).setCellValue(member.totalMinutes());
            }
            Row projectTotal = sheet.createRow(rowNumber++);
            projectTotal.createCell(0).setCellValue("Project subtotal");
            projectTotal.createCell(1).setCellValue(project.projectName());
            projectTotal.createCell(2).setCellValue(project.totalMinutes());
        }
        Row overallTotal = sheet.createRow(rowNumber);
        overallTotal.createCell(0).setCellValue("Overall total");
        overallTotal.createCell(3).setCellValue(report.overallTotalMinutes());
        autosize(sheet, columns.length);
    }

    private static void setOptionalNumber(Row row, int column, Integer value, String emptyValue) {
        if (value == null) {
            row.createCell(column).setCellValue(emptyValue);
        } else {
            row.createCell(column).setCellValue(value);
        }
    }

    private static String projectName(ProjectTaskReportView report) {
        return report.projectOptions().stream()
                .filter(project -> report.filter().projectId() != null
                        && project.id() == report.filter().projectId())
                .map(project -> project.name())
                .findFirst()
                .orElse("Selected Project");
    }

    private static String projectFilterName(ProjectTaskReportView report) {
        return report.filter().projectId() == null ? "Select a Project" : projectName(report);
    }

    private static String memberName(ProjectTaskReportView report) {
        if (report.filter().memberMembershipId() == null) {
            return "All current members";
        }
        return report.memberOptions().stream()
                .filter(member -> member.membershipId() == report.filter().memberMembershipId())
                .map(member -> member.displayName())
                .findFirst()
                .orElse("Selected Member");
    }

    private static String dateRange(LocalDate from, LocalDate to) {
        if (from == null && to == null) {
            return "Any";
        }
        String start = from == null ? "—" : DATE.format(from);
        String end = to == null ? "—" : DATE.format(to);
        return start + " – " + end;
    }

    private static CellStyle titleStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 14);
        style.setFont(font);
        return style;
    }

    private static CellStyle headingStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        style.setFont(font);
        return style;
    }

    private static CellStyle signedVarianceStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        style.setDataFormat(workbook.createDataFormat().getFormat("+0;-0;0"));
        return style;
    }

    private static void autosize(Sheet sheet, int columns) {
        for (int index = 0; index < columns; index++) {
            sheet.autoSizeColumn(index);
            sheet.setColumnWidth(index, Math.min(sheet.getColumnWidth(index), 80 * 256));
        }
        sheet.createFreezePane(0, 7);
    }
}
