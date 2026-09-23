package com.lab.labtimesheet.feature.project.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.project.model.dto.ProjectCreateCommand;
import com.lab.labtimesheet.feature.project.model.dto.ProjectListPage;
import com.lab.labtimesheet.feature.project.service.ProjectQueryService;
import com.lab.labtimesheet.feature.project.service.ProjectService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/** PostgreSQL catalog proof for the Project list, lifecycle queue, and Task-progress paths. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class ProjectQueryIndexIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-22T00:00:00Z");

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transactions;

    @Autowired
    private ProjectQueryService projectPages;

    @Autowired
    private ProjectService projectMutations;

    @Test
    void projectAndProgressPathsRetainTheirFilterIndexes() {
        Set<String> indexes = Set.copyOf(jdbc.queryForList("""
                select indexname
                from pg_indexes
                where schemaname = 'public'
                  and tablename in (
                      'projects', 'project_memberships', 'project_leadership_terms',
                      'project_invitations', 'project_membership_exit_requests', 'tasks')
                """, String.class));

        assertThat(indexes).contains(
                "ix_projects_mentor_status",
                "ix_projects_status_dates",
                "ix_project_memberships_project_active",
                "ix_project_memberships_intern_active",
                "ix_project_leadership_terms_membership",
                "ix_project_invitations_project_status",
                "ix_project_invitations_invitee_status",
                "ix_project_membership_exit_requests_project_status",
                "ix_project_membership_exit_requests_target_project",
                "ix_tasks_project_status_active",
                "ix_tasks_assignee_status_active");

        String projectProgressPlan = transactions.execute(status -> {
            jdbc.execute("set local enable_seqscan = off");
            return jdbc.queryForList("""
                    explain (costs off)
                    select count(*)
                    from tasks task
                    where task.project_id = 1
                      and task.deleted_at is null
                    """, String.class).stream().reduce("", (left, right) -> left + "\n" + right);
        });
        assertThat(projectProgressPlan)
                .contains("Index Scan")
                .containsAnyOf("ix_tasks_project_status_active", "ix_tasks_assignee_status_active");

        String transferPlan = transactions.execute(status -> {
            jdbc.execute("set local enable_seqscan = off");
            return jdbc.queryForList("""
                    explain (costs off)
                    select task.id
                    from tasks task
                    where task.project_id = 1
                      and task.assignee_membership_id = 1
                      and task.status in ('TODO', 'IN_PROGRESS', 'BLOCKED')
                      and task.deleted_at is null
                    order by task.id
                    """, String.class).stream().reduce("", (left, right) -> left + "\n" + right);
        });
        assertThat(transferPlan).contains("ix_tasks_assignee_status_active");
    }

    @Test
    void roleScopedProjectListsUseBoundedPagesAndEligibleDashboardTotals() {
        long mentorId = user("project-list-plan-mentor@example.test", "MENTOR");
        long adminId = user("project-list-plan-admin@example.test", "ADMIN");
        long leaderId = intern("project-list-plan-intern@example.test", "LIST-001");
        long completedMemberId = intern("completed-dashboard-eligibility@example.test", "COMPLETED-001");
        for (int index = 0; index < 51; index++) {
            long projectId = projectMutations.create(
                    mentorId,
                    new ProjectCreateCommand(
                            "Bounded Project " + index,
                            "Representative list-plan fixture",
                            LocalDate.of(2026, 8, 15),
                            LocalDate.of(2026, 12, 31),
                            leaderId));
            projectMutations.addMember(mentorId, projectId, completedMemberId);
        }
        jdbc.update("""
                update projects
                set status = 'ACTIVE', activated_at = ?, updated_at = ?
                where mentor_user_id = ?
                """, NOW.atOffset(java.time.ZoneOffset.UTC), NOW.atOffset(java.time.ZoneOffset.UTC), mentorId);
        jdbc.execute("analyze projects");
        jdbc.execute("analyze project_memberships");
        projectMutations.completeInternship(adminId, completedMemberId);

        assertThat(jdbc.queryForObject("""
                select count(*)
                from project_memberships membership
                join projects project on project.id = membership.project_id
                where project.mentor_user_id = ?
                  and membership.intern_user_id = ?
                  and membership.left_at is null
                """, Integer.class, mentorId, completedMemberId)).isEqualTo(51);
        assertThat(jdbc.queryForObject(
                "select internship_status from intern_profiles where user_id = ?",
                String.class,
                completedMemberId)).isEqualTo("COMPLETED");

        ProjectListPage adminPageTwo = projectPages.listPage(adminId, PageRequest.of(1, 50));
        ProjectListPage mentorPageTwo = projectPages.listPage(mentorId, PageRequest.of(1, 50));
        ProjectListPage internPageTwo = projectPages.listPage(leaderId, PageRequest.of(1, 50));
        assertThat(projectPages.listPage(adminId, PageRequest.of(0, 50)).projects()).hasSize(50);
        assertThat(projectPages.listPage(mentorId, PageRequest.of(0, 50)).projects()).hasSize(50);
        assertThat(projectPages.listPage(leaderId, PageRequest.of(0, 50)).projects()).hasSize(50);
        assertThat(adminPageTwo.projects()).hasSize(1);
        assertThat(mentorPageTwo.projects()).hasSize(1);
        assertThat(internPageTwo.projects()).hasSize(1);
        assertThat(adminPageTwo.pageNumber()).isEqualTo(2);
        assertThat(adminPageTwo.hasPrevious()).isTrue();
        assertThat(adminPageTwo.hasNext()).isFalse();
        assertThat(mentorPageTwo.projects().getFirst().id())
                .isEqualTo(adminPageTwo.projects().getFirst().id());
        assertThat(internPageTwo.projects().getFirst().id())
                .isEqualTo(adminPageTwo.projects().getFirst().id());

        assertThat(projectPages.dashboardSummary(adminId).activeProjectCount()).isEqualTo(51);
        assertThat(projectPages.dashboardSummary(mentorId).activeProjectCount()).isEqualTo(51);
        assertThat(projectPages.dashboardSummary(mentorId).distinctActiveMemberCount()).isEqualTo(1);
        assertThat(projectPages.dashboardSummary(leaderId).activeProjectCount()).isEqualTo(51);

        String adminPlan = explain("""
                select project.*
                from projects project
                order by project.updated_at desc, project.id desc
                limit 50
                """);
        String mentorPlan = explain("""
                select project.*
                from projects project
                where project.mentor_user_id = %d
                order by project.updated_at desc, project.id desc
                limit 50
                """.formatted(mentorId));
        String internPlan = explain("""
                select distinct project.*
                from projects project
                join project_memberships membership on membership.project_id = project.id
                where membership.intern_user_id = %d
                  and (membership.left_at is null or project.status = 'COMPLETED')
                order by project.updated_at desc, project.id desc
                limit 50
                """.formatted(leaderId));

        assertThat(adminPlan).contains("Limit");
        assertThat(mentorPlan).contains("Limit").contains("ix_projects_mentor_status");
        assertThat(internPlan).contains("Limit").contains("Index Scan");
    }

    private String explain(String sql) {
        return transactions.execute(status -> {
            jdbc.execute("set local enable_seqscan = off");
            return jdbc.queryForList("explain (costs off) " + sql, String.class).stream()
                    .reduce("", (left, right) -> left + "\n" + right);
        });
    }

    private long user(String email, String role) {
        return jdbc.queryForObject("""
                insert into app_users (
                    email, display_name, password_hash, global_role, account_status, activated_at)
                values (?, ?, '{noop}password-password', ?, 'ACTIVE', ?)
                returning id
                """, Long.class, email, email, role, NOW.atOffset(java.time.ZoneOffset.UTC));
    }

    private long intern(String email, String studentCode) {
        long userId = user(email, "INTERN");
        jdbc.update("""
                insert into intern_profiles (
                    user_id, student_code, internship_start_date, internship_end_date,
                    internship_status, activated_at)
                values (?, ?, date '2026-08-01', date '2026-12-31', 'ACTIVE', ?)
                """, userId, studentCode, NOW.atOffset(java.time.ZoneOffset.UTC));
        return userId;
    }
}
