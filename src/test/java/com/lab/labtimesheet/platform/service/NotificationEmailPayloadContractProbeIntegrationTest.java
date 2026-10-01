package com.lab.labtimesheet.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Savepoint;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.TimeZone;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.FlywayException;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Probes the notification email payload contract on disposable PostgreSQL.
 *
 * <p>Protects {@code NOT-012}, {@code AC-NOT-007}, and data-model {@code C-07}: the observable
 * break is PostgreSQL accepting a payload on {@code NOT_REQUIRED}/{@code UNAVAILABLE}, or
 * refusing a lawful shape; the hand-derived result is six forbidden single-field shapes rejected
 * under {@code ck_notifications_email_payload}, all five lawful states accepted, and a failed
 * migration leaving a legacy row and Flyway history unchanged.
 */
class NotificationEmailPayloadContractProbeIntegrationTest {

    private static final String CHECK_VIOLATION = "23514";
    private static final String PAYLOAD_CONSTRAINT = "ck_notifications_email_payload";

    /**
     * Proves the contract accepts payload-free terminal states and payload-bearing delivery states.
     *
     * <p>{@code NOT-012}, {@code AC-NOT-007}, and {@code C-07} would be observably broken if
     * PostgreSQL refused either payload-free state or a valid {@code PENDING}, {@code SENT}, or
     * {@code FAILED} row. Hand calculation gives five accepted rows, one for each status.
     */
    @Test
    void acceptsEveryLawfulEmailPayloadShape() throws Exception {
        withPostgres((postgres) -> {
            migrate(postgres);
            try (Connection connection = connect(postgres)) {
                long recipientId = recipient(connection);
                insert(connection, recipientId, "NOT_REQUIRED", null, null, null, null, null);
                insert(connection, recipientId, "UNAVAILABLE", null, null, null, null, null);
                insert(connection, recipientId, "PENDING", "to@example.test", "subject", "body",
                        "2026-09-29T10:00:00Z", null);
                insert(connection, recipientId, "SENT", "to@example.test", "subject", "body",
                        null, "2026-09-29T10:00:00Z");
                insert(connection, recipientId, "FAILED", "to@example.test", "subject", "body", null, null);
                connection.commit();
                assertThat(scalar(connection, "SELECT count(*) FROM notifications")).isEqualTo(5);
            }
        });
    }

    /**
     * Proves each forbidden payload shape fails only the named payload check.
     *
     * <p>{@code NOT-012}, {@code AC-NOT-007}, and {@code C-07} would be observably broken if any
     * single populated email field were accepted for a no-email state, or if required payload
     * could be omitted for an ordinary delivery state. Hand calculation gives nine refusals:
     * six single-field terminal-state rows and one missing-payload row for each delivery state.
     */
    @Test
    void rejectsForbiddenPayloadShapesByThePayloadConstraint() throws Exception {
        withPostgres((postgres) -> {
            migrate(postgres);
            try (Connection connection = connect(postgres)) {
                long recipientId = recipient(connection);
                for (String status : new String[] {"NOT_REQUIRED", "UNAVAILABLE"}) {
                    assertRejected(connection, recipientId, status, "to@example.test", null, null, null, null);
                    assertRejected(connection, recipientId, status, null, "subject", null, null, null);
                    assertRejected(connection, recipientId, status, null, null, "body", null, null);
                }
                assertRejected(connection, recipientId, "PENDING", null, null, null,
                        "2026-09-29T10:00:00Z", null);
                assertRejected(connection, recipientId, "SENT", null, null, null,
                        null, "2026-09-29T10:00:00Z");
                assertRejected(connection, recipientId, "FAILED", null, null, null, null, null);
            }
        });
    }

    /**
     * Proves migration failure does not rewrite legacy rows or mark V8 successful.
     *
     * <p>{@code NOT-012}, {@code AC-NOT-007}, and {@code C-07} would be observably broken if the
     * migration silently rewrote a stored payload. The hand-derived result is a failed V8 and
     * byte-for-byte unchanged values in the row's three email payload columns.
     */
    @Test
    void migrationPreservesViolatingLegacyRowsAndFailsWithoutSuccessfulHistory() throws Exception {
        withPostgres((postgres) -> {
            migrate(postgres, "7");
            long recipientId;
            long notificationId;
            try (Connection connection = connect(postgres)) {
                recipientId = recipient(connection);
                notificationId = insert(connection, recipientId, "UNAVAILABLE", "to@example.test",
                        "subject", "body", null, null);
                connection.commit();
            }

            assertThatThrownBy(() -> migrate(postgres))
                    .isInstanceOf(FlywayException.class)
                    .hasStackTraceContaining("ck_notifications_email_payload");
            try (Connection connection = connect(postgres)) {
                assertThat(java.util.Arrays.asList(payload(connection, notificationId)))
                        .containsExactly("to@example.test", "subject", "body");
                assertThat(scalar(connection,
                        "SELECT count(*) FROM flyway_schema_history WHERE version = '8' AND success"))
                        .isEqualTo(0);
            }
        });
    }

    private static void assertRejected(Connection connection, long recipientId, String status,
            String to, String subject, String body, String nextAttempt, String sentAt) throws Exception {
        Savepoint savepoint = connection.setSavepoint();
        try {
            insert(connection, recipientId, status, to, subject, body, nextAttempt, sentAt);
            throw new AssertionError("Expected the payload constraint to reject " + status);
        } catch (SQLException exception) {
            assertThat((Throwable) exception).isInstanceOf(PSQLException.class);
            PSQLException postgresException = (PSQLException) exception;
            assertThat(postgresException.getSQLState()).isEqualTo(CHECK_VIOLATION);
            assertThat(postgresException.getServerErrorMessage().getConstraint())
                    .isEqualTo(PAYLOAD_CONSTRAINT);
        } finally {
            connection.rollback(savepoint);
            connection.releaseSavepoint(savepoint);
        }
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
        migrate(postgres, null);
    }

    private static void migrate(PostgreSQLContainer postgres, String target) {
        var configuration = Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration");
        if (target != null) configuration.target(target);
        configuration.load().migrate();
    }

    private static Connection connect(PostgreSQLContainer postgres) throws SQLException {
        Connection connection = DriverManager.getConnection(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
        connection.setAutoCommit(false);
        return connection;
    }

    private static long recipient(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO app_users (email, display_name, global_role)
                VALUES (?, 'Notification probe', 'ADMIN') RETURNING id
                """)) {
            statement.setString(1, "ed01-" + UUID.randomUUID() + "@example.test");
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }

    private static long insert(Connection connection, long recipientId, String status,
            String to, String subject, String body, String nextAttempt, String sentAt) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO notifications
                  (recipient_user_id, notification_type, title, body, email_status,
                   email_to, email_subject, email_body, email_next_attempt_at, email_sent_at)
                VALUES (?, 'SYSTEM', 'Contract probe', 'Notification body', ?, ?, ?, ?, ?, ?)
                RETURNING id
                """)) {
            statement.setLong(1, recipientId);
            statement.setString(2, status);
            statement.setString(3, to);
            statement.setString(4, subject);
            statement.setString(5, body);
            statement.setObject(6, nextAttempt == null ? null : java.time.OffsetDateTime.parse(nextAttempt));
            statement.setObject(7, sentAt == null ? null : java.time.OffsetDateTime.parse(sentAt));
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }

    private static int scalar(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getInt(1);
        }
    }

    private static String[] payload(Connection connection, long id) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT email_to, email_subject, email_body FROM notifications WHERE id = ?")) {
            statement.setLong(1, id);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return new String[] {rows.getString(1), rows.getString(2), rows.getString(3)};
            }
        }
    }

    @FunctionalInterface
    private interface CheckedConsumer<T> {
        void accept(T value) throws Exception;
    }
}
