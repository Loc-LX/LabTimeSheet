package com.lab.labtimesheet.feature.reporting.model.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Closed set of immutable, role-specific dashboard projections rendered by Reporting.
 *
 * <p>Each projection contains only data authorized and calculated by its owning feature service.
 */
public sealed interface DashboardView {

    /**
     * System-wide counts visible to an active Admin.
     *
     * @param activeAccounts active account count
     * @param pendingActivations accounts awaiting activation
     * @param activeInternships active internship count
     */
    record Admin(long activeAccounts, long pendingActivations,
                 long activeInternships) implements DashboardView {
    }

    /**
     * Owning-Mentor operational summary; empty authorized scopes are represented by zero counts.
     *
     * @param displayName persisted Mentor display name
     * @param activeProjects active owned Project count
     * @param activeMembers distinct eligible active members across owned Projects
     * @param blockedTasks blocked Tasks visible within active owned Projects
     */
    record Mentor(String displayName, long activeProjects, long activeMembers,
                  long blockedTasks) implements DashboardView {
    }

    /**
     * Eligible Intern summary for the Attendance policy's current business date.
     *
     * @param displayName persisted Intern display name
     * @param attendanceState current policy-local attendance state
     * @param activeProjects active Projects containing a current eligible membership
     * @param assignedTasks current assigned Task count
     * @param priorityTasks ordered Task-owned priority items; empty when none are assigned
     */
    record Intern(String displayName, AttendanceState attendanceState, long activeProjects,
                  long assignedTasks, List<AssignedTask> priorityTasks) implements DashboardView {
    }

    /**
     * Compact Task row shown on an Intern dashboard.
     *
     * @param title Task title
     * @param projectName owning Project name
     * @param status Task status label supplied by the Task feature
     * @param dueDate Task due date, or {@code null} when no due date is assigned
     */
    record AssignedTask(String title, String projectName, String status, LocalDate dueDate) {
    }

    /** Current attendance state exposed to the Intern dashboard. */
    enum AttendanceState {
        NOT_CHECKED_IN("Not checked in"),
        CHECKED_IN("Checked in"),
        CHECKED_OUT("Checked out");

        private final String label;

        AttendanceState(String label) {
            this.label = label;
        }

        /**
         * Returns the English presentation label for this state.
         *
         * @return non-empty user-facing state label
         */
        public String label() {
            return label;
        }
    }
}
