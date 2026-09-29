package com.lab.labtimesheet.platform.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.TimeZone;
import java.util.UUID;
import java.util.stream.Stream;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Probes the V9 attendance correction contract, append-only trigger, and event reason guards.
 *
 * <p>Protects {@code AC-DB-006}, {@code AC-DB-011}, {@code DB-017}, and {@code DB-018}.
 * Verifies that historical {@code AUTO_REJECTED} and {@code LOCKED} rows and events remain intact,
 * while new expiry event writes, unreasoned amendments/reversals, decided {@code OVERDUE} shapes,
 * and event updates/deletions are rejected by PostgreSQL.
 */
class CorrectionContractProbeIntegrationTest {

    private static final String CHECK_VIOLATION = "23514";
    private static final String USER_RAISE_EXCEPTION = "P0001";
    private static final String APPEND_ONLY_MESSAGE = "attendance correction events are append-only";

    @TestFactory
    Stream<DynamicTest> correctionContractIsEnforcedOnPostgreSQL() {
        return Stream.of(
                DynamicTest.dynamicTest("p1: historical correction and events remain intact after migration",
                        this::probeP1HistoricalRecordsRetained),
                DynamicTest.dynamicTest("p2: DB-017 rejects new AUTO_REJECTED and LOCKED events",
                        this::probeP2RejectsNewExpiryEvents),
                DynamicTest.dynamicTest("p3: DB-017 enforces nonblank reason for AMENDED and REVERSED events",
                        this::probeP3EnforcesAmendmentAndReversalReason),
                DynamicTest.dynamicTest("p4: DB-018 enforces that OVERDUE correction is undecided",
                        this::probeP4EnforcesOverdueUndecidedShape),
                DynamicTest.dynamicTest("p5: DB-017 append-only trigger blocks UPDATE and DELETE on events",
                        this::probeP5EnforcesAppendOnlyEvents));
    }

    private void probeP1HistoricalRecordsRetained() throws Exception {
        inBusinessTimezone(() -> {
            try (PostgreSQLContainer postgres = postgres()) {
                postgres.start();
                flyway(postgres, "7").migrate();
                long correctionId;
                long submittedEventId;
                long autoRejectedEventId;
                long lockedEventId;
                Instant lockedAt = Instant.parse("2026-08-06T10:00:00Z");
                try (Connection connection = connect(postgres)) {
                    Fixture fixture = seedUsers(connection);
                    long recordId = attendanceRecord(connection, fixture.internId(), LocalDate.of(2026, 8, 3));
                    correctionId = insertCorrection(connection, recordId, "REJECTED", null,
                            Instant.parse("2026-08-06T09:00:00Z"), lockedAt);
                    submittedEventId = insertEvent(connection, correctionId, "SUBMITTED", null, "PENDING",
                            fixture.internId(), "Initial submission", Instant.parse("2026-08-04T09:00:00Z"));
                    autoRejectedEventId = insertEvent(connection, correctionId, "AUTO_REJECTED", "PENDING", "REJECTED",
                            null, "Decision deadline expired", Instant.parse("2026-08-06T09:00:00Z"));
                    lockedEventId = insertEvent(connection, correctionId, "LOCKED", "REJECTED", "REJECTED",
                            null, null, lockedAt);
                    connection.commit();
                }
                flyway(postgres, null).migrate();
                try (Connection connection = connect(postgres)) {
                    try (PreparedStatement statement = connection.prepareStatement("""
                            SELECT status, locked_at FROM attendance_corrections WHERE id = ?
                            """)) {
                        statement.setLong(1, correctionId);
                        try (ResultSet rows = statement.executeQuery()) {
                            assertThat(rows.next()).isTrue();
                            assertThat(rows.getString("status")).isEqualTo("REJECTED");
                            assertThat(rows.getTimestamp("locked_at")).isEqualTo(Timestamp.from(lockedAt));
                        }
                    }
                    assertEvent(connection, submittedEventId, "SUBMITTED", null, "PENDING");
                    assertEvent(connection, autoRejectedEventId, "AUTO_REJECTED", "PENDING", "REJECTED");
                    assertEvent(connection, lockedEventId, "LOCKED", "REJECTED", "REJECTED");
                }
            }
        });
    }

    private void probeP2RejectsNewExpiryEvents() throws Exception {
        inBusinessTimezone(() -> {
            try (PostgreSQLContainer postgres = postgres()) {
                postgres.start();
                flyway(postgres, "7").migrate();
                long correctionId;
                try (Connection connection = connect(postgres)) {
                    Fixture fixture = seedUsers(connection);
                    long recordId = attendanceRecord(connection, fixture.internId(), LocalDate.of(2026, 8, 3));
                    correctionId = insertCorrection(connection, recordId, "PENDING", null, null, null);
                    connection.commit();
                }
                try (Connection connection = connect(postgres)) {
                    assertAccepted(connection,
                            () -> insertEvent(connection, correctionId, "AUTO_REJECTED", "PENDING", "REJECTED",
                                    null, "Auto rejected", Instant.parse("2026-08-06T09:00:00Z")));
                    assertAccepted(connection,
                            () -> insertEvent(connection, correctionId, "LOCKED", "REJECTED", "REJECTED",
                                    null, null, Instant.parse("2026-08-06T10:00:00Z")));
                }
                flyway(postgres, null).migrate();
                try (Connection connection = connect(postgres)) {
                    assertFailsWithConstraint(connection,
                            () -> insertEvent(connection, correctionId, "AUTO_REJECTED", "PENDING", "REJECTED",
                                    null, "Auto rejected", Instant.parse("2026-08-06T09:00:00Z")),
                            CHECK_VIOLATION, "ck_attendance_correction_events_no_expiry_kinds");
                    assertFailsWithConstraint(connection,
                            () -> insertEvent(connection, correctionId, "LOCKED", "REJECTED", "REJECTED",
                                    null, null, Instant.parse("2026-08-06T10:00:00Z")),
                            CHECK_VIOLATION, "ck_attendance_correction_events_no_expiry_kinds");
                }
            }
        });
    }

    private void probeP3EnforcesAmendmentAndReversalReason() throws Exception {
        inBusinessTimezone(() -> {
            try (PostgreSQLContainer postgres = postgres()) {
                postgres.start();
                flyway(postgres, "7").migrate();
                long correctionId;
                Fixture fixture;
                try (Connection connection = connect(postgres)) {
                    fixture = seedUsers(connection);
                    long recordId = attendanceRecord(connection, fixture.internId(), LocalDate.of(2026, 8, 3));
                    correctionId = insertCorrection(connection, recordId, "APPROVED", fixture.mentorId(),
                            Instant.parse("2026-08-05T10:00:00Z"), null);
                    connection.commit();
                }
                try (Connection connection = connect(postgres)) {
                    assertAccepted(connection,
                            () -> insertEvent(connection, correctionId, "AMENDED", "APPROVED", "APPROVED",
                                    fixture.mentorId(), null, Instant.parse("2026-08-05T11:00:00Z")));
                    assertAccepted(connection,
                            () -> insertEvent(connection, correctionId, "AMENDED", "APPROVED", "APPROVED",
                                    fixture.mentorId(), "   ", Instant.parse("2026-08-05T11:00:00Z")));
                    assertAccepted(connection,
                            () -> insertEvent(connection, correctionId, "REVERSED", "APPROVED", "REJECTED",
                                    fixture.mentorId(), null, Instant.parse("2026-08-05T11:00:00Z")));
                    assertAccepted(connection,
                            () -> insertEvent(connection, correctionId, "REVERSED", "APPROVED", "REJECTED",
                                    fixture.mentorId(), "    ", Instant.parse("2026-08-05T11:00:00Z")));
                }
                flyway(postgres, null).migrate();
                try (Connection connection = connect(postgres)) {
                    assertFailsWithConstraint(connection,
                            () -> insertEvent(connection, correctionId, "AMENDED", "APPROVED", "APPROVED",
                                    fixture.mentorId(), null, Instant.parse("2026-08-05T11:00:00Z")),
                            CHECK_VIOLATION, "ck_attendance_correction_events_reason");
                    assertFailsWithConstraint(connection,
                            () -> insertEvent(connection, correctionId, "AMENDED", "APPROVED", "APPROVED",
                                    fixture.mentorId(), "   ", Instant.parse("2026-08-05T11:00:00Z")),
                            CHECK_VIOLATION, "ck_attendance_correction_events_reason");
                    assertFailsWithConstraint(connection,
                            () -> insertEvent(connection, correctionId, "REVERSED", "APPROVED", "REJECTED",
                                    fixture.mentorId(), null, Instant.parse("2026-08-05T11:00:00Z")),
                            CHECK_VIOLATION, "ck_attendance_correction_events_reason");
                    assertFailsWithConstraint(connection,
                            () -> insertEvent(connection, correctionId, "REVERSED", "APPROVED", "REJECTED",
                                    fixture.mentorId(), "    ", Instant.parse("2026-08-05T11:00:00Z")),
                            CHECK_VIOLATION, "ck_attendance_correction_events_reason");

                    insertEvent(connection, correctionId, "AMENDED", "APPROVED", "APPROVED",
                            fixture.mentorId(), "Valid amendment note", Instant.parse("2026-08-05T11:00:00Z"));
                    insertEvent(connection, correctionId, "REVERSED", "APPROVED", "REJECTED",
                            fixture.mentorId(), "Valid reversal note", Instant.parse("2026-08-05T12:00:00Z"));
                }
            }
        });
    }

    private void probeP4EnforcesOverdueUndecidedShape() throws Exception {
        inBusinessTimezone(() -> {
            try (PostgreSQLContainer postgres = postgres()) {
                postgres.start();
                flyway(postgres, "7").migrate();
                Fixture fixture;
                try (Connection connection = connect(postgres)) {
                    fixture = seedUsers(connection);
                    connection.commit();
                }
                try (Connection connection = connect(postgres)) {
                    long rec1 = attendanceRecord(connection, fixture.internId(), LocalDate.of(2026, 8, 3));
                    assertAccepted(connection, () -> insertCorrection(connection, rec1, "OVERDUE", null, null, null));

                    long rec2 = attendanceRecord(connection, fixture.internId(), LocalDate.of(2026, 8, 4));
                    assertAccepted(connection, () -> insertCorrection(connection, rec2, "OVERDUE", null,
                            Instant.parse("2026-08-06T09:00:00Z"), null));

                    long rec3 = attendanceRecord(connection, fixture.internId(), LocalDate.of(2026, 8, 5));
                    assertAccepted(connection, () -> insertCorrection(connection, rec3, "OVERDUE",
                            fixture.mentorId(), null, null));
                    connection.commit();
                }
                flyway(postgres, null).migrate();
                try (Connection connection = connect(postgres)) {
                    long rec1 = attendanceRecord(connection, fixture.internId(), LocalDate.of(2026, 8, 6));
                    insertCorrection(connection, rec1, "OVERDUE", null, null, null);

                    long rec2 = attendanceRecord(connection, fixture.internId(), LocalDate.of(2026, 8, 7));
                    assertFailsWithConstraint(connection,
                            () -> insertCorrection(connection, rec2, "OVERDUE", null,
                                    Instant.parse("2026-08-06T09:00:00Z"), null),
                            CHECK_VIOLATION, "ck_attendance_corrections_pending_decision");

                    long rec3 = attendanceRecord(connection, fixture.internId(), LocalDate.of(2026, 8, 8));
                    assertFailsWithConstraint(connection,
                            () -> insertCorrection(connection, rec3, "OVERDUE", fixture.mentorId(), null, null),
                            CHECK_VIOLATION, "ck_attendance_corrections_pending_decision");
                }
            }
        });
    }

    private void probeP5EnforcesAppendOnlyEvents() throws Exception {
        inBusinessTimezone(() -> {
            try (PostgreSQLContainer postgres = postgres()) {
                postgres.start();
                flyway(postgres, "7").migrate();
                long eventId;
                try (Connection connection = connect(postgres)) {
                    Fixture fixture = seedUsers(connection);
                    long recordId = attendanceRecord(connection, fixture.internId(), LocalDate.of(2026, 8, 3));
                    long correctionId = insertCorrection(connection, recordId, "PENDING", null, null, null);
                    eventId = insertEvent(connection, correctionId, "SUBMITTED", null, "PENDING",
                            fixture.internId(), "Initial submission", Instant.parse("2026-08-04T09:00:00Z"));
                    connection.commit();
                }
                try (Connection connection = connect(postgres)) {
                    assertAccepted(connection, () -> {
                        try (PreparedStatement update = connection.prepareStatement("""
                                UPDATE attendance_correction_events SET note = 'tampered' WHERE id = ?
                                """)) {
                            update.setLong(1, eventId);
                            assertThat(update.executeUpdate()).isEqualTo(1);
                        }
                    });
                    assertAccepted(connection, () -> {
                        try (PreparedStatement delete = connection.prepareStatement("""
                                DELETE FROM attendance_correction_events WHERE id = ?
                                """)) {
                            delete.setLong(1, eventId);
                            assertThat(delete.executeUpdate()).isEqualTo(1);
                        }
                    });
                }
                flyway(postgres, null).migrate();
                try (Connection connection = connect(postgres)) {
                    assertFailsWithTrigger(connection, () -> {
                        try (PreparedStatement update = connection.prepareStatement("""
                                UPDATE attendance_correction_events SET note = 'tampered' WHERE id = ?
                                """)) {
                            update.setLong(1, eventId);
                            update.executeUpdate();
                        }
                    }, USER_RAISE_EXCEPTION, APPEND_ONLY_MESSAGE);

                    assertFailsWithTrigger(connection, () -> {
                        try (PreparedStatement delete = connection.prepareStatement("""
                                DELETE FROM attendance_correction_events WHERE id = ?
                                """)) {
                            delete.setLong(1, eventId);
                            delete.executeUpdate();
                        }
                    }, USER_RAISE_EXCEPTION, APPEND_ONLY_MESSAGE);
                }
            }
        });
    }

    private static void assertEvent(Connection connection, long eventId, String type, String from, String to)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT event_type, from_status, to_status FROM attendance_correction_events WHERE id = ?
                """)) {
            statement.setLong(1, eventId);
            try (ResultSet rows = statement.executeQuery()) {
                assertThat(rows.next()).isTrue();
                assertThat(rows.getString("event_type")).isEqualTo(type);
                assertThat(rows.getString("from_status")).isEqualTo(from);
                assertThat(rows.getString("to_status")).isEqualTo(to);
            }
        }
    }

    private static void assertFailsWithConstraint(Connection connection, SqlRunnable action,
            String expectedSqlState, String expectedConstraintName) throws SQLException {
        Savepoint savepoint = connection.setSavepoint();
        SQLException caught = null;
        try {
            action.run();
        } catch (SQLException ex) {
            caught = ex;
            connection.rollback(savepoint);
        }
        assertThat((Throwable) caught)
                .as("Operation should fail with check constraint violation")
                .isNotNull();
        assertThat(caught.getSQLState()).isEqualTo(expectedSqlState);
        assertThat(caught.getMessage()).contains(expectedConstraintName);
    }

    private static void assertFailsWithTrigger(Connection connection, SqlRunnable action,
            String expectedSqlState, String expectedMessage) throws SQLException {
        Savepoint savepoint = connection.setSavepoint();
        SQLException caught = null;
        try {
            action.run();
        } catch (SQLException ex) {
            caught = ex;
            connection.rollback(savepoint);
        }
        assertThat((Throwable) caught)
                .as("Operation should fail with trigger rejection")
                .isNotNull();
        assertThat(caught.getSQLState()).isEqualTo(expectedSqlState);
        assertThat(caught.getMessage()).contains(expectedMessage);
    }

    private static void assertAccepted(Connection connection, SqlRunnable action) throws SQLException {
        Savepoint savepoint = connection.setSavepoint();
        SQLException caught = null;
        try {
            action.run();
        } catch (SQLException ex) {
            caught = ex;
        } finally {
            connection.rollback(savepoint);
        }
        assertThat((Throwable) caught)
                .as("Operation should be accepted by the V7 schema")
                .isNull();
    }

    @FunctionalInterface
    private interface SqlRunnable {
        void run() throws SQLException;
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

    private static Flyway flyway(PostgreSQLContainer postgres, String target) {
        var configuration = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration");
        if (target != null) configuration.target(target);
        return configuration.load();
    }

    private static Connection connect(PostgreSQLContainer postgres) throws SQLException {
        Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl() + "&preferQueryMode=simple", postgres.getUsername(), postgres.getPassword());
        connection.setAutoCommit(false);
        return connection;
    }

    private record Fixture(long internId, long mentorId) {}

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

    private static long attendanceRecord(Connection connection, long internId, LocalDate workDate) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO attendance_records (intern_user_id, work_date, policy_version_id, check_in_at, check_out_at)
                VALUES (?, ?, 1, ?, NULL) RETURNING id
                """)) {
            statement.setLong(1, internId);
            statement.setObject(2, workDate);
            statement.setTimestamp(3, Timestamp.from(Instant.parse("2026-08-03T08:20:00Z")));
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }

    private static long insertCorrection(Connection connection, long recordId, String status,
            Long decidedByMentorId, Instant decidedAt, Instant lockedAt) throws SQLException {
        Instant submittedAt = Instant.parse("2026-08-04T09:00:00Z");
        Instant submissionDeadline = Instant.parse("2026-08-05T08:30:00Z");
        Instant decisionDeadline = Instant.parse("2026-08-06T09:00:00Z");
        Instant requestedCheckout = Instant.parse("2026-08-03T17:00:00Z");
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO attendance_corrections (
                    attendance_record_id, requested_checkout_at, reason, status, submitted_at,
                    submission_deadline, decision_deadline, decided_by_mentor_user_id, decided_at,
                    decision_note, locked_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id
                """)) {
            statement.setLong(1, recordId);
            statement.setTimestamp(2, Timestamp.from(requestedCheckout));
            statement.setString(3, "Probe correction " + status);
            statement.setString(4, status);
            statement.setTimestamp(5, Timestamp.from(submittedAt));
            statement.setTimestamp(6, Timestamp.from(submissionDeadline));
            statement.setTimestamp(7, Timestamp.from(decisionDeadline));
            if (decidedByMentorId == null) statement.setNull(8, java.sql.Types.BIGINT);
            else statement.setLong(8, decidedByMentorId);
            if (decidedAt == null) statement.setNull(9, java.sql.Types.TIMESTAMP_WITH_TIMEZONE);
            else statement.setTimestamp(9, Timestamp.from(decidedAt));
            statement.setString(10, decidedAt != null ? "Decision note" : null);
            if (lockedAt == null) statement.setNull(11, java.sql.Types.TIMESTAMP_WITH_TIMEZONE);
            else statement.setTimestamp(11, Timestamp.from(lockedAt));
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }

    private static long insertEvent(Connection connection, long correctionId, String eventType,
            String fromStatus, String toStatus, Long actorUserId, String note, Instant occurredAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO attendance_correction_events (
                    correction_id, event_type, from_status, to_status, actor_user_id, note, occurred_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?) RETURNING id
                """)) {
            statement.setLong(1, correctionId);
            statement.setString(2, eventType);
            statement.setString(3, fromStatus);
            statement.setString(4, toStatus);
            if (actorUserId == null) statement.setNull(5, java.sql.Types.BIGINT);
            else statement.setLong(5, actorUserId);
            statement.setString(6, note);
            statement.setTimestamp(7, Timestamp.from(occurredAt));
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }
}
