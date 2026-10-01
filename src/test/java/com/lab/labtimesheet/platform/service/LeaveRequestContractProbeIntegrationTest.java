package com.lab.labtimesheet.platform.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.TimeZone;
import java.util.UUID;
import java.util.stream.Stream;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Probes C.5 reclassification and the Leave predicates of DB-018 on disposable PostgreSQL.
 *
 * <p>Protects {@code C.5}, {@code DB-018}, {@code AC-DB-006}, and {@code AC-DB-011}. Before V7,
 * the row without a decision remains {@code CANCELLED} and the new invalid shapes are accepted;
 * after V7 that row is {@code WITHDRAWN} with its original cancellation instant, while the
 * database rejects each undecided/withdrawn/cancelled shape missing its required fields.
 */
class LeaveRequestContractProbeIntegrationTest {

    private static final String CHECK_VIOLATION = "23514";

    /**
     * Migrates a private PostgreSQL instance to V6, writes the C.5 rows, then probes V7 reclassification
     * and each Leave-specific DB-018 predicate independently.
     *
     * @return independent PostgreSQL probes for reclassification and row-shape predicates
     */
    @TestFactory
    Stream<DynamicTest> leaveRequestContractIsEnforcedAfterReclassification() {
        return Stream.of(
                DynamicTest.dynamicTest("C.5: undecided CANCELLED becomes WITHDRAWN; approved cancellation stays",
                        this::reclassifiesLegacyRows),
                DynamicTest.dynamicTest("AC-DB-011: DB-018 accepts only lawful Leave row shapes",
                        this::enforcesLeavePredicates));
    }

    private void reclassifiesLegacyRows() throws Exception {
        inBusinessTimezone(() -> {
            try (PostgreSQLContainer postgres = postgres()) {
                postgres.start();
                Flyway flyway = flyway(postgres);
                flyway(postgres, "6").migrate();
                Fixture fixture;
                long withdrawnId;
                long cancelledId;
                Instant withdrawnAt = Instant.parse("2026-09-20T10:15:00Z");
                Instant cancelledAt = Instant.parse("2026-09-21T11:30:00Z");
                try (Connection connection = connect(postgres)) {
                    fixture = seedUsers(connection);
                    withdrawnId = leave(connection, fixture.internId(), null,
                            "CANCELLED", null, withdrawnAt, null);
                    cancelledId = leave(connection, fixture.internId(), fixture.mentorId(),
                            "CANCELLED", cancelledAt, cancelledAt, null);
                    connection.commit();
                    flyway.migrate();
                }
                try (Connection connection = connect(postgres)) {
                    assertLeave(connection, withdrawnId, "WITHDRAWN", withdrawnAt, null);
                    assertLeave(connection, cancelledId, "CANCELLED", null, cancelledAt);
                }
            }
        });
    }

    private void enforcesLeavePredicates() throws Exception {
        inBusinessTimezone(() -> {
            try (PostgreSQLContainer postgres = postgres()) {
                postgres.start();
                Flyway flyway = flyway(postgres);
                flyway(postgres, "6").migrate();
                try (Connection connection = connect(postgres)) {
                    Fixture fixture = seedUsers(connection);
                    connection.commit();
                    flyway.migrate();

                Instant decidedAt = Instant.parse("2026-09-20T10:15:00Z");
                long date = 1;
                leave(connection, fixture.internId(), null, "PENDING", null, null, null, date++);
                leave(connection, fixture.internId(), null, "OVERDUE", null, null, null, date++);
                leave(connection, fixture.internId(), null, "WITHDRAWN", null, null, decidedAt, date++);
                leave(connection, fixture.internId(), fixture.mentorId(), "CANCELLED",
                        decidedAt, decidedAt, null, date++);
                leave(connection, fixture.internId(), fixture.mentorId(), "REJECTED",
                        decidedAt, null, null, date++);

                    rejects(connection, fixture, "PENDING", fixture.mentorId(), decidedAt, null, null, date++);
                    rejects(connection, fixture, "OVERDUE", null, decidedAt, null, null, date++);
                    rejects(connection, fixture, "OVERDUE", fixture.mentorId(), null, null, null, date++);
                    rejects(connection, fixture, "WITHDRAWN", fixture.mentorId(), null, null, decidedAt, date++);
                rejects(connection, fixture, "WITHDRAWN", null, decidedAt, null, decidedAt, date++);
                    rejects(connection, fixture, "WITHDRAWN", null, null, null, null, date++);
                    rejects(connection, fixture, "CANCELLED", fixture.mentorId(), null, decidedAt, null, date++);
                    rejects(connection, fixture, "CANCELLED", null, decidedAt, decidedAt, null, date);
                }
            }
        });
    }

    private static void inBusinessTimezone(CheckedAction action) throws Exception {
        TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        try {
            action.run();
        } finally {
            TimeZone.setDefault(original);
        }
    }

    @FunctionalInterface
    private interface CheckedAction {
        void run() throws Exception;
    }

    private static PostgreSQLContainer postgres() {
        return new PostgreSQLContainer(DockerImageName.parse("postgres:18.4"));
    }

    private static Flyway flyway(PostgreSQLContainer postgres) {
        return flyway(postgres, null);
    }

    private static Flyway flyway(PostgreSQLContainer postgres, String target) {
        var configuration = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration");
        if (target != null) configuration.target(target);
        return configuration.load();
    }

    private static Connection connect(PostgreSQLContainer postgres) throws SQLException {
        Connection connection = java.sql.DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        connection.setAutoCommit(false);
        return connection;
    }

    private static Fixture seedUsers(Connection connection) throws SQLException {
        long mentorId = user(connection, "MENTOR");
        long internId = user(connection, "INTERN");
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO intern_profiles (user_id, student_code, internship_start_date, internship_end_date)
                VALUES (?, ?, DATE '2026-01-01', DATE '2027-12-31')
                """)) {
            statement.setLong(1, internId);
            statement.setString(2, "probe-" + UUID.randomUUID());
            statement.executeUpdate();
        }
        return new Fixture(internId, mentorId);
    }

    private static long user(Connection connection, String role) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO app_users (email, display_name, global_role)
                VALUES (?, ?, ?) RETURNING id
                """)) {
            statement.setString(1, role.toLowerCase() + "-" + UUID.randomUUID() + "@example.test");
            statement.setString(2, role + " probe");
            statement.setString(3, role);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }

    private static long leave(Connection connection, long internId, Long mentorId, String status,
            Instant decidedAt, Instant cancelledAt, Instant withdrawnAt) throws SQLException {
        return leave(connection, internId, mentorId, status, decidedAt, cancelledAt, withdrawnAt,
                Math.abs(UUID.randomUUID().getMostSignificantBits() % 100_000) + 100L);
    }

    private static long leave(Connection connection, long internId, Long mentorId, String status,
            Instant decidedAt, Instant cancelledAt, Instant withdrawnAt, long day) throws SQLException {
        Instant submittedAt = Instant.parse("2026-09-01T00:00:00Z");
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO leave_requests
                  (intern_user_id, start_date, end_date, reason, status, submitted_at,
                   first_counted_start_at, decided_by_mentor_user_id, decided_at, cancelled_at, withdrawn_at)
                VALUES (?, DATE '2028-01-01' + (?::integer),
                        DATE '2028-01-01' + (?::integer), ?, ?, ?, ?, ?, ?, ?, ?)
                RETURNING id
                """)) {
            statement.setLong(1, internId);
            statement.setLong(2, day);
            statement.setLong(3, day);
            statement.setString(4, "Contract probe " + status);
            statement.setString(5, status);
            statement.setTimestamp(6, Timestamp.from(submittedAt));
            statement.setTimestamp(7, Timestamp.from(submittedAt.plusSeconds(86400)));
            if (mentorId == null) statement.setNull(8, java.sql.Types.BIGINT);
            else statement.setLong(8, mentorId);
            timestamp(statement, 9, decidedAt);
            timestamp(statement, 10, cancelledAt);
            timestamp(statement, 11, withdrawnAt);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }

    private static void timestamp(PreparedStatement statement, int index, Instant value) throws SQLException {
        if (value == null) statement.setNull(index, java.sql.Types.TIMESTAMP_WITH_TIMEZONE);
        else statement.setTimestamp(index, Timestamp.from(value));
    }

    private static void assertLeave(Connection connection, long id, String status,
            Instant withdrawnAt, Instant cancelledAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT status, withdrawn_at, cancelled_at FROM leave_requests WHERE id=?
                """)) {
            statement.setLong(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("status")).isEqualTo(status);
                Timestamp storedWithdrawal = rows.getTimestamp("withdrawn_at");
                Timestamp storedCancellation = rows.getTimestamp("cancelled_at");
                if (withdrawnAt == null) assertThat(storedWithdrawal).isNull();
                else assertThat(storedWithdrawal).isEqualTo(Timestamp.from(withdrawnAt));
                if (cancelledAt == null) assertThat(storedCancellation).isNull();
                else assertThat(storedCancellation).isEqualTo(Timestamp.from(cancelledAt));
            }
        }
    }

    private static void rejects(Connection connection, Fixture fixture, String status, Long mentorId,
            Instant decidedAt, Instant cancelledAt, Instant withdrawnAt, long day) throws SQLException {
        Savepoint savepoint = connection.setSavepoint();
        SQLException failure = null;
        try {
            leave(connection, fixture.internId(), mentorId, status, decidedAt, cancelledAt, withdrawnAt, day);
        } catch (SQLException exception) {
            failure = exception;
            connection.rollback(savepoint);
        }
        assertThat((Object) failure)
                .as("DB-018 refuses invalid %s actor/time/withdrawal shape", status).isNotNull();
        assertThat(failure.getSQLState()).as("DB-018 check violation").isEqualTo(CHECK_VIOLATION);
        connection.releaseSavepoint(savepoint);
    }

    private record Fixture(long internId, long mentorId) { }
}
