package com.lab.labtimesheet.feature.reporting.controller;

import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRole;
import com.lab.labtimesheet.feature.attendance.service.AttendanceApplicationService;
import com.lab.labtimesheet.feature.attendance.service.AttendanceCurrentUserService;
import com.lab.labtimesheet.feature.reporting.model.dto.AttendanceDailyScore;
import com.lab.labtimesheet.feature.reporting.model.dto.AttendanceReportDay;
import com.lab.labtimesheet.feature.reporting.model.dto.AttendanceReportSummary;
import com.lab.labtimesheet.feature.reporting.service.AttendanceReportDataProvider;
import com.lab.labtimesheet.feature.reporting.service.AttendanceReportService;
import com.lab.labtimesheet.feature.reporting.service.ReportChartJson;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/**
 * Server-rendered attendance and compliance report page.
 *
 * <p>The Attendance feature supplies classified day rows through {@link AttendanceReportDataProvider};
 * this controller only applies shared formulas and renders them, keeping every output format from
 * drifting (RPT-001, RPT-009). Until the Attendance feature registers a provider, the page reports
 * {@code 404 Not Found} rather than fabricating rows.
 */
@Controller
@RequestMapping("/reports/attendance")
@RequiredArgsConstructor
public class AttendanceReportController {

    private final ObjectProvider<AttendanceReportDataProvider> reports;
    private final AttendanceReportService reportFormulas;
    private final AttendanceCurrentUserService currentUsers;
    private final AttendanceApplicationService attendance;

    /**
     * Renders the classified attendance/compliance report for the authenticated Intern or a target
     * Intern inspected by a Mentor or Admin.
     *
     * @param principal authenticated user
     * @param internId optional target Intern for Mentor and Admin reports
     * @param from optional inclusive local start date
     * @param to optional inclusive local end date
     * @param model Thymeleaf model
     * @return attendance report view name
     */
    @GetMapping
    public String report(
            Principal principal,
            @RequestParam(required = false) Long internId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Model model) {
        AttendanceActor actor = currentUsers.actor(principal);
        long targetInternId = targetInternId(actor, internId);
        LocalDate effectiveTo = to == null ? attendance.currentBusinessDate() : to;
        LocalDate effectiveFrom = from == null ? effectiveTo.withDayOfMonth(1) : from;
        AttendanceReportDataProvider provider = reports.getIfAvailable();
        if (provider == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "The attendance report is not available until the attendance feed is integrated");
        }
        List<AttendanceReportDay> days =
                provider.reportDays(actor, targetInternId, effectiveFrom, effectiveTo);
        AttendanceReportSummary summary = reportFormulas.summarize(days);
        List<AttendanceDailyScore> series = reportFormulas.dailyScoreSeries(days);

        model.addAttribute("days", days);
        model.addAttribute("summary", summary);
        model.addAttribute("dailyScores", series.stream()
                .collect(Collectors.toMap(
                        AttendanceDailyScore::date, AttendanceDailyScore::score)));
        model.addAttribute("from", effectiveFrom);
        model.addAttribute("to", effectiveTo);
        model.addAttribute("ownReport", actor.userId() == targetInternId);
        model.addAttribute("targetInternId", targetInternId);
        model.addAttribute("chartJson", series.isEmpty() ? null : complianceChartJson(series));
        return "reports/attendance";
    }

    private long targetInternId(AttendanceActor actor, Long internId) {
        if (actor.role() == AttendanceRole.INTERN) {
            if (internId != null && internId.longValue() != actor.userId()) {
                throw new AccessDeniedException("Interns may view only their own attendance");
            }
            return actor.userId();
        }
        if (internId == null) {
            throw new IllegalArgumentException("internId is required for Mentor and Admin reports");
        }
        return internId;
    }

    private String complianceChartJson(List<AttendanceDailyScore> series) {
        return ReportChartJson.line(
                "Daily compliance",
                series.stream().map(AttendanceDailyScore::date).map(Object::toString).toList(),
                series.stream().map(AttendanceDailyScore::score).toList());
    }
}