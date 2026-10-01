package com.lab.labtimesheet.platform.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.Date;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.TimeZone;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Probes the V10 leave request days withdrawn guard trigger on disposable PostgreSQL.
 *
 * <p>Protects {@code DB-017}, {@code DB-018}, and {@code AC-DB-006} (Leave portion):
 * observable break is PostgreSQL allowing modification of policy version, quota snapshot,
 * or approval_withdrawn_at once approval is withdrawn, or allowing concurrent snapshot edits
 * when withdrawing approval. Hand-derived result is rejection with SQLSTATE 23514 naming
 * the trigger {@code tr_leave_request_days_withdrawn_guard}, while withdrawing approval
 * without snapshot mutation on an active leave day is accepted.</p>
 */
class LeaveWithdrawnDayGuardProbeIntegrationTest {

    private static final String CHECK_VIOLATION = "23514";
    private static final String TRIGGER_NAME = "tr_leave_request_days_withdrawn_guard";

    /**
     * Proves the V10 guard rejects snapshot changes and timestamp alteration on withdrawn days,
     * while accepting legitimate approval-withdrawal marks on active days.
     *
     * <p>Protects {@code DB-017}, {@code DB-018}, and {@code AC-DB-006}.
     * Observable break: PostgreSQL permits changing policy or quota snapshot on a withdrawn day,
     * permits clearing or altering {@code approval_withdrawn_at}, or rejects setting it cleanly;
     * hand-derived result: all four mutations on withdrawn day fail with SQLSTATE 23514 and
     * name {@code tr_leave_request_days_withdrawn_guard}, while setting the timestamp alone succeeds.</p>
     */
    @Test
    void withdrawnLeaveDayGuardEnforcesImmutabilityOnPostgres() throws Exception {
        withPostgres(postgres -> {
            migrate(postgres);
            try (Connection connection = connect(postgres)) {
                Fixture fixture = seedFixture(connection);
                long requestId = fixture.requestId();
                long policyId = fixture.policyId();
                long secondPolicyId = fixture.secondPolicyId();

                LocalDate date1 = LocalDate.of(2026, 8, 17);
                LocalDate date2 = LocalDate.of(2026, 8, 18);
                Instant withdrawnAt = Instant.parse("2026-08-17T10:00:00Z");
                Instant laterWithdrawnAt = Instant.parse("2026-08-17T11:00:00Z");

                // Insert two leave_request_days: date1 already withdrawn, date2 not yet withdrawn
                insertLeaveDay(connection, requestId, date1, policyId, 3, withdrawnAt);
                insertLeaveDay(connection, requestId, date2, policyId, 3, null);
                connection.commit();

                // 1. On already withdrawn day: changing policy_version_id is rejected
                assertRejected(connection, () -> {
                    try (PreparedStatement stmt = connection.prepareStatement("""
                            UPDATE leave_request_days SET policy_version_id = ? WHERE leave_request_id = ? AND leave_date = ?
                            """)) {
                        stmt.setLong(1, secondPolicyId);
                        stmt.setLong(2, requestId);
                        stmt.setDate(3, Date.valueOf(date1));
                        stmt.executeUpdate();
                    }
                });

                // 2. On already withdrawn day: changing monthly_quota_snapshot is rejected
                assertRejected(connection, () -> {
                    try (PreparedStatement stmt = connection.prepareStatement("""
                            UPDATE leave_request_days SET monthly_quota_snapshot = ? WHERE leave_request_id = ? AND leave_date = ?
                            """)) {
                        stmt.setInt(1, 5);
                        stmt.setLong(2, requestId);
                        stmt.setDate(3, Date.valueOf(date1));
                        stmt.executeUpdate();
                    }
                });

                // 3. On already withdrawn day: setting approval_withdrawn_at to null is rejected
                assertRejected(connection, () -> {
                    try (PreparedStatement stmt = connection.prepareStatement("""
                            UPDATE leave_request_days SET approval_withdrawn_at = NULL WHERE leave_request_id = ? AND leave_date = ?
                            """)) {
                        stmt.setLong(1, requestId);
                        stmt.setDate(2, Date.valueOf(date1));
                        stmt.executeUpdate();
                    }
                });

                // 4. On already withdrawn day: changing approval_withdrawn_at to a different value is rejected
                assertRejected(connection, () -> {
                    try (PreparedStatement stmt = connection.prepareStatement("""
                            UPDATE leave_request_days SET approval_withdrawn_at = ? WHERE leave_request_id = ? AND leave_date = ?
                            """)) {
                        stmt.setTimestamp(1, Timestamp.from(laterWithdrawnAt));
                        stmt.setLong(2, requestId);
                        stmt.setDate(3, Date.valueOf(date1));
                        stmt.executeUpdate();
                    }
                });

                // 5. On an unwithdrawn day: setting approval_withdrawn_at and mutating quota_month in the same statement is rejected
                assertRejected(connection, () -> {
                    try (PreparedStatement stmt = connection.prepareStatement("""
                            UPDATE leave_request_days
                            SET approval_withdrawn_at = ?, quota_month = ?
                            WHERE leave_request_id = ? AND leave_date = ?
                            """)) {
                        stmt.setTimestamp(1, Timestamp.from(withdrawnAt));
                        stmt.setDate(2, Date.valueOf(LocalDate.of(2026, 9, 1)));
                        stmt.setLong(3, requestId);
                        stmt.setDate(4, Date.valueOf(date2));
                        stmt.executeUpdate();
                    }
                });

                // 6. On an unwithdrawn day: setting approval_withdrawn_at (without mutating other columns) is accepted
                assertAccepted(connection, () -> {
                    try (PreparedStatement stmt = connection.prepareStatement("""
                            UPDATE leave_request_days SET approval_withdrawn_at = ? WHERE leave_request_id = ? AND leave_date = ?
                            """)) {
                        stmt.setTimestamp(1, Timestamp.from(withdrawnAt));
                        stmt.setLong(2, requestId);
                        stmt.setDate(3, Date.valueOf(date2));
                        stmt.executeUpdate();
                    }
                });
            }
        });
    }

    private static void assertRejected(Connection connection, SqlRunnable action) throws SQLException {
        Savepoint savepoint = connection.setSavepoint();
        try {
            action.run();
            connection.rollback(savepoint);
            org.junit.jupiter.api.Assertions.fail("Expected SQL trigger exception but statement succeeded");
        } catch (SQLException exception) {
            assertThat((Throwable) exception).isInstanceOf(PSQLException.class);
            PSQLException psqlException = (PSQLException) exception;
            assertThat(psqlException.getSQLState()).isEqualTo(CHECK_VIOLATION);
            assertThat(psqlException.getMessage()).contains(TRIGGER_NAME);
        } finally {
            connection.rollback(savepoint);
            connection.releaseSavepoint(savepoint);
        }
    }

    private static void assertAccepted(Connection connection, SqlRunnable action) throws SQLException {
        Savepoint savepoint = connection.setSavepoint();
        try {
            action.run();
        } finally {
            connection.rollback(savepoint);
            connection.releaseSavepoint(savepoint);
        }
    }

    private static void insertLeaveDay(
            Connection connection, long requestId, LocalDate leaveDate, long policyId,
            int quotaSnapshot, Instant withdrawnAt) throws SQLException {
        try (PreparedStatement stmt = connection.prepareStatement("""
                INSERT INTO leave_request_days (leave_request_id, leave_date, quota_month,
                                                policy_version_id, monthly_quota_snapshot, approval_withdrawn_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """)) {
            stmt.setLong(1, requestId);
            stmt.setDate(2, Date.valueOf(leaveDate));
            stmt.setDate(3, Date.valueOf(leaveDate.withDayOfMonth(1)));
            stmt.setLong(4, policyId);
            stmt.setInt(5, quotaSnapshot);
            if (withdrawnAt == null) {
                stmt.setNull(6, java.sql.Types.TIMESTAMP_WITH_TIMEZONE);
            } else {
                stmt.setTimestamp(6, Timestamp.from(withdrawnAt));
            }
            stmt.executeUpdate();
        }
    }

    private static Fixture seedFixture(Connection connection) throws SQLException {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        long adminId;
        try (PreparedStatement stmt = connection.prepareStatement("""
                INSERT INTO app_users (email, display_name, global_role)
                VALUES (?, 'Admin probe', 'ADMIN') RETURNING id
                """)) {
            stmt.setString(1, "admin-" + suffix + "@example.test");
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                adminId = rs.getLong(1);
            }
        }
        long internId;
        try (PreparedStatement stmt = connection.prepareStatement("""
                INSERT INTO app_users (email, display_name, global_role)
                VALUES (?, 'Intern probe', 'INTERN') RETURNING id
                """)) {
            stmt.setString(1, "intern-" + suffix + "@example.test");
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                internId = rs.getLong(1);
            }
        }
        try (PreparedStatement stmt = connection.prepareStatement("""
                INSERT INTO intern_profiles (user_id, student_code, internship_start_date, internship_end_date)
                VALUES (?, ?, DATE '2026-01-01', DATE '2026-12-31')
                """)) {
            stmt.setLong(1, internId);
            stmt.setString(2, "STU-" + suffix);
            stmt.executeUpdate();
        }
        long policyId;
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT id FROM attendance_policy_versions ORDER BY id LIMIT 1")) {
            rs.next();
            policyId = rs.getLong(1);
        }
        long secondPolicyId;
        try (PreparedStatement stmt = connection.prepareStatement("""
                INSERT INTO attendance_policy_versions (effective_from, timezone_name, scheduled_start,
                                                        scheduled_end, check_in_grace_minutes, checkout_grace_minutes,
                                                        violation_penalty, monthly_leave_quota, created_by_user_id)
                VALUES (DATE '2026-09-01', 'Asia/Ho_Chi_Minh', '08:30:00', '15:30:00',
                        30, 30, 0.25, 4, ?) RETURNING id
                """)) {
            stmt.setLong(1, adminId);
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                secondPolicyId = rs.getLong(1);
            }
        }
        long requestId;
        try (PreparedStatement stmt = connection.prepareStatement("""
                INSERT INTO leave_requests (intern_user_id, start_date, end_date, reason, status,
                                            submitted_at, first_counted_start_at, decided_by_mentor_user_id, decided_at)
                VALUES (?, DATE '2026-08-17', DATE '2026-08-18', 'Probe request', 'APPROVED',
                        TIMESTAMPTZ '2026-08-10 00:00:00Z', TIMESTAMPTZ '2026-08-17 01:30:00Z', ?, TIMESTAMPTZ '2026-08-11 00:00:00Z')
                RETURNING id
                """)) {
            stmt.setLong(1, internId);
            stmt.setLong(2, adminId);
            try (ResultSet rs = stmt.executeQuery()) {
                rs.next();
                requestId = rs.getLong(1);
            }
        }
        return new Fixture(requestId, policyId, secondPolicyId);
    }

    private record Fixture(long requestId, long policyId, long secondPolicyId) {}

    @FunctionalInterface
    private interface SqlRunnable {
        void run() throws SQLException;
    }

    @FunctionalInterface
    private interface CheckedConsumer<T> {
        void accept(T value) throws Exception;
    }

    private static void withPostgres(CheckedConsumer<PostgreSQLContainer> action) throws Exception {
        TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        try (PostgreSQLContainer postgres = new PostgreSQLContainer(DockerImageName.parse("postgres:18.4"))) {
            postgres.start();
            action.accept(postgres);
        } finally {
            TimeZone.setDefault(original);
        }
    }

    private static void migrate(PostgreSQLContainer postgres) {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    private static Connection connect(PostgreSQLContainer postgres) throws SQLException {
        Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        connection.setAutoCommit(false);
        return connection;
    }
}
