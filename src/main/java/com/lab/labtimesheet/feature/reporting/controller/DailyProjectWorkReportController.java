package com.lab.labtimesheet.feature.reporting.controller;

import com.lab.labtimesheet.feature.reporting.service.DailyProjectWorkReportService;
import com.lab.labtimesheet.feature.project.exception.ProjectAccessDeniedException;
import com.lab.labtimesheet.feature.project.model.dto.ProjectActorView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectSummary;
import com.lab.labtimesheet.feature.reporting.model.dto.DailyProjectWorkReportNavigation;
import com.lab.labtimesheet.feature.reporting.model.dto.DailyProjectWorkReportSelection;
import com.lab.labtimesheet.feature.project.service.ProjectQueryService;
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

/** Renders the authorized Daily Project Work Report dataset as an active HTML page. */
@Controller
@RequestMapping("/reports/daily")
@RequiredArgsConstructor
public class DailyProjectWorkReportController {

    private final DailyProjectWorkReportService reports;
    private final ProjectQueryService projects;

    /**
     * Renders the current-business-date report by default, with an optional authorized Project
     * and past/current Report date. A current Intern Leader without a Project selection is
     * redirected to its only eligible Project or receives a selector when several are available.
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
        if (projectId == null && hasRole(authentication, "ROLE_INTERN")) {
            List<ProjectSummary> eligibleProjects = currentLeaderProjects(authentication, model);
            if (eligibleProjects.isEmpty()) {
                throw new ProjectAccessDeniedException();
            }
            if (eligibleProjects.size() == 1) {
                return redirectToSelectedProject(eligibleProjects.getFirst().id(), requestedDate);
            }
            model.addAttribute(
                    "projectSelection",
                    new DailyProjectWorkReportSelection(requestedDate, eligibleProjects));
            return "reports/daily";
        }
        model.addAttribute("report", reports.build(authentication.getName(), projectId, requestedDate));
        return "reports/daily";
    }

    private List<ProjectSummary> currentLeaderProjects(Authentication authentication, Model model) {
        Object navigation = model.getAttribute("dailyReportNavigation");
        if (navigation instanceof DailyProjectWorkReportNavigation leaderNavigation) {
            return leaderNavigation.projects();
        }
        ProjectActorView actor = projects.authenticatedActor(authentication.getName());
        if (!"INTERN".equals(actor.role())) {
            throw new ProjectAccessDeniedException();
        }
        return projects.listCurrentLeaderProjectsForDailyReport(actor.userId());
    }

    private static String redirectToSelectedProject(long projectId, LocalDate requestedDate) {
        String target = "redirect:/reports/daily?projectId=" + projectId;
        return requestedDate == null ? target : target + "&reportDate=" + requestedDate;
    }

    private static boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream()
                .anyMatch(authority -> role.equals(authority.getAuthority()));
    }
}
