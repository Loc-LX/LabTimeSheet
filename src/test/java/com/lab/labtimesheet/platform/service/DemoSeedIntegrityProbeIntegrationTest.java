package com.lab.labtimesheet.platform.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.TimeZone;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** PostgreSQL integration probe for current Leaders in the demo seed. */
class DemoSeedIntegrityProbeIntegrationTest {

    /**
     * Protects {@code PRJ-001} and the initial-Leader invariant in {@code AC-PRJ-014}, and keeps
     * the seeded Leaders usable on the demo dates. Observable break: a seeded Project detail page
     * returns 409 because it has no current Leader, or a seeded Leader's internship no longer
     * covers 2 or 3 October 2026, so the Leader can no longer act or check in on the demo day.
     * Expected value: 0 PLANNED or ACTIVE Projects without exactly one current Leader, and 0 such
     * Leaders outside an ACTIVE internship on either date. The probe migrates and seeds its own
     * PostgreSQL container, because both seeds commit and would otherwise replace the rows of the
     * database the Spring test context shares.
     *
     * @throws Exception when a seed script cannot be executed as a JDBC block
     */
    @Test
    void demoSeedsGiveEveryOpenProjectExactlyOneCurrentLeader() throws Exception {
        TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        try {
            try (PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18.4"))) {
                postgres.start();
                Flyway.configure()
                        .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                        .locations("classpath:db/migration")
                        .load()
                        .migrate();
                try (Connection connection = DriverManager.getConnection(
                                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
                        Statement statement = connection.createStatement()) {
                    executeSeed(statement, Path.of("scripts/demo-seed.sql"));
                    executeSeed(statement, Path.of("scripts/demo-seed-v2.sql"));
                    List<String> violations = leaderViolations(connection);
                    assertEquals(List.of(), violations,
                            "Projects without exactly one current Leader whose membership is current: " + violations);
                    for (LocalDate demoDate : List.of(LocalDate.of(2026, 10, 2), LocalDate.of(2026, 10, 3))) {
                        List<String> ineligibleLeaders = ineligibleLeaders(connection, demoDate);
                        assertEquals(List.of(), ineligibleLeaders,
                                "Current Leaders without an active internship covering " + demoDate + ": "
                                        + ineligibleLeaders);
                    }
                }
            }
        } finally {
            TimeZone.setDefault(original);
        }
    }

    private static void executeSeed(Statement statement, Path path) throws IOException, SQLException {
        String sql = Files.readString(path, StandardCharsets.UTF_8);
        statement.execute(sql);
    }

    private static List<String> leaderViolations(Connection connection) throws SQLException {
        String sql = """
                SELECT p.name
                FROM projects p
                LEFT JOIN project_leadership_terms plt
                    ON plt.project_id = p.id AND plt.ended_at IS NULL
                LEFT JOIN project_memberships pm
                    ON pm.id = plt.membership_id AND pm.project_id = p.id AND pm.left_at IS NULL
                WHERE p.status IN ('PLANNED', 'ACTIVE')
                GROUP BY p.id, p.name
                HAVING count(plt.id) <> 1 OR count(pm.id) <> 1
                ORDER BY p.name
                """;
        List<String> violations = new ArrayList<>();
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            while (rows.next()) violations.add(rows.getString(1));
        }
        return violations;
    }

    private static List<String> ineligibleLeaders(Connection connection, LocalDate businessDate) throws SQLException {
        String sql = """
                SELECT p.name || ' / ' || ip.student_code
                FROM projects p
                JOIN project_leadership_terms plt ON plt.project_id = p.id AND plt.ended_at IS NULL
                JOIN project_memberships pm
                    ON pm.id = plt.membership_id AND pm.project_id = p.id AND pm.left_at IS NULL
                JOIN intern_profiles ip ON ip.user_id = pm.intern_user_id
                WHERE p.status IN ('PLANNED', 'ACTIVE')
                  AND (ip.internship_status <> 'ACTIVE'
                       OR ip.internship_start_date > ?
                       OR ip.internship_end_date < ?)
                ORDER BY p.name
                """;
        List<String> ineligible = new ArrayList<>();
        try (var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, businessDate);
            statement.setObject(2, businessDate);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) ineligible.add(rows.getString(1));
            }
        }
        return ineligible;
    }
}
