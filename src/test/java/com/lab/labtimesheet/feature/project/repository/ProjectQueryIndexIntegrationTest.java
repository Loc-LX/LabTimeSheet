package com.lab.labtimesheet.feature.project.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/** PostgreSQL catalog proof for the Project list, lifecycle queue, and Task-progress paths. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class ProjectQueryIndexIntegrationTest {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private TransactionTemplate transactions;

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
}
