package com.lab.labtimesheet.platform.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.util.UUID;
import java.util.stream.Stream;
import javax.sql.DataSource;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** PostgreSQL-only acceptance and refusal probes for the Project cancellation contract. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class ProjectCancellationSchemaProbeIntegrationTest {

    private static final String CONSTRAINT = "ck_projects_cancellation";

    @Autowired
    private DataSource dataSource;

    /**
     * Protects {@code DB-019}, {@code AC-DB-012}, and the cancellation half of {@code AC-DB-007}.
     * Observable break: PostgreSQL accepts a cancelled row without actor/time/reason or refuses
     * either source status. Expected: only valid cancelled rows commit and malformed rows reach
     * the named check constraint with SQLSTATE 23514.
     *
     * @return independent isolated PostgreSQL probes
     */
    @TestFactory
    Stream<DynamicTest> projectCancellationContractIsEnforcedByPostgreSql() {
        return Stream.of(
                accepts("PLANNED cancellation without activated_at", false),
                accepts("ACTIVE cancellation retaining activated_at", true),
                rejects("missing cancellation Mentor", "cancelled_by_mentor_user_id = NULL"),
                rejects("missing cancellation time", "cancelled_at = NULL"),
                rejects("missing cancellation reason", "cancellation_reason = NULL"),
                rejects("blank cancellation reason", "cancellation_reason = '  '"));
    }

    private DynamicTest accepts(String label, boolean active) {
        return DynamicTest.dynamicTest("DB-019 AC-DB-012: " + label, () -> withFixture((connection, fixture) -> {
            if (active) {
                execute(connection, "UPDATE projects SET status='ACTIVE', activated_at=now() WHERE id=?", fixture.projectId);
            }
            execute(connection, """
                    UPDATE projects SET status='CANCELLED', cancelled_by_mentor_user_id=?,
                        cancelled_at=now(), cancellation_reason='Scope changed' WHERE id=?
                    """, fixture.mentorId, fixture.projectId);
            assertEquals("CANCELLED", scalar(connection, "SELECT status FROM projects WHERE id=?", fixture.projectId));
            assertEquals(active ? "t" : "f", scalar(connection,
                    "SELECT activated_at IS NOT NULL FROM projects WHERE id=?", fixture.projectId));
        }));
    }

    private DynamicTest rejects(String label, String assignment) {
        return DynamicTest.dynamicTest("DB-019 AC-DB-012: " + label, () -> withFixture((connection, fixture) -> {
            Savepoint savepoint = connection.setSavepoint();
            PSQLException failure = null;
            try {
                execute(connection, "UPDATE projects SET status='CANCELLED', cancelled_by_mentor_user_id=?, "
                        + "cancelled_at=now(), cancellation_reason='Scope changed' WHERE id=?",
                        fixture.mentorId, fixture.projectId);
                execute(connection, "UPDATE projects SET " + assignment + " WHERE id=?", fixture.projectId);
            } catch (SQLException exception) {
                failure = postgresException(exception);
            } finally {
                connection.rollback(savepoint);
                connection.releaseSavepoint(savepoint);
            }
            assertNotNull(failure, "malformed cancellation must be rejected by PostgreSQL");
            assertEquals("23514", failure.getSQLState());
            assertEquals(CONSTRAINT, serverError(failure).getConstraint());
        }));
    }

    private void withFixture(Probe probe) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Fixture fixture = seed(connection);
                probe.run(connection, fixture);
            } finally {
                connection.rollback();
            }
        }
    }

    private static Fixture seed(Connection connection) throws SQLException {
        String suffix = UUID.randomUUID().toString();
        long mentorId = user(connection, "MENTOR", "mentor-" + suffix);
        long internId = user(connection, "INTERN", "intern-" + suffix);
        execute(connection, """
                INSERT INTO intern_profiles (user_id, student_code, internship_start_date, internship_end_date)
                VALUES (?, ?, CURRENT_DATE - 10, CURRENT_DATE + 200)
                """, internId, "student-" + suffix);
        long projectId = generatedId(connection, """
                INSERT INTO projects (mentor_user_id, name, start_date, end_date)
                VALUES (?, ?, CURRENT_DATE, CURRENT_DATE + 30) RETURNING id
                """, mentorId, "cancellation-probe-" + suffix);
        long membershipId = generatedId(connection, """
                INSERT INTO project_memberships (project_id, intern_user_id, added_by_user_id)
                VALUES (?, ?, ?) RETURNING id
                """, projectId, internId, mentorId);
        execute(connection, """
                INSERT INTO project_leadership_terms (project_id, membership_id, appointed_by_mentor_user_id)
                VALUES (?, ?, ?)
                """, projectId, membershipId, mentorId);
        return new Fixture(mentorId, projectId);
    }

    private static long user(Connection connection, String role, String suffix) throws SQLException {
        return generatedId(connection, """
                INSERT INTO app_users (email, display_name, password_hash, global_role, account_status, activated_at)
                VALUES (?, ?, '{noop}probe-password', ?, 'ACTIVE', now()) RETURNING id
                """, suffix + "@example.test", suffix, role);
    }

    private static long generatedId(Connection connection, String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, values);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) fail("INSERT RETURNING produced no row");
                return rows.getLong(1);
            }
        }
    }

    private static void execute(Connection connection, String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, values);
            statement.executeUpdate();
        }
    }

    private static String scalar(Connection connection, String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, values);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getString(1);
            }
        }
    }

    private static void bind(PreparedStatement statement, Object... values) throws SQLException {
        for (int index = 0; index < values.length; index++) statement.setObject(index + 1, values[index]);
    }

    private static PSQLException postgresException(SQLException exception) {
        SQLException cause = exception;
        while (cause != null && !(cause instanceof PSQLException)) cause = cause.getNextException();
        return (PSQLException) cause;
    }

    private static ServerErrorMessage serverError(PSQLException exception) {
        return exception.getServerErrorMessage();
    }

    @FunctionalInterface
    private interface Probe {
        void run(Connection connection, Fixture fixture) throws Exception;
    }

    private record Fixture(long mentorId, long projectId) { }
}
