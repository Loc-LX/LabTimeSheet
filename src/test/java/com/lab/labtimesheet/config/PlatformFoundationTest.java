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

        assertThat(tables).isEqualTo(23);
        assertThat(foreignKeys).isEqualTo(56);
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
