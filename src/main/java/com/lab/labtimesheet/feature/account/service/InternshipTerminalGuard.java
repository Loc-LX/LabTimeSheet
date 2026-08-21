package com.lab.labtimesheet.feature.account.service;

import com.lab.labtimesheet.feature.project.service.ProjectInternshipGuardService;
import com.lab.labtimesheet.feature.task.service.TaskQueryService;
import java.util.Objects;

/**
 * Applies the account-owned terminal-action guard using Project and Task service contracts only.
 */
final class InternshipTerminalGuard {

    private final ProjectInternshipGuardService projects;
    private final TaskQueryService tasks;

    InternshipTerminalGuard(ProjectInternshipGuardService projects, TaskQueryService tasks) {
        this.projects = Objects.requireNonNull(projects, "projects");
        this.tasks = Objects.requireNonNull(tasks, "tasks");
    }

    /**
     * Rejects completion or withdrawal while the Intern still owns open Project work.
     *
     * @param internUserId Intern account identifier
     */
    void assertAllowed(long internUserId) {
        var projectGuard = projects.guardForInternship(internUserId);
        if (projectGuard.currentLeader()) {
            throw new IllegalStateException(
                    "Internship completion or withdrawal is blocked while the Intern leads a Project");
        }
        if (tasks.countUnfinishedTasksForMemberships(projectGuard.membershipIds()) > 0) {
            throw new IllegalStateException(
                    "Internship completion or withdrawal is blocked while unfinished Tasks remain");
        }
    }
}
