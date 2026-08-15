package com.lab.labtimesheet.feature.task.service;

import com.lab.labtimesheet.feature.project.model.dto.ProjectSummary;
import com.lab.labtimesheet.feature.project.model.dto.ProjectTaskMemberView;
import com.lab.labtimesheet.feature.project.service.ProjectQueryService;
import com.lab.labtimesheet.feature.task.model.TaskStatus;
import com.lab.labtimesheet.feature.task.model.dto.TaskDashboardView;
import com.lab.labtimesheet.feature.task.model.dto.TaskPriorityView;
import com.lab.labtimesheet.feature.task.model.entity.Task;
import com.lab.labtimesheet.feature.task.repository.TaskRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Supplies the Task-owned portion of the shared role dashboard.
 *
 * <p>Project visibility, role, and active membership facts come only from the Project service DTO
 * boundary. Mentor counts cover blocked current Tasks in visible active Projects. Intern counts and
 * priorities cover current assignments only where the actor still has an active membership.
 */
@Service
public class TaskDashboardService {

    private static final TaskDashboardView EMPTY_DASHBOARD = new TaskDashboardView(0, 0, List.of());

    private final TaskRepository tasks;
    private final ProjectQueryService projects;

    /**
     * Creates the dashboard query service.
     *
     * @param tasks Task persistence boundary
     * @param projects authorized Project query boundary
     */
    public TaskDashboardService(TaskRepository tasks, ProjectQueryService projects) {
        this.tasks = tasks;
        this.projects = projects;
    }

    /**
     * Builds the role-scoped Task dashboard for one authenticated account.
     *
     * <p>Mentors receive the blocked count for active Projects they can see. Interns receive their
     * non-deleted assignment count and at most five priority Tasks, ordered by due date ascending,
     * null due dates last, and Task identifier ascending. Other roles receive zero/empty values.
     *
     * @param actorEmail authenticated account email
     * @return immutable role-appropriate Task dashboard data
     */
    @Transactional(readOnly = true)
    public TaskDashboardView dashboard(String actorEmail) {
        var actor = projects.authenticatedActor(actorEmail);
        List<ProjectSummary> activeProjects = projects.listVisible(actor.userId()).stream()
                .filter(project -> "ACTIVE".equals(project.status()))
                .toList();
        if ("MENTOR".equals(actor.role())) {
            return mentorDashboard(activeProjects);
        }
        if ("INTERN".equals(actor.role())) {
            return internDashboard(actor.userId(), activeProjects);
        }
        return EMPTY_DASHBOARD;
    }

    private TaskDashboardView mentorDashboard(List<ProjectSummary> activeProjects) {
        List<Long> projectIds = activeProjects.stream().map(ProjectSummary::id).toList();
        long blocked = projectIds.isEmpty()
                ? 0
                : tasks.countByProjectIdInAndStatusAndDeletedAtIsNull(projectIds, TaskStatus.BLOCKED);
        return new TaskDashboardView(blocked, 0, List.of());
    }

    private TaskDashboardView internDashboard(long actorUserId, List<ProjectSummary> activeProjects) {
        Map<Long, ProjectSummary> currentProjects = new LinkedHashMap<>();
        Map<Long, Long> currentMemberships = new LinkedHashMap<>();
        for (ProjectSummary project : activeProjects) {
            projects.taskContext(actorUserId, project.id()).activeMembers().stream()
                    .filter(member -> member.userId() == actorUserId)
                    .map(ProjectTaskMemberView::membershipId)
                    .findFirst()
                    .ifPresent(membershipId -> {
                        currentProjects.put(project.id(), project);
                        currentMemberships.put(project.id(), membershipId);
                    });
        }
        List<Long> projectIds = List.copyOf(currentProjects.keySet());
        List<Long> membershipIds = List.copyOf(currentMemberships.values());
        if (projectIds.isEmpty()) {
            return EMPTY_DASHBOARD;
        }

        long assigned = tasks.countByProjectIdInAndAssigneeMembershipIdInAndDeletedAtIsNull(
                projectIds, membershipIds);
        List<TaskPriorityView> priority = tasks.findPriorityTasks(
                        projectIds, membershipIds, PageRequest.of(0, 5))
                .stream()
                .map(task -> priorityView(task, currentProjects.get(task.getProjectId()).name()))
                .toList();
        return new TaskDashboardView(0, assigned, priority);
    }

    private static TaskPriorityView priorityView(Task task, String projectName) {
        return new TaskPriorityView(task.getTitle(), projectName, task.getStatus(), task.getDueDate());
    }
}
