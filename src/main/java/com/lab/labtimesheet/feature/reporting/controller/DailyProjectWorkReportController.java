package com.lab.labtimesheet.feature.reporting.controller;

import com.lab.labtimesheet.feature.attendance.service.AttendanceApplicationService;
import com.lab.labtimesheet.feature.attendance.service.CalendarApplicationService;
import com.lab.labtimesheet.feature.project.exception.ProjectAccessDeniedException;
import com.lab.labtimesheet.feature.project.model.dto.ProjectActorView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectSummary;
import com.lab.labtimesheet.feature.project.service.ProjectQueryService;
import com.lab.labtimesheet.feature.reporting.model.dto.DailyProjectWorkReportSelection;
import com.lab.labtimesheet.feature.reporting.service.DailyProjectWorkReportService;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
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
    private final AttendanceApplicationService attendance;
    private final CalendarApplicationService calendar;

    /**
     * Renders the current-business-date report by default, with an optional authorized Project
     * and past/current Report date. A current Intern Leader without a Project selection is
     * redirected to its only eligible Project or receives a selector when several are available.
     *
     * @param authentication authenticated owning Mentor or current Project Leader
     * @param projectIdParameter optional authorized Project filter, parsed after role authorization
     * @param reportDateParameter optional local Report date, parsed after role authorization
     * @param dateAliasParameter compatibility alias for links that use {@code date}
     * @param model Thymeleaf model
     * @return Daily report template
     */
    @GetMapping
    public String report(
            Authentication authentication,
            @RequestParam(name = "projectId", required = false) String projectIdParameter,
            @RequestParam(name = "reportDate", required = false) String reportDateParameter,
            @RequestParam(name = "date", required = false) String dateAliasParameter,
            Model model) {
        OperationalReportAuthorization.requireDailyReportAccess(authentication);
        Long projectId = DailyProjectWorkReportRequest.parseProjectId(projectIdParameter);
        LocalDate requestedDate = DailyProjectWorkReportRequest.mergeDates(
                DailyProjectWorkReportRequest.parseDate(dateAliasParameter),
                DailyProjectWorkReportRequest.parseDate(reportDateParameter));
        if (projectId == null && hasRole(authentication, "ROLE_INTERN")) {
            ProjectActorView actor = currentLeaderActor(authentication);
            if (!projects.hasCurrentLeaderProjectForDailyReport(actor.userId())) {
                throw new ProjectAccessDeniedException();
            }
            validateRequestedDate(requestedDate);
            List<ProjectSummary> eligibleProjects =
                    projects.listCurrentLeaderProjectsForDailyReport(actor.userId());
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

    private ProjectActorView currentLeaderActor(Authentication authentication) {
        ProjectActorView actor = projects.authenticatedActor(authentication.getName());
        if (actor == null || !"INTERN".equals(actor.role())) {
            throw new ProjectAccessDeniedException();
        }
        return actor;
    }

    private void validateRequestedDate(LocalDate requestedDate) {
        if (requestedDate != null && requestedDate.isAfter(calendar.currentBusinessDate())) {
            throw new IllegalArgumentException("Report date must not be in the future");
        }
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
