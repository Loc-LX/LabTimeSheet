package com.lab.labtimesheet.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class PlatformFoundationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private Clock clock;

    @Test
    void flywayCreatesApprovedPostgresCatalog() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        Integer tables = jdbc.queryForObject("""
                select count(*)
                from information_schema.tables
                where table_schema = 'public'
                  and table_type = 'BASE TABLE'
                  and table_name <> 'flyway_schema_history'
                """, Integer.class);
        Integer foreignKeys = jdbc.queryForObject("""
                select count(*)
                from pg_constraint c
                join pg_namespace n on n.oid = c.connamespace
                where n.nspname = 'public' and c.contype = 'f'
                """, Integer.class);

        assertThat(tables).isEqualTo(24);
        assertThat(foreignKeys).isEqualTo(61);
        assertThat(jdbc.queryForObject("""
                select count(*) from information_schema.columns
                where table_schema = 'public' and table_name = 'tasks'
                  and column_name = 'estimated_minutes'
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select data_type from information_schema.columns
                where table_schema = 'public' and table_name = 'tasks'
                  and column_name = 'estimated_minutes'
                """, String.class)).isEqualTo("integer");
        assertThat(jdbc.queryForObject("""
                select is_nullable from information_schema.columns
                where table_schema = 'public' and table_name = 'tasks'
                  and column_name = 'estimated_minutes'
                """, String.class)).isEqualTo("YES");
        assertThat(jdbc.queryForObject("""
                select column_default from information_schema.columns
                where table_schema = 'public' and table_name = 'tasks'
                  and column_name = 'estimated_minutes'
                """, String.class)).isNull();
        assertThat(jdbc.queryForObject("""
                select count(*) from pg_constraint
                where conname in ('ck_tasks_estimated_minutes',
                    'ck_task_forecasts_initial_note',
                    'ck_task_forecasts_remaining_minutes', 'ck_task_forecasts_actual_snapshot',
                    'ck_task_forecasts_initial_or_correction', 'uq_task_forecasts_one_successor')
                """, Integer.class)).isEqualTo(6);
        assertThat(jdbc.queryForObject("""
                select count(*) from pg_constraint
                where conname in ('fk_task_forecasts_project', 'fk_task_forecasts_task_project',
                    'fk_task_forecasts_incoming_membership_project',
                    'fk_task_forecasts_leader_membership_project', 'fk_task_forecasts_superseded')
                """, Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("""
                select count(*) from pg_indexes
                where schemaname = 'public' and tablename = 'task_remaining_effort_forecasts'
                  and indexname in ('ix_task_forecasts_task_history', 'ix_task_forecasts_task_latest',
                      'ix_task_forecasts_incoming_assignment', 'ix_task_forecasts_project',
                      'ix_task_forecasts_leader_membership')
                """, Integer.class)).isEqualTo(5);
        assertThat(jdbc.queryForObject("""
                select count(*) from pg_trigger
                where tgname = 'tr_task_forecasts_append_only' and not tgisinternal
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("""
                select count(*) from information_schema.tables
                where table_schema = 'public' and table_name = 'task_remaining_effort_forecasts'
                """, Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject(
                "select checkout_grace_minutes from attendance_policy_versions where effective_from = date '1970-01-01'",
                Integer.class)).isEqualTo(30);
        assertThat(jdbc.queryForObject("select count(*) from attendance_policy_workdays", Integer.class)).isEqualTo(5);
    }

    @Test
    void testClockIsDeterministic() {
        assertThat(clock.instant()).isEqualTo(Instant.parse("2026-08-14T00:00:00Z"));
        assertThat(clock.getZone()).isEqualTo(ZoneId.of("Asia/Ho_Chi_Minh"));
    }
}
