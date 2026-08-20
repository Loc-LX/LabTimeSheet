package com.lab.labtimesheet.feature.reporting.controller;

import com.lab.labtimesheet.feature.account.model.GlobalRole;
import com.lab.labtimesheet.feature.account.model.dto.AccountIdentity;
import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.attendance.service.AttendanceApplicationService;
import com.lab.labtimesheet.feature.reporting.model.dto.ProjectTaskReportData;
import com.lab.labtimesheet.feature.reporting.model.dto.ProjectTaskReportMember;
import com.lab.labtimesheet.feature.reporting.model.dto.ProjectTaskReportSummary;
import com.lab.labtimesheet.feature.reporting.service.ProjectTaskReportDataProvider;
import com.lab.labtimesheet.feature.reporting.service.ProjectTaskReportService;
import com.lab.labtimesheet.feature.reporting.service.ReportChartJson;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

/**
 * Server-rendered Project/Task report page.
 *
 * <p>The Project and Task features supply the authorized dataset through
 * {@link ProjectTaskReportDataProvider}; this controller only applies shared formulas and renders
 * them, keeping every output format from drifting (RPT-001, RPT-009). Until the features register a
 * provider, the page reports {@code 404 Not Found} rather than fabricating rows.
 */
@Controller
@RequestMapping("/reports/projects")
@RequiredArgsConstructor
public class ProjectTaskReportController {

    private final ObjectProvider<ProjectTaskReportDataProvider> reports;
    private final ProjectTaskReportService reportFormulas;
    private final AccountService accounts;
    private final AttendanceApplicationService attendance;

    /**
     * Renders the aggregate and authorized per-member Project/Task report for the period.
     *
     * @param principal authenticated viewer
     * @param projectId target Project identifier
     * @param from optional inclusive first work date
     * @param to optional inclusive last work date
     * @param model Thymeleaf model
     * @return Project/Task report view name
     */
    @GetMapping
    public String report(
            Principal principal,
            @RequestParam long projectId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Model model) {
        AccountIdentity viewer = accounts.requireIdentityByEmail(principal.getName());
        boolean admin = viewer.role() == GlobalRole.ADMIN;
        LocalDate effectiveTo = to == null ? attendance.currentBusinessDate() : to;
        LocalDate effectiveFrom = from == null ? effectiveTo.withDayOfMonth(1) : from;
        ProjectTaskReportDataProvider provider = reports.getIfAvailable();
        if (provider == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "The Project report is not available until the Project and Task feeds are integrated");
        }
        ProjectTaskReportData data = provider.data(viewer.id(), admin, projectId, effectiveFrom, effectiveTo);
        ProjectTaskReportSummary summary = reportFormulas.summarize(data.tasks());
        boolean allowDetail =
                reportFormulas.allowPerMemberDetail(admin, data.owningMentor(), data.currentLeader());

        model.addAttribute("projectId", projectId);
        model.addAttribute("projectName", data.projectName());
        model.addAttribute("summary", summary);
        model.addAttribute("allowDetail", allowDetail);
        model.addAttribute("members", allowDetail ? data.members() : List.of());
        model.addAttribute("from", effectiveFrom);
        model.addAttribute("to", effectiveTo);
        model.addAttribute("chartJson",
                allowDetail && !data.members().isEmpty() ? memberHoursChartJson(data.members()) : null);
        return "reports/projects";
    }

    private String memberHoursChartJson(List<ProjectTaskReportMember> members) {
        return ReportChartJson.bar(
                "Member hours",
                members.stream().map(ProjectTaskReportMember::displayName).toList(),
                members.stream().map(ProjectTaskReportMember::loggedHours).toList());
    }
}