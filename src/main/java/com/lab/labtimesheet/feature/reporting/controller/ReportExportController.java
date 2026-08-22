package com.lab.labtimesheet.feature.reporting.controller;

import com.lab.labtimesheet.feature.reporting.model.dto.AttendanceReportView;
import com.lab.labtimesheet.feature.reporting.model.dto.ProjectTaskReportView;
import com.lab.labtimesheet.feature.reporting.service.AttendanceReportService;
import com.lab.labtimesheet.feature.reporting.service.ProjectTaskReportService;
import com.lab.labtimesheet.feature.reporting.service.ReportExportService;
import com.lab.labtimesheet.feature.task.model.TaskStatus;
import java.security.Principal;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/** Serves bounded XLSX and print-safe PDF representations of the shared report datasets. */
@Controller
@RequestMapping("/reports")
@RequiredArgsConstructor
public class ReportExportController {

    private static final int MAX_RANGE_DAYS = 366;
    private static final MediaType XLSX = MediaType.parseMediaType(
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final AttendanceReportService attendanceReports;
    private final ProjectTaskReportService projectTaskReports;
    private final ReportExportService exports;

    /**
     * Downloads the authorization-scoped attendance dataset as XLSX.
     *
     * @param principal authenticated account
     * @param internId optional authorized detail target
     * @param from optional inclusive local-date lower bound
     * @param to optional inclusive local-date upper bound
     * @return workbook attachment with safe deterministic filename
     */
    @GetMapping("/attendance.xlsx")
    public ResponseEntity<byte[]> attendanceXlsx(
            Principal principal,
            @RequestParam(required = false) Long internId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        validateOptionalRange(from, to);
        AttendanceReportView report = attendanceReports.build(principal, internId, from, to);
        validateRange(report.from(), report.to());
        return attachment(exports.attendanceXlsx(report), XLSX,
                "attendance-report-" + report.from() + "-to-" + report.to() + ".xlsx");
    }

    /**
     * Downloads the authorization-scoped attendance dataset as PDF.
     *
     * @param principal authenticated account
     * @param internId optional authorized detail target
     * @param from optional inclusive local-date lower bound
     * @param to optional inclusive local-date upper bound
     * @return print-safe PDF attachment with safe deterministic filename
     */
    @GetMapping("/attendance.pdf")
    public ResponseEntity<byte[]> attendancePdf(
            Principal principal,
            @RequestParam(required = false) Long internId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        validateOptionalRange(from, to);
        AttendanceReportView report = attendanceReports.build(principal, internId, from, to);
        validateRange(report.from(), report.to());
        return attachment(exports.attendancePdf(report), MediaType.APPLICATION_PDF,
                "attendance-report-" + report.from() + "-to-" + report.to() + ".pdf");
    }

    /**
     * Downloads the authorization-scoped Project/Task dataset as XLSX.
     *
     * @param authentication authenticated account
     * @param projectId optional visible Project
     * @param memberMembershipId optional visible member filter
     * @param status optional Task status filter
     * @param dueFrom required inclusive due-date lower bound
     * @param dueTo required inclusive due-date upper bound
     * @param workFrom required inclusive work-date lower bound
     * @param workTo required inclusive work-date upper bound
     * @return workbook attachment with safe deterministic filename
     */
    @GetMapping("/project-tasks.xlsx")
    public ResponseEntity<byte[]> projectTaskXlsx(
            Authentication authentication,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long memberMembershipId,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate workFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate workTo) {
        validateProjectTaskExportRanges(dueFrom, dueTo, workFrom, workTo);
        ProjectTaskReportView report = projectTaskReports.build(
                authentication.getName(), projectId, memberMembershipId, status,
                dueFrom, dueTo, workFrom, workTo);
        return attachment(exports.projectTaskXlsx(report), XLSX,
                projectFilename(".xlsx", dueFrom, dueTo, workFrom, workTo));
    }

    /**
     * Downloads the authorization-scoped Project/Task dataset as PDF.
     *
     * @param authentication authenticated account
     * @param projectId optional visible Project
     * @param memberMembershipId optional visible member filter
     * @param status optional Task status filter
     * @param dueFrom required inclusive due-date lower bound
     * @param dueTo required inclusive due-date upper bound
     * @param workFrom required inclusive work-date lower bound
     * @param workTo required inclusive work-date upper bound
     * @return print-safe PDF attachment with safe deterministic filename
     */
    @GetMapping("/project-tasks.pdf")
    public ResponseEntity<byte[]> projectTaskPdf(
            Authentication authentication,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long memberMembershipId,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate workFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate workTo) {
        validateProjectTaskExportRanges(dueFrom, dueTo, workFrom, workTo);
        ProjectTaskReportView report = projectTaskReports.build(
                authentication.getName(), projectId, memberMembershipId, status,
                dueFrom, dueTo, workFrom, workTo);
        return attachment(exports.projectTaskPdf(report), MediaType.APPLICATION_PDF,
                projectFilename(".pdf", dueFrom, dueTo, workFrom, workTo));
    }

    private static void validateProjectTaskExportRanges(
            LocalDate dueFrom, LocalDate dueTo, LocalDate workFrom, LocalDate workTo) {
        validateRequiredRange("due", dueFrom, dueTo);
        validateRequiredRange("work", workFrom, workTo);
    }

    private static void validateOptionalRange(LocalDate from, LocalDate to) {
        if (from != null || to != null) {
            validateRequiredRange("attendance", from, to);
        }
    }

    private static void validateRequiredRange(String label, LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Report exports require complete bounded date ranges");
        }
        try {
            validateRange(from, to);
        } catch (IllegalArgumentException invalidRange) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Project/Task " + label + " date range is invalid", invalidRange);
        }
    }

    private static void validateRange(LocalDate from, LocalDate to) {
        if (from.isAfter(to) || from.plusDays(MAX_RANGE_DAYS - 1L).isBefore(to)) {
            throw new IllegalArgumentException("Report date range must be between 1 and 366 days");
        }
    }

    private static String projectFilename(
            String extension, LocalDate dueFrom, LocalDate dueTo, LocalDate workFrom, LocalDate workTo) {
        LocalDate from = earlier(dueFrom, workFrom);
        LocalDate to = later(dueTo, workTo);
        return "project-task-report-" + (from == null ? "all" : from)
                + "-to-" + (to == null ? "all" : to) + extension;
    }

    private static LocalDate earlier(LocalDate first, LocalDate second) {
        if (first == null) {
            return second;
        }
        return second == null || first.isBefore(second) ? first : second;
    }

    private static LocalDate later(LocalDate first, LocalDate second) {
        if (first == null) {
            return second;
        }
        return second == null || first.isAfter(second) ? first : second;
    }

    private static ResponseEntity<byte[]> attachment(byte[] body, MediaType type, String filename) {
        return ResponseEntity.ok()
                .contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(filename).build().toString())
                .body(body);
    }
}
