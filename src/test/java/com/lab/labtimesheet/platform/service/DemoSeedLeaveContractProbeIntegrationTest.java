package com.lab.labtimesheet.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.stream.Stream;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Runs both disposable demo-seed paths against a PostgreSQL database migrated through V7.
 *
 * <p>Protects C.5, DB-018, D47, and the seeded-instance clause of ACC-021: the main and V2 add-on
 * must execute after migration 7, every active Intern must carry the Mentor of their earliest
 * current Project membership (or Mentor 1 without one), and all stored Leave rows must satisfy the
 * contract now enforced by PostgreSQL.
 */
class DemoSeedLeaveContractProbeIntegrationTest {

    private static final Map<String, String> EXPECTED_MENTORS = Map.of(
            "STU-1001", "mentor1@example.com",
            "STU-1002", "mentor2@example.com",
            "STU-1003", "mentor1@example.com",
            "STU-1004", "mentor2@example.com",
            "STU-1006", "mentor2@example.com",
            "STU-1008", "mentor1@example.com");

    /**
     * Runs the primary seed and its V2 add-on independently on isolated PostgreSQL 18.4 databases.
     *
     * @return one probe for each supported seed path
     */
    @TestFactory
    Stream<DynamicTest> bothDemoSeedPathsSatisfyTheMigratedContracts() {
        return Stream.of(
                dynamicTest("C.5/DB-018/D47: primary demo seed runs on V7", () -> probe(false)),
                dynamicTest("C.5/DB-018/D47: V2 demo seed add-on runs on V7", () -> probe(true)));
    }

    private static void probe(boolean includeV2Seed) throws Exception {
        TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        try (PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18.4"))) {
            postgres.start();
            Flyway.configure()
                    .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                    .locations("classpath:db/migration")
                    .load()
                    .migrate();
            try (Connection connection = connect(postgres); Statement statement = connection.createStatement()) {
                statement.execute(Files.readString(Path.of("scripts", "demo-seed.sql")));
                if (includeV2Seed) {
                    statement.execute(Files.readString(Path.of("scripts", "demo-seed-v2.sql")));
                }
                assertThat(scalar(statement,
                        "SELECT count(*) FROM flyway_schema_history WHERE version = '7' AND success"))
                        .as("the seed ran on a schema that includes the Leave contract V7")
                        .isEqualTo("1");
                assertThat(scalar(statement, """
                        SELECT count(*)
                        FROM intern_profiles
                        WHERE internship_status = 'ACTIVE' AND responsible_mentor_user_id IS NULL
                        """))
                        .as("every seeded ACTIVE Intern has a responsible Mentor")
                        .isEqualTo("0");
                assertThat(activeInternMentors(statement))
                        .as("D47 assigns the earliest current Project owner's Mentor or Mentor 1")
                        .containsExactlyInAnyOrderEntriesOf(EXPECTED_MENTORS);
                assertThat(scalar(statement, """
                        SELECT count(*)
                        FROM leave_requests
                        WHERE (status IN ('PENDING', 'OVERDUE', 'WITHDRAWN')
                               AND (decided_at IS NOT NULL OR decided_by_mentor_user_id IS NOT NULL))
                           OR (status = 'WITHDRAWN' AND withdrawn_at IS NULL)
                           OR (status = 'CANCELLED'
                               AND (decided_at IS NULL OR decided_by_mentor_user_id IS NULL))
                        """))
                        .as("all seeded Leave rows obey DB-018 undecided, withdrawn, and cancelled shapes")
                        .isEqualTo("0");
            }
        } finally {
            TimeZone.setDefault(original);
        }
    }

    private static Connection connect(PostgreSQLContainer postgres) throws Exception {
        return DriverManager.getConnection(
                postgres.getJdbcUrl() + "&preferQueryMode=simple", postgres.getUsername(), postgres.getPassword());
    }

    private static String scalar(Statement statement, String sql) throws Exception {
        try (ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getString(1);
        }
    }

    private static Map<String, String> activeInternMentors(Statement statement) throws Exception {
        Map<String, String> assignments = new LinkedHashMap<>();
        try (ResultSet rows = statement.executeQuery("""
                SELECT intern.student_code, mentor.email
                FROM intern_profiles intern
                JOIN app_users mentor ON mentor.id = intern.responsible_mentor_user_id
                WHERE intern.internship_status = 'ACTIVE'
                ORDER BY intern.student_code
                """)) {
            while (rows.next()) {
                assignments.put(rows.getString(1), rows.getString(2));
            }
        }
        return assignments;
    }
}
