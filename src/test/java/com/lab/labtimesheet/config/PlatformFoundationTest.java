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

    /**
     * Protects the schema-wide half of {@code DB-001}. Observable break: one migration introduces a
     * PostgreSQL enum for a state column, or stores an instant as `timestamp without time zone`.
     * Both look harmless in a single table and both are expensive later: an enum cannot gain a
     * value without a migration that locks the type, and a naive timestamp silently drops the offset
     * that {@code GOV-011} depends on for resolving business dates.
     *
     * <p>The existing catalog test asserts one column of one table. These assertions are stated
     * over the whole public schema, so a new table is covered the day it is created rather than the
     * day someone remembers to extend a list.
     *
     * <p>Each figure is derived from {@code DB-001} itself, which names the permitted types. The
     * rule's remaining clauses are checked elsewhere: {@code date} and {@code time} columns are not
     * asserted here because a wrong choice there is visible in the entity mapping and caught by the
     * behavioural tests, while an enum type and a zoneless instant are invisible to Java and
     * observable only in the catalog.
     *
     * <p>The surrogate-key assertion is written over columns named {@code id} rather than over
     * every primary key column, and the difference is deliberate. Three tables key on natural
     * values rather than on a generated one: {@code attendance_policy_workdays} on its policy
     * version and ISO weekday, {@code leave_request_days} on its request and date, and
     * {@code system_state} on a singleton guard column. A first attempt asserted over all primary
     * keys and failed on exactly those three. {@code DB-001} governs generated identity keys, and
     * a composite natural key is not one.
     */
    @Test
    void theSchemaUsesNoPostgresEnumsAndStoresEveryInstantWithItsZone() {
        JdbcTemplate jdbc = new JdbcTemplate(dataSource);

        assertThat(jdbc.queryForObject("""
                select count(*)
                from pg_type t
                join pg_namespace n on n.oid = t.typnamespace
                where n.nspname = 'public' and t.typtype = 'e'
                """, Integer.class))
                .as("DB-001 requires checked varchar states rather than PostgreSQL enums")
                .isZero();

        assertThat(jdbc.queryForList("""
                select table_name || '.' || column_name
                from information_schema.columns
                where table_schema = 'public'
                  and table_name <> 'flyway_schema_history'
                  and data_type = 'timestamp without time zone'
                order by 1
                """, String.class))
                .as("DB-001 requires timestamptz for instants, so no column may drop its zone")
                .isEmpty();

        assertThat(jdbc.queryForList("""
                select table_name || '.' || column_name || ' is ' || data_type
                       || ', identity ' || is_identity
                from information_schema.columns
                where table_schema = 'public'
                  and column_name = 'id'
                  and (data_type <> 'bigint' or is_identity <> 'YES')
                order by 1
                """, String.class))
                .as("DB-001 requires every surrogate key to be a generated BIGINT identity")
                .isEmpty();
    }
}
