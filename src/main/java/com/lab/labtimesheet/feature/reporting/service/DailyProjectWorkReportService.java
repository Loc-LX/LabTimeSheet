package com.lab.labtimesheet.feature.reporting.service;

import com.lab.labtimesheet.feature.attendance.model.dto.AttendanceReportDateContext;
import com.lab.labtimesheet.feature.attendance.service.AttendanceApplicationService;
import com.lab.labtimesheet.feature.attendance.service.CalendarApplicationService;
import com.lab.labtimesheet.feature.project.exception.ProjectAccessDeniedException;
import com.lab.labtimesheet.feature.project.model.dto.ProjectActorView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectMemberView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectSummary;
import com.lab.labtimesheet.feature.project.service.ProjectQueryService;
import com.lab.labtimesheet.feature.reporting.model.dto.DailyProjectWorkReportLog;
import com.lab.labtimesheet.feature.reporting.model.dto.DailyProjectWorkReportMember;
import com.lab.labtimesheet.feature.reporting.model.dto.DailyProjectWorkReportProject;
import com.lab.labtimesheet.feature.reporting.model.dto.DailyProjectWorkReportTask;
import com.lab.labtimesheet.feature.reporting.model.dto.DailyProjectWorkReportView;
import com.lab.labtimesheet.feature.task.model.dto.TaskDailyReportView;
import com.lab.labtimesheet.feature.task.model.dto.TaskWorkLogView;
import com.lab.labtimesheet.feature.task.service.TaskQueryService;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Composes the authorized Project, Task, and Attendance public query boundaries into the Daily
 * Project Work Report dataset.
 *
 * <p>Authorization is reduced before any Task dataset is loaded. Attendance contributes only
 * selected-date context; retained Task work is never filtered by policy or global day-off state.
 * The resulting tree is suitable for HTML and future export renderers without importing a foreign
 * repository or JPA entity.</p>
 */
@Service
@RequiredArgsConstructor
public class DailyProjectWorkReportService {

    private final ProjectQueryService projects;
    private final TaskQueryService taskQueries;
    private final AttendanceApplicationService attendance;
    private final CalendarApplicationService calendar;

    /**
     * Builds an authorized one-date report for an owning Mentor or the current Leader of one
     * selected open Project. Admins and ordinary/former Interns are denied at this boundary.
     *
     * @param actorEmail authenticated account email
     * @param projectId optional owned-Project filter for Mentors; mandatory current-Leader Project
     *        identifier for Interns
     * @param requestedDate selected local Report date, or null for the current business date
     * @return immutable grouped report dataset
     * @throws ProjectAccessDeniedException when the actor is not an owning Mentor/current Leader
     *         or the Project filter is outside their authorized Project scope
     * @throws IllegalArgumentException when the requested date is in the future
     */
    @Transactional(readOnly = true)
    public DailyProjectWorkReportView build(
            String actorEmail, Long projectId, LocalDate requestedDate) {
        ProjectActorView actor = projects.authenticatedActor(actorEmail);
        List<ProjectSummary> projectOptions;
        ProjectSummary selected;
        boolean lockedSingleProject;
        if ("MENTOR".equals(actor.role())) {
            projectOptions = projects.listAllVisibleForReport(actor.userId());
            selected = projectId == null
                    ? null
                    : projectOptions.stream()
                            .filter(project -> project.id() == projectId)
                            .findFirst()
                            .orElseThrow(ProjectAccessDeniedException::new);
            lockedSingleProject = false;
        } else if ("INTERN".equals(actor.role())) {
            if (projectId == null) {
                throw new ProjectAccessDeniedException();
            }
            selected = projects.currentLeaderProjectForDailyReport(actor.userId(), projectId);
            projectOptions = List.of(selected);
            lockedSingleProject = true;
        } else {
            throw new ProjectAccessDeniedException();
        }

        LocalDate today = calendar.currentBusinessDate();
        LocalDate reportDate = requestedDate == null ? today : requestedDate;
        if (reportDate.isAfter(today)) {
            throw new IllegalArgumentException("Report date must not be in the future");
        }
        AttendanceReportDateContext dayContext = attendance.reportDateContext(reportDate);

        List<ProjectSummary> scopedProjects = selected == null ? projectOptions : List.of(selected);
        List<DailyProjectWorkReportProject> reportProjects = scopedProjects.stream()
                .map(project -> project(actor.userId(), project, reportDate))
                .filter(project -> selected != null || project.totalMinutes() > 0)
                .toList();
        return new DailyProjectWorkReportView(
                reportDate,
                dayContext,
                projectId,
                selected == null ? null : selected.name(),
                projectOptions,
                reportProjects,
                reportProjects.stream().mapToLong(DailyProjectWorkReportProject::totalMinutes).sum(),
                lockedSingleProject);
    }

    private DailyProjectWorkReportProject project(
            long actorUserId, ProjectSummary project, LocalDate reportDate) {
        Map<Long, Map<Long, AuthorTask>> logsByAuthorAndTask = new LinkedHashMap<>();
        for (TaskDailyReportView task : taskQueries.dailyReport(project.id(), reportDate)) {
            List<TaskWorkLogView> selectedLogs = task.workLogs().stream()
                    .filter(log -> reportDate.equals(log.workDate()))
                    .toList();
            selectedLogs.forEach(log -> logsByAuthorAndTask
                    .computeIfAbsent(log.membershipId(), ignored -> new LinkedHashMap<>())
                    .computeIfAbsent(task.id(), ignored -> new AuthorTask(task))
                    .logs()
                    .add(log));
        }
        if (logsByAuthorAndTask.isEmpty()) {
            return new DailyProjectWorkReportProject(project.id(), project.name(), List.of(), 0L);
        }

        Map<Long, ProjectMemberView> membersById = projects.members(actorUserId, project.id()).stream()
                .collect(Collectors.toMap(
                        ProjectMemberView::membershipId,
                        member -> member,
                        (first, ignored) -> first,
                        LinkedHashMap::new));

        List<DailyProjectWorkReportMember> reportMembers = logsByAuthorAndTask.entrySet().stream()
                .map(entry -> {
                    ProjectMemberView member = membersById.get(entry.getKey());
                    String displayName = member == null ? "Membership " + entry.getKey() : member.displayName();
                    List<DailyProjectWorkReportTask> authorTasks = entry.getValue().values().stream()
                            .map(authorTask -> task(authorTask.task(), authorTask.logs(), reportDate))
                            .toList();
                    return new DailyProjectWorkReportMember(
                            entry.getKey(),
                            displayName,
                            authorTasks,
                            authorTasks.stream()
                                    .mapToLong(DailyProjectWorkReportTask::selectedDateMinutes)
                                    .sum());
                })
                .toList();
        long totalMinutes = reportMembers.stream()
                .mapToLong(DailyProjectWorkReportMember::totalMinutes)
                .sum();
        return new DailyProjectWorkReportProject(project.id(), project.name(), reportMembers, totalMinutes);
    }

    private static DailyProjectWorkReportTask task(
            TaskDailyReportView task, List<TaskWorkLogView> authorLogs, LocalDate reportDate) {
        List<DailyProjectWorkReportLog> logs = authorLogs.stream()
                .map(log -> new DailyProjectWorkReportLog(
                        log.id(), reportDate, Objects.requireNonNullElse(log.note(), "N/A"), log.minutes()))
                .toList();
        return new DailyProjectWorkReportTask(
                task.id(), task.title(), task.assigneeMembershipId(), task.status(),
                logs.stream().mapToLong(DailyProjectWorkReportLog::minutes).sum(),
                task.lifetimeActualMinutes(), task.estimatedMinutes(), task.varianceState(),
                task.varianceMinutes(), task.assignedAt(), task.createdAt(), task.deleted(),
                logs, task.latestForecast());
    }

    private record AuthorTask(TaskDailyReportView task, List<TaskWorkLogView> logs) {
        private AuthorTask(TaskDailyReportView task) {
            this(task, new java.util.ArrayList<>());
        }
    }
}
