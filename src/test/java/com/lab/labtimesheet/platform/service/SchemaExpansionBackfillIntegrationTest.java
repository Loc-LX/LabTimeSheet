package com.lab.labtimesheet.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.TimeZone;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Probes the V3 backfills by migrating a dedicated PostgreSQL database to V2, seeding rows, and
 * applying the later migrations.
 *
 * <p>Protects {@code D27} with {@code ATT-020} as {@code D45} applies it to V3, {@code DB-014},
 * and the Project link that {@code PRJ-002} needs. Expected periods derive from the database's
 * {@code current_date}: the current month is {@code OPEN}; a month three months earlier is past its
 * deadline and {@code FINALIZED} at the migration's server time, unless a {@code PENDING} leave
 * request overlapping it or a {@code PENDING} correction of one of its attendance records keeps it
 * {@code OPEN}. A route matching {@code ^/projects/([0-9]+)(/|\?|$)} whose Project exists receives
 * that Project; every other route receives none.
 */
@Testcontainers(disabledWithoutDocker = true)
class SchemaExpansionBackfillIntegrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:18.4"));

    /**
     * Migrates a fresh PostgreSQL database to V2, seeds the hand-derived cases, and asks Flyway to
     * apply later migrations. Each dynamic test resets its own schema so every RED is independent.
     * On V1+V2 the expected break is the absent {@code attendance_periods} relation or
     * {@code notifications.project_id}, never a successful assertion caused by {@code 42P01}.
     *
     * @return isolated period and notification backfill probes
     */
    @TestFactory
    Stream<DynamicTest> v3BackfillsApplyTheRepositoryMigrationAtItsServerTime() {
        List<DynamicTest> probes = new ArrayList<>();
        probes.add(dynamicTest("PRJ-002: existing Project routes receive their Project link",
                () -> withV2SeedAndV3(fixture -> {
                    try (Connection connection = connect();
                            PreparedStatement query = connection.prepareStatement("""
                                    SELECT action_url, project_id FROM notifications ORDER BY action_url
                                    """)) {
                        try (ResultSet rows = query.executeQuery()) {
                            while (rows.next()) {
                                String route = rows.getString(1);
                                Long projectId = nullableLong(rows, 2);
                                if (route.equals("/projects/" + fixture.projectId)
                                        || route.equals("/projects/" + fixture.projectId + "/tasks/1")
                                        || route.equals("/projects/" + fixture.projectId + "?tab=tasks")) {
                                    assertThat(projectId).as("matching existing Project route %s", route)
                                            .isEqualTo(fixture.projectId);
                                } else {
                                    assertThat(projectId).as("unmatched Project route %s", route).isNull();
                                }
                            }
                        }
                    }
                })));
        probes.add(periodProbe("ATT-019/ATT-020/D45: the current month is OPEN", PeriodCase.CURRENT));
        probes.add(periodProbe("ATT-019/ATT-020/D45: a past month without pending work is FINALIZED",
                PeriodCase.PAST_CLEAR));
        probes.add(periodProbe("ATT-019/ATT-020/D45: a pending leave keeps its past month OPEN",
                PeriodCase.PAST_PENDING_LEAVE));
        probes.add(periodProbe("ATT-019/ATT-020/D45: a pending correction keeps its past month OPEN",
                PeriodCase.PAST_PENDING_CORRECTION));
        return probes.stream();
    }

    private DynamicTest periodProbe(String name, PeriodCase expectedCase) {
        return dynamicTest(name, () -> withV2SeedAndV3(fixture -> {
            LocalDate month = switch (expectedCase) {
                case CURRENT -> fixture.currentMonth;
                case PAST_CLEAR, PAST_PENDING_LEAVE, PAST_PENDING_CORRECTION -> fixture.pastMonth;
            };
            String expectedStatus = expectedCase == PeriodCase.PAST_CLEAR ? "FINALIZED" : "OPEN";
            try (Connection connection = connect();
                    PreparedStatement query = connection.prepareStatement("""
                            SELECT status, finalized_at FROM attendance_periods
                            WHERE intern_user_id = ? AND period_month = ?
                            """)) {
                query.setLong(1, fixture.internId);
                query.setDate(2, Date.valueOf(month));
                try (ResultSet rows = query.executeQuery()) {
                    assertThat(rows.next()).as("D27 creates one period for the Intern's attendance month").isTrue();
                    assertThat(rows.getString("status")).as("ATT-020 status for %s", month)
                            .isEqualTo(expectedStatus);
                    if (expectedStatus.equals("FINALIZED")) {
                        assertThat(rows.getTimestamp("finalized_at").toLocalDateTime())
                                .as("migration server time is retained")
                                .isBetween(fixture.migrationStartedAt, fixture.migrationFinishedAt);
                    } else {
                        assertThat(rows.getTimestamp("finalized_at")).as("OPEN periods have no finalization time")
                                .isNull();
                    }
                    assertThat(rows.next()).as("DB-014 permits one period per Intern and month").isFalse();
                }
            }
        }, expectedCase == PeriodCase.PAST_PENDING_LEAVE,
                expectedCase == PeriodCase.PAST_PENDING_CORRECTION));
    }

    private static void withV2SeedAndV3(BackfillAssertion assertion) throws Exception {
        withV2SeedAndV3(assertion, true, true);
    }

    private static void withV2SeedAndV3(BackfillAssertion assertion, boolean pendingLeave,
                                        boolean pendingCorrection) throws Exception {
        TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        try {
            Flyway v2 = configuration().cleanDisabled(false).target("2").load();
            v2.clean();
            v2.migrate();
            Fixture fixture = seedV2Rows(pendingLeave, pendingCorrection);
            LocalDateTime migrationStartedAt = serverTime();
            configuration().load().migrate();
            LocalDateTime migrationFinishedAt = serverTime();
            assertion.verify(fixture.withMigrationWindow(migrationStartedAt, migrationFinishedAt));
        } finally {
            TimeZone.setDefault(original);
        }
    }

    private static FluentConfiguration configuration() {
        return Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration");
    }

    private static Fixture seedV2Rows(boolean pendingLeave, boolean pendingCorrection) throws SQLException {
        try (Connection connection = connect()) {
            connection.setAutoCommit(false);
            try {
                long adminId = user(connection, "ADMIN");
                long mentorId = user(connection, "MENTOR");
                long internId = user(connection, "INTERN");
                insert(connection, """
                        INSERT INTO intern_profiles (user_id, student_code, internship_start_date, internship_end_date)
                        VALUES (?, ?, CURRENT_DATE - 100, CURRENT_DATE + 300)
                        """, internId, "student-" + UUID.randomUUID());
                long policyId = scalarLong(connection,
                        "SELECT id FROM attendance_policy_versions WHERE effective_from=DATE '1970-01-01'");
                long projectId = generatedId(connection, """
                        INSERT INTO projects (mentor_user_id, name, start_date, end_date)
                        VALUES (?, ?, CURRENT_DATE - 10, CURRENT_DATE + 365) RETURNING id
                        """, mentorId, "project-" + UUID.randomUUID());
                LocalDate currentMonth = scalarDate(connection, "SELECT date_trunc('month', current_date)::date");
                LocalDate pastMonth = currentMonth.minusMonths(3);
                insertAttendance(connection, internId, policyId, currentMonth.plusDays(1));
                LocalDate pastClear = pastMonth.plusDays(1);
                LocalDate pastLeave = pastMonth.plusDays(2);
                LocalDate pastCorrection = pastMonth.plusDays(3);
                long correctionAttendanceId = insertAttendance(connection, internId, policyId, pastCorrection);
                insertAttendance(connection, internId, policyId, pastClear);
                insertAttendance(connection, internId, policyId, pastLeave);

                if (pendingLeave) {
                    Timestamp submittedAt = Timestamp.from(Instant.now().minusSeconds(3600));
                    insert(connection, """
                            INSERT INTO leave_requests
                              (intern_user_id, start_date, end_date, reason, status, submitted_at, first_counted_start_at)
                            VALUES (?, ?, ?, 'Pending backfill probe', 'PENDING', ?, ?)
                            """, internId, Date.valueOf(pastMonth.plusDays(10)),
                            Date.valueOf(pastMonth.plusDays(11)), submittedAt,
                            Timestamp.from(submittedAt.toInstant().plusSeconds(1)));
                }
                if (pendingCorrection) {
                    Timestamp submittedAt = Timestamp.from(Instant.now().minusSeconds(3600));
                    insert(connection, """
                            INSERT INTO attendance_corrections
                              (attendance_record_id, requested_checkout_at, reason, submitted_at,
                               submission_deadline, decision_deadline)
                            VALUES (?, CURRENT_TIMESTAMP, 'Pending correction backfill probe', ?, ?, ?)
                            """, correctionAttendanceId, submittedAt,
                            Timestamp.from(submittedAt.toInstant().plusSeconds(48 * 3600L)),
                            Timestamp.from(submittedAt.toInstant().plusSeconds(96 * 3600L)));
                }

                notification(connection, internId, "/projects/" + projectId);
                                notification(connection, internId, "/projects/" + projectId + "/tasks/1");
                notification(connection, internId, "/projects/" + projectId + "?tab=tasks");
                notification(connection, internId, "/projects/" + projectId + "abc");
                notification(connection, internId, "/projects/" + (projectId + 999));
                notification(connection, internId, "/projects/not-a-number");
                notification(connection, internId, "/elsewhere/projects/" + projectId);
                connection.commit();
                return new Fixture(adminId, mentorId, internId, projectId, currentMonth, pastMonth, null, null);
            } catch (SQLException | RuntimeException exception) {
                connection.rollback();
                throw exception;
            }
        }
    }

    private static long insertAttendance(Connection connection, long internId, long policyId, LocalDate workDate)
            throws SQLException {
        Timestamp checkIn = Timestamp.valueOf(LocalDateTime.of(workDate, LocalTime.of(9, 0)));
        return generatedId(connection, """
                INSERT INTO attendance_records (intern_user_id, work_date, policy_version_id, check_in_at)
                VALUES (?, ?, ?, ?) RETURNING id
                """, internId, Date.valueOf(workDate), policyId, checkIn);
    }

    private static void notification(Connection connection, long recipientId, String route) throws SQLException {
        insert(connection, """
                INSERT INTO notifications (recipient_user_id, notification_type, title, body, action_url)
                VALUES (?, 'SYSTEM', 'Probe', 'Backfill route probe', ?)
                """, recipientId, route);
    }

    private static long user(Connection connection, String role) throws SQLException {
        return generatedId(connection, """
                INSERT INTO app_users (email, display_name, password_hash, global_role, account_status, activated_at)
                VALUES (?, ?, 'probe-hash', ?, 'ACTIVE', CURRENT_TIMESTAMP) RETURNING id
                """, role.toLowerCase() + "-" + UUID.randomUUID() + "@example.test", role, role);
    }

    private static long generatedId(Connection connection, String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, values);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }

    private static void insert(Connection connection, String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, values);
            statement.executeUpdate();
        }
    }

    private static void bind(PreparedStatement statement, Object... values) throws SQLException {
        for (int index = 0; index < values.length; index++) {
            statement.setObject(index + 1, values[index]);
        }
    }

    private static long scalarLong(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getLong(1);
        }
    }

    private static LocalDate scalarDate(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getDate(1).toLocalDate();
        }
    }

    private static LocalDateTime serverTime() throws SQLException {
        try (Connection connection = connect();
                Statement statement = connection.createStatement();
                ResultSet rows = statement.executeQuery("SELECT clock_timestamp()")) {
            rows.next();
            return rows.getTimestamp(1).toLocalDateTime();
        }
    }

    private static Long nullableLong(ResultSet rows, int column) throws SQLException {
        long value = rows.getLong(column);
        return rows.wasNull() ? null : value;
    }

    private static Connection connect() throws SQLException {
        return java.sql.DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    @FunctionalInterface
    private interface BackfillAssertion {
        void verify(Fixture fixture) throws Exception;
    }

    private enum PeriodCase {
        CURRENT,
        PAST_CLEAR,
        PAST_PENDING_LEAVE,
        PAST_PENDING_CORRECTION
    }

    private record Fixture(long adminId, long mentorId, long internId, long projectId,
                          LocalDate currentMonth, LocalDate pastMonth,
                          LocalDateTime migrationStartedAt, LocalDateTime migrationFinishedAt) {
        private Fixture withMigrationWindow(LocalDateTime start, LocalDateTime finish) {
            return new Fixture(adminId, mentorId, internId, projectId, currentMonth, pastMonth, start, finish);
        }
    }
}
