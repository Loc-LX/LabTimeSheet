package com.lab.labtimesheet.feature.reporting.controller;

import com.lab.labtimesheet.feature.reporting.service.ProjectTaskReportService;
import com.lab.labtimesheet.feature.project.model.TaskStatus;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Renders the authorization-scoped Project/Task HTML report and its filter controls. */
@Controller
@RequestMapping("/reports/project-tasks")
@RequiredArgsConstructor
public class ProjectTaskReportController {

    private final ProjectTaskReportService reports;

    /**
     * Renders the report for all visible Project options or one selected Project.
     *
     * @param authentication authenticated account
     * @param projectId optional visible Project identifier
     * @param memberMembershipId optional current membership filter
     * @param status optional Task status filter
     * @param dueFrom optional inclusive due-date lower bound
     * @param dueTo optional inclusive due-date upper bound
     * @param workFrom optional inclusive work-date lower bound
     * @param workTo optional inclusive work-date upper bound
     * @param model Thymeleaf model
     * @return Project/Task report template
     */
    @GetMapping
    public String report(
            Authentication authentication,
            @RequestParam(required = false) Long projectId,
            @RequestParam(required = false) Long memberMembershipId,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate workFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate workTo,
            Model model) {
        OperationalReportAuthorization.requireOperationalReportAccess(authentication);
        model.addAttribute("report", reports.build(
                authentication.getName(), projectId, memberMembershipId, status, dueFrom, dueTo, workFrom, workTo));
        model.addAttribute("statuses", List.of(TaskStatus.values()));
        return "reports/project-tasks";
    }
}
