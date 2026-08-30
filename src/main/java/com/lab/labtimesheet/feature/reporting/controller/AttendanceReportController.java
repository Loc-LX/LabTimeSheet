package com.lab.labtimesheet.feature.reporting.controller;

import com.lab.labtimesheet.feature.reporting.service.AttendanceReportService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Renders the authorized attendance/compliance HTML dataset with inclusive local-date filters.
 */
@Controller
@RequestMapping("/reports/attendance")
@RequiredArgsConstructor
public class AttendanceReportController {

    private final AttendanceReportService reports;

    /**
     * Renders own Intern scope or an explicitly selected Mentor/Admin detail scope.
     *
     * @param authentication authenticated application identity and role
     * @param internId optional target Intern for detail scope
     * @param from optional inclusive local start date
     * @param to optional inclusive local end date
     * @param model Thymeleaf model
     * @return attendance report template
     */
    @GetMapping
    public String report(
            Authentication authentication,
            @RequestParam(required = false) Long internId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Model model) {
        OperationalReportAuthorization.requireAttendanceReportAccess(authentication);
        model.addAttribute("report", reports.build(authentication, internId, from, to));
        return "reports/attendance";
    }
}
