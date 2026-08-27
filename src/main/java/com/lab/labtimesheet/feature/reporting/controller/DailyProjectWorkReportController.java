package com.lab.labtimesheet.feature.reporting.controller;

import com.lab.labtimesheet.feature.reporting.service.DailyProjectWorkReportService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Renders the authorized Daily Project Work Report dataset as an active HTML page. */
@Controller
@RequestMapping("/reports/daily")
@RequiredArgsConstructor
public class DailyProjectWorkReportController {

    private final DailyProjectWorkReportService reports;

    /**
     * Renders the current-business-date report by default, with an optional authorized Project
     * and past/current Report date.
     *
     * @param authentication authenticated owning Mentor or current Project Leader
     * @param projectId optional authorized Project filter
     * @param reportDate optional local Report date
     * @param dateAlias compatibility alias for links that use {@code date}
     * @param model Thymeleaf model
     * @return Daily report template
     */
    @GetMapping
    public String report(
            Authentication authentication,
            @RequestParam(required = false) Long projectId,
            @RequestParam(name = "reportDate", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate reportDate,
            @RequestParam(name = "date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateAlias,
            Model model) {
        if (reportDate != null && dateAlias != null && !reportDate.equals(dateAlias)) {
            throw new IllegalArgumentException("Report date parameters must match");
        }
        LocalDate requestedDate = reportDate != null ? reportDate : dateAlias;
        model.addAttribute("report", reports.build(authentication.getName(), projectId, requestedDate));
        return "reports/daily";
    }
}
