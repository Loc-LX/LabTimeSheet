package com.lab.labtimesheet.platform.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Savepoint;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.sql.DataSource;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.postgresql.util.PSQLException;
import org.postgresql.util.ServerErrorMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * PostgreSQL probes for the V3 expansion of plan section C.3, run against the schema that the
 * repository's Flyway migrations produce.
 *
 * <p>Protects {@code AC-DB-001}, {@code AC-DB-008}, {@code AC-DB-010}, {@code DB-002},
 * {@code DB-006}, {@code PRJ-002},
 * {@code DB-011}, {@code DB-014} through {@code DB-022}, and the column names of {@code D45}.
 * Against V1+V2, a probe of a new table must fail with {@code 42P01}, a probe of a new column with
 * {@code 42703}, and a probe of a newly lawful row with the old check constraint that refuses it.
 * A probe that also asserts a refusal the rules keep does so before its V3 step, in a savepoint.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class SchemaExpansionProbeIntegrationTest {

    private static final String CHECK_VIOLATION = "23514";
    private static final Pattern DIAGRAM_ENTITY = Pattern.compile("(?m)^    ([a-z][a-z0-9_]*) \\{$");
    private static final Pattern DIAGRAM_FOREIGN_KEY = Pattern.compile(
            "(?m)^    [a-z][a-z0-9_]* (?:\\|\\||o\\|)--(?:o\\{|\\|\\{|o\\|) "
                    + "([a-z][a-z0-9_]*) : \"(fk_[a-z0-9_]+)\"$");
    private static final Pattern MIGRATION_FOREIGN_KEY = Pattern.compile("CONSTRAINT (fk_[a-z0-9_]+)");
    @Autowired
    private DataSource dataSource;

    /**
     * Runs independent acceptance and refusal probes for the six V3 tables, every expanded
     * predicate, and the added columns. A newly lawful value that the old schema rejects is an
     * observable RED; a missing table or column is also RED, but the asserted SQLSTATE and
     * constraint prevent a missing-object error from masquerading as a working refusal.
     *
     * @return isolated PostgreSQL probes with stable requirement and constraint names
     */
    @TestFactory
    Stream<DynamicTest> v3ConstraintsAndNewShapesAreProbedOnPostgreSql() {
        List<DynamicTest> probes = new ArrayList<>();

        probes.add(accepts("AC-DB-001: catalog tables and foreign keys match §19.4", (c, f) -> {
            DiagramContract diagram = diagramContract();
            Set<String> actualTables = new HashSet<>();
            try (Statement statement = c.createStatement(); ResultSet rows = statement.executeQuery("""
                    SELECT table_name FROM information_schema.tables
                    WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
                      AND table_name <> 'flyway_schema_history'
                    """)) {
                while (rows.next()) {
                    actualTables.add(rows.getString(1));
                }
            }
            assertThat(diagram.tables()).as("AC-DB-001 §19.4 table count").hasSize(30);
            assertThat(actualTables).as("AC-DB-001 exact application table set from §19.4")
                    .containsExactlyInAnyOrderElementsOf(diagram.tables());

            Set<String> actualForeignKeys = new HashSet<>();
            try (Statement statement = c.createStatement(); ResultSet rows = statement.executeQuery("""
                    SELECT constraint_row.conname
                    FROM pg_constraint constraint_row
                    JOIN pg_class relation ON relation.oid = constraint_row.conrelid
                    WHERE constraint_row.contype = 'f'
                      AND relation.relnamespace = 'public'::regnamespace
                      AND relation.relname <> 'flyway_schema_history'
                    """)) {
                while (rows.next()) {
                    actualForeignKeys.add(rows.getString(1));
                }
            }
            assertThat(actualForeignKeys).as("AC-DB-001 exact FK-name set from §19.4")
                    .containsExactlyInAnyOrderElementsOf(diagram.foreignKeys());

            Set<String> baselineForeignKeys = baselineForeignKeys();
            assertThat(baselineForeignKeys).as("V1 declares 56 named foreign keys").hasSize(56);
            assertThat(diagram.foreignKeys()).as("every V1 foreign key is named in §19.4")
                    .containsAll(baselineForeignKeys);
            assertThat(actualForeignKeys).as("every V1 foreign key remains in the catalog")
                    .containsAll(baselineForeignKeys);
        }));
        probes.add(accepts("DB-006: every foreign key has an index led by its columns", (c, f) -> {
            List<String> unindexedForeignKeys = new ArrayList<>();
            try (Statement statement = c.createStatement(); ResultSet rows = statement.executeQuery("""
                    SELECT constraint_row.conname || ' (' || relation.relname || '.' ||
                           array_to_string(ARRAY(
                               SELECT attribute.attname
                               FROM unnest(constraint_row.conkey) WITH ORDINALITY AS fk_key(attnum, ordinal)
                               JOIN pg_attribute attribute
                                 ON attribute.attrelid = constraint_row.conrelid
                                AND attribute.attnum = fk_key.attnum
                               ORDER BY fk_key.ordinal
                           ), ', ') || ')'
                    FROM pg_constraint constraint_row
                    JOIN pg_class relation ON relation.oid = constraint_row.conrelid
                    WHERE constraint_row.contype = 'f'
                      AND relation.relnamespace = 'public'::regnamespace
                      AND NOT EXISTS (
                          SELECT 1
                          FROM pg_index index_row
                          WHERE index_row.indrelid = constraint_row.conrelid
                            AND index_row.indisvalid AND index_row.indisready
                            AND index_row.indpred IS NULL
                            AND (
                                SELECT array_agg(index_key.attnum::smallint ORDER BY index_key.ordinal)
                                FROM unnest(index_row.indkey) WITH ORDINALITY AS index_key(attnum, ordinal)
                                WHERE index_key.ordinal <= index_row.indnkeyatts
                                  AND index_key.ordinal <= cardinality(constraint_row.conkey)
                            ) = constraint_row.conkey
                      )
                    ORDER BY constraint_row.conname
                    """)) {
                while (rows.next()) {
                    unindexedForeignKeys.add(rows.getString(1));
                }
            }
            assertThat(unindexedForeignKeys).as("DB-006 foreign keys without a matching leading-column index")
                    .isEmpty();
        }));
        probes.add(accepts("PRJ-002: deleting a Project clears its notifications' Project link", (c, f) -> {
            long projectId = generatedId(c, """
                    INSERT INTO projects (mentor_user_id, name, start_date, end_date)
                    VALUES (?, ?, CURRENT_DATE - 10, CURRENT_DATE + 365) RETURNING id
                    """, f.mentorId, "notification-delete-probe-" + UUID.randomUUID());
            long notificationId = generatedId(c, """
                    INSERT INTO notifications (recipient_user_id, notification_type, title, body, action_url, project_id)
                    VALUES (?, 'SYSTEM', 'Project notice', 'Retained when its Project is deleted', ?, ?) RETURNING id
                    """, f.internId, "/projects/" + projectId, projectId);

            assertThat(insert(c, "DELETE FROM projects WHERE id=?", projectId))
                    .as("PRJ-002 deletes the childless Project")
                    .isEqualTo(1);
            try (PreparedStatement statement = c.prepareStatement(
                    "SELECT project_id FROM notifications WHERE id=?")) {
                statement.setLong(1, notificationId);
                try (ResultSet rows = statement.executeQuery()) {
                    assertThat(rows.next()).as("PRJ-002 keeps the notification row").isTrue();
                    assertThat(rows.getObject("project_id")).as("PRJ-002 clears the Project link").isNull();
                }
            }
        }));
        probes.add(accepts("DB-014: an OPEN period has no finalization time", (c, f) -> {
            insert(c, """
                    INSERT INTO attendance_periods (intern_user_id, period_month, status, finalized_at)
                    VALUES (?, DATE '2026-01-01', 'OPEN', NULL)
                    """, f.internId);
        }));
        probes.add(accepts("DB-014: a FINALIZED period records its finalization time", (c, f) -> {
            insert(c, """
                    INSERT INTO attendance_periods (intern_user_id, period_month, status, finalized_at)
                    VALUES (?, DATE '2026-01-01', 'FINALIZED', TIMESTAMPTZ '2026-02-06 06:59:00+00')
                    """, f.internId);
        }));
        probes.add(rejects("DB-014: the same Intern cannot have two periods for one month",
                "23505", "uq_attendance_periods_intern_month", (c, f) -> {
                    period(c, f.internId, Date.valueOf("2026-01-01"), "OPEN", null);
                    period(c, f.internId, Date.valueOf("2026-01-01"), "OPEN", null);
                }));
        probes.add(rejects("DB-014: an unknown period status is refused", CHECK_VIOLATION,
                "ck_attendance_periods_status", (c, f) -> period(c, f.internId,
                        Date.valueOf("2026-01-01"), "CLOSED", null)));
        probes.add(rejects("DB-014: an OPEN period cannot carry finalized_at", CHECK_VIOLATION,
                "ck_attendance_periods_finalized_at", (c, f) -> period(c, f.internId,
                        Date.valueOf("2026-01-01"), "OPEN", Timestamp.from(Instant.parse("2026-02-06T06:59:00Z")))));
        probes.add(rejects("DB-014: a FINALIZED period requires finalized_at", CHECK_VIOLATION,
                "ck_attendance_periods_finalized_at", (c, f) -> period(c, f.internId,
                        Date.valueOf("2026-01-01"), "FINALIZED", null)));
        probes.add(rejects("DB-014: the period month must be its first day", CHECK_VIOLATION,
                "ck_attendance_periods_month_start", (c, f) -> period(c, f.internId,
                        Date.valueOf("2026-01-02"), "OPEN", null)));
        probes.add(rejects("DB-014: a period must reference an Intern profile", "23503",
                "fk_attendance_periods_intern", (c, f) -> period(c, Long.MAX_VALUE,
                        Date.valueOf("2026-01-01"), "OPEN", null)));

        probes.add(accepts("DB-015: a pending reopen stores its requested date range and reason", (c, f) -> {
            long periodId = period(c, f.internId, Date.valueOf("2025-01-01"), "FINALIZED",
                    Timestamp.from(Instant.parse("2025-02-06T06:59:00Z")));
            reopen(c, periodId, f.internId, Date.valueOf("2025-01-03"), Date.valueOf("2025-01-04"),
                    "Correct the recorded dates", "PENDING", null, null, null);
        }));
        probes.add(rejects("DB-015: a reopen requires a nonblank reason", CHECK_VIOLATION,
                "ck_attendance_period_reopens_reason", (c, f) -> reopen(c,
                        period(c, f.internId, Date.valueOf("2025-01-01"), "FINALIZED",
                                Timestamp.from(Instant.parse("2025-02-06T06:59:00Z"))),
                        f.internId, Date.valueOf("2025-01-03"), Date.valueOf("2025-01-04"), "  ",
                        "PENDING", null, null, null)));
        probes.add(rejects("DB-015: a reopen must identify records or a date range", CHECK_VIOLATION,
                "ck_attendance_period_reopens_scope", (c, f) -> reopen(c,
                        period(c, f.internId, Date.valueOf("2025-01-01"), "FINALIZED",
                                Timestamp.from(Instant.parse("2025-02-06T06:59:00Z"))),
                        f.internId, null, null, "Correct the record", "PENDING", null, null, null)));
        probes.add(rejects("DB-015: a decided reopen requires an Admin and decision time", CHECK_VIOLATION,
                "ck_attendance_period_reopens_decision", (c, f) -> reopen(c,
                        period(c, f.internId, Date.valueOf("2025-01-01"), "FINALIZED",
                                Timestamp.from(Instant.parse("2025-02-06T06:59:00Z"))),
                        f.internId, Date.valueOf("2025-01-03"), Date.valueOf("2025-01-04"),
                        "Correct the record", "APPROVED", null, null, null)));
        probes.add(accepts("DB-015: an approved reopen retains its Admin and decision time", (c, f) -> {
            long periodId = period(c, f.internId, Date.valueOf("2025-01-01"), "FINALIZED",
                    Timestamp.from(Instant.parse("2025-02-06T06:59:00Z")));
            reopen(c, periodId, f.internId, Date.valueOf("2025-01-03"), Date.valueOf("2025-01-04"),
                    "Correct the recorded dates", "APPROVED", f.adminId,
                    Timestamp.from(Instant.parse("2025-02-07T00:00:00Z")), null);
        }));
        probes.add(rejects("DB-015: a rejected reopen requires a nonblank rejection reason", CHECK_VIOLATION,
                "ck_attendance_period_reopens_rejection_reason", (c, f) -> {
                    long periodId = period(c, f.internId, Date.valueOf("2025-01-01"), "FINALIZED",
                            Timestamp.from(Instant.parse("2025-02-06T06:59:00Z")));
                    reopen(c, periodId, f.internId, Date.valueOf("2025-01-03"), Date.valueOf("2025-01-04"),
                            "Correct the recorded dates", "REJECTED", f.adminId,
                            Timestamp.from(Instant.parse("2025-02-07T00:00:00Z")), "  ");
                }));

        probes.add(accepts("DB-016: a requested attendance exception has a reason and deadlines", (c, f) -> {
            attendanceException(c, f.attendanceId, "LATE_ARRIVAL", "REQUEST", "Personal reason", "PENDING");
        }));
        probes.add(accepts("DB-016: a Mentor mark uses the other lawful source", (c, f) -> {
            attendanceException(c, f.attendanceId, "EARLY_DEPARTURE", "MENTOR_MARK", "Verified reason", "EXCUSED");
        }));
        probes.add(rejects("DB-016: one record cannot have the same exception kind twice", "23505",
                "uq_attendance_exceptions_record_kind", (c, f) -> {
                    attendanceException(c, f.attendanceId, "LATE_ARRIVAL", "REQUEST", "Reason", "PENDING");
                    attendanceException(c, f.attendanceId, "LATE_ARRIVAL", "REQUEST", "Reason", "PENDING");
                }));
        probes.add(rejects("DB-016: an exception kind is constrained", CHECK_VIOLATION,
                "ck_attendance_exceptions_kind", (c, f) -> attendanceException(
                        c, f.attendanceId, "CHECKOUT", "REQUEST", "Reason", "PENDING")));
        probes.add(rejects("DB-016: an exception status is constrained", CHECK_VIOLATION,
                "ck_attendance_exceptions_status", (c, f) -> attendanceException(
                        c, f.attendanceId, "LATE_ARRIVAL", "REQUEST", "Reason", "CANCELLED")));
        probes.add(rejects("DB-016: an exception reason is nonblank", CHECK_VIOLATION,
                "ck_attendance_exceptions_reason", (c, f) -> attendanceException(
                        c, f.attendanceId, "LATE_ARRIVAL", "REQUEST", "  ", "PENDING")));

        probes.add(accepts("DB-017: a leave amendment records its outcome and reason", (c, f) -> {
            long leaveId = pendingLeave(c, f.internId, Date.valueOf("2026-03-01"), Date.valueOf("2026-03-01"));
            leaveDecision(c, leaveId, "AMENDMENT", "APPROVED", f.mentorId, "Changed date", "Reason");
        }));
        probes.add(rejects("DB-017: a leave amendment reason is nonblank", CHECK_VIOLATION,
                "ck_leave_request_decisions_reason", (c, f) -> {
                    long leaveId = pendingLeave(c, f.internId, Date.valueOf("2026-03-01"), Date.valueOf("2026-03-01"));
                    leaveDecision(c, leaveId, "AMENDMENT", "APPROVED", f.mentorId, "Changed date", " ");
                }));
        probes.add(rejectsTrigger("DB-017: leave decision rows are append-only on update",
                "leave request decisions are append-only", (c, f) -> {
                    long leaveId = pendingLeave(c, f.internId, Date.valueOf("2026-03-01"), Date.valueOf("2026-03-01"));
                    long decisionId = leaveDecision(c, leaveId, "DECISION", "APPROVED", f.mentorId, null, null);
                    updateById(c, "leave_request_decisions", decisionId);
                }));
        probes.add(rejectsTrigger("DB-017: leave decision rows are append-only on delete",
                "leave request decisions are append-only", (c, f) -> {
                    long leaveId = pendingLeave(c, f.internId, Date.valueOf("2026-03-01"), Date.valueOf("2026-03-01"));
                    long decisionId = leaveDecision(c, leaveId, "DECISION", "APPROVED", f.mentorId, null, null);
                    deleteById(c, "leave_request_decisions", decisionId);
                }));
        probes.add(accepts("DB-017: an exception amendment records its outcome and reason", (c, f) -> {
            long exceptionId = attendanceException(c, f.attendanceId, "LATE_ARRIVAL", "REQUEST", "Reason", "PENDING");
            exceptionDecision(c, exceptionId, "AMENDMENT", "EXCUSED", f.mentorId, "Changed note", "Reason");
        }));
        probes.add(rejectsTrigger("DB-017: exception decision rows are append-only on update",
                "attendance exception decisions are append-only", (c, f) -> {
                    long exceptionId = attendanceException(c, f.attendanceId, "LATE_ARRIVAL", "REQUEST", "Reason", "PENDING");
                    long decisionId = exceptionDecision(c, exceptionId, "DECISION", "EXCUSED", f.mentorId, null, null);
                    updateById(c, "attendance_exception_decisions", decisionId);
                }));
        probes.add(rejectsTrigger("DB-017: exception decision rows are append-only on delete",
                "attendance exception decisions are append-only", (c, f) -> {
                    long exceptionId = attendanceException(c, f.attendanceId, "LATE_ARRIVAL", "REQUEST", "Reason", "PENDING");
                    long decisionId = exceptionDecision(c, exceptionId, "DECISION", "EXCUSED", f.mentorId, null, null);
                    deleteById(c, "attendance_exception_decisions", decisionId);
                }));

        probes.add(accepts("DB-020: a Task block transition needs no reason", (c, f) -> {
            taskTransition(c, f.taskId, "TODO", "BLOCKED", f.mentorId, null);
        }));
        probes.add(accepts("DB-020: a Task reopen carries its reason", (c, f) -> {
            taskTransition(c, f.taskId, "DONE", "IN_PROGRESS", f.mentorId, "Correct the result");
        }));
        probes.add(rejects("DB-020: a Task reopen requires a nonblank reason", CHECK_VIOLATION,
                "ck_task_status_transitions_reopen_reason", (c, f) -> taskTransition(
                        c, f.taskId, "DONE", "IN_PROGRESS", f.mentorId, "  ")));
        probes.add(rejectsTrigger("DB-020: Task transitions are append-only on update",
                "task status transitions are append-only", (c, f) -> {
                    long transitionId = taskTransition(c, f.taskId, "TODO", "BLOCKED", f.mentorId, null);
                    updateById(c, "task_status_transitions", transitionId);
                }));
        probes.add(rejectsTrigger("DB-020: Task transitions are append-only on delete",
                "task status transitions are append-only", (c, f) -> {
                    long transitionId = taskTransition(c, f.taskId, "TODO", "BLOCKED", f.mentorId, null);
                    deleteById(c, "task_status_transitions", transitionId);
                }));

        probes.add(accepts("DB-019: a cancelled PLANNED Project may have no activation time", (c, f) -> {
            assertRefusedInSavepoint(c, f, "ck_projects_activation", (connection, fixture) -> insert(connection,
                    "UPDATE projects SET activated_at=CURRENT_TIMESTAMP WHERE id=?", fixture.projectId));
            assertRefusedInSavepoint(c, f, "ck_projects_activation", (connection, fixture) -> insert(connection,
                    "UPDATE projects SET status='ACTIVE' WHERE id=?", fixture.projectId));
            projectCancellation(c, f.projectId, f.mentorId, null);
        }));
        probes.add(accepts("DB-019: a cancelled ACTIVE Project may retain its activation time", (c, f) ->
                projectCancellation(c, f.projectId, f.mentorId, Timestamp.from(Instant.parse("2026-01-01T00:00:00Z")))));
        probes.add(accepts("DB-018: an OVERDUE leave request is undecided", (c, f) -> {
            long id = pendingLeave(c, f.internId, Date.valueOf("2026-04-01"), Date.valueOf("2026-04-01"));
            insert(c, "UPDATE leave_requests SET status='OVERDUE' WHERE id=?", id);
        }));
        probes.add(accepts("DB-018: a WITHDRAWN leave stores withdrawn_at without a decision", (c, f) -> {
            long id = pendingLeave(c, f.internId, Date.valueOf("2026-04-01"), Date.valueOf("2026-04-01"));
            insert(c, "UPDATE leave_requests SET status='WITHDRAWN', withdrawn_at=CURRENT_TIMESTAMP WHERE id=?", id);
        }));
        probes.add(accepts("DB-018: a withdrawn approval mark retains its withdrawal time", (c, f) -> {
            long leaveId = pendingLeave(c, f.internId, Date.valueOf("2026-04-01"), Date.valueOf("2026-04-01"));
            insert(c, "UPDATE leave_requests SET status='APPROVED', decided_at=CURRENT_TIMESTAMP, "
                    + "decided_by_mentor_user_id=? WHERE id=?", f.mentorId, leaveId);
            insert(c, """
                    INSERT INTO leave_request_days
                      (leave_request_id, leave_date, quota_month, policy_version_id,
                       monthly_quota_snapshot, approval_withdrawn_at)
                    VALUES (?, DATE '2026-04-01', DATE '2026-04-01', ?, 3, CURRENT_TIMESTAMP)
                    """, leaveId, f.policyId);
        }));
        probes.add(accepts("DB-011: a revoked invitation accepts PROJECT_CANCELLED with no resolving actor",
                (c, f) -> invitation(c, f, "REVOKED", "PROJECT_CANCELLED", null)));
        probes.add(accepts("AC-DB-010: a WITHDRAWN range releases its overlap", (c, f) -> {
            long withdrawnId = pendingLeave(c, f.internId,
                    Date.valueOf("2026-05-01"), Date.valueOf("2026-05-02"));
            insert(c, "UPDATE leave_requests SET status='WITHDRAWN', withdrawn_at=CURRENT_TIMESTAMP WHERE id=?",
                    withdrawnId);
            pendingLeave(c, f.internId, Date.valueOf("2026-05-02"), Date.valueOf("2026-05-03"));
        }));
        probes.add(rejects("DB-018 and AC-DB-010: OVERDUE participates in the no-overlap predicate",
                "23P01", "ex_leave_requests_no_overlap", (c, f) -> {
                    insert(c, """
                            INSERT INTO leave_requests
                              (intern_user_id, start_date, end_date, reason, status, submitted_at,
                               first_counted_start_at)
                            VALUES (?, DATE '2026-05-01', DATE '2026-05-02', 'late review', 'OVERDUE',
                                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '1 second')
                            """, f.internId);
                    insert(c, """
                            INSERT INTO leave_requests
                              (intern_user_id, start_date, end_date, reason, status, submitted_at,
                               first_counted_start_at)
                            VALUES (?, DATE '2026-05-02', DATE '2026-05-03', 'overlap', 'PENDING',
                                    CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '1 second')
                            """, f.internId);
                }));

        probes.add(accepts("DB-018 and COR-007: an OVERDUE correction has no decision actor or time", (c, f) -> {
            long correctionId = pendingCorrection(c, f.attendanceId);
            insert(c, "UPDATE attendance_corrections SET status='OVERDUE' WHERE id=?", correctionId);
        }));
        probes.add(accepts("DB-017 and COR-007: correction history accepts an OVERDUE transition", (c, f) -> {
            long correctionId = pendingCorrection(c, f.attendanceId);
            insert(c, """
                    INSERT INTO attendance_correction_events
                      (correction_id, event_type, from_status, to_status, occurred_at)
                    VALUES (?, 'OVERDUE', 'PENDING', 'OVERDUE', CURRENT_TIMESTAMP)
                    """, correctionId);
        }));
        probes.add(accepts("DB-017: correction history accepts amendment and reversal kinds", (c, f) -> {
            long correctionId = pendingCorrection(c, f.attendanceId);
            insert(c, """
                    INSERT INTO attendance_correction_events
                      (correction_id, event_type, from_status, to_status, actor_user_id, note)
                    VALUES (?, 'AMENDED', 'APPROVED', 'APPROVED', ?, 'reason'),
                           (?, 'REVERSED', 'APPROVED', 'REJECTED', ?, 'reason')
                    """, correctionId, f.mentorId, correctionId, f.mentorId);
        }));
        probes.add(accepts("DB-022: a deactivated account may have no password or activation time", (c, f) -> {
            assertRefusedInSavepoint(c, f, "ck_app_users_pending_password", (connection, fixture) -> insert(connection,
                    """
                    INSERT INTO app_users (email, display_name, global_role, account_status, activated_at,
                                           password_hash, deactivated_at)
                    VALUES (?, 'Blank inactive', 'INTERN', 'DEACTIVATED', CURRENT_TIMESTAMP, '  ', CURRENT_TIMESTAMP)
                    """, "blank-inactive-" + UUID.randomUUID() + "@example.test"));
            insert(c, """
                    INSERT INTO app_users (email, display_name, global_role, account_status, activated_at,
                                           password_hash, deactivated_at)
                    VALUES (?, 'Inactive', 'INTERN', 'DEACTIVATED', NULL, NULL, CURRENT_TIMESTAMP)
                    """, "inactive-" + UUID.randomUUID() + "@example.test");
        }));
        probes.add(accepts("DB-022: a deactivated account may retain its lock beside activation", (c, f) -> {
            insert(c, """
                    INSERT INTO app_users (email, display_name, global_role, account_status, activated_at,
                                           locked_at, password_hash, deactivated_at)
                    VALUES (?, 'Inactive locked', 'INTERN', 'DEACTIVATED', CURRENT_TIMESTAMP,
                            CURRENT_TIMESTAMP, 'retained-hash', CURRENT_TIMESTAMP)
                    """, "inactive-locked-" + UUID.randomUUID() + "@example.test");
        }));
        probes.add(accepts("DB-021 and AC-DB-008: an Intern profile accepts a responsible Mentor", (c, f) ->
                insert(c, "UPDATE intern_profiles SET responsible_mentor_user_id=? WHERE user_id=?",
                        f.mentorId, f.internId)));
        probes.add(rejectsWithMessagePrefix(
                "DB-021 and AC-DB-008: an Admin cannot be a responsible Mentor", "23514",
                "responsible mentor must be a MENTOR account", (c, f) -> insert(c,
                        "UPDATE intern_profiles SET responsible_mentor_user_id=? WHERE user_id=?",
                        f.adminId, f.internId)));
        probes.add(rejectsWithMessagePrefix(
                "DB-021 and AC-DB-008: an Intern cannot be a responsible Mentor", "23514",
                "responsible mentor must be a MENTOR account", (c, f) -> insert(c,
                        "UPDATE intern_profiles SET responsible_mentor_user_id=? WHERE user_id=?",
                        f.internId, f.internId)));

        return probes.stream();
    }

    private DynamicTest accepts(String name, Probe probe) {
        return dynamicTest(name, () -> inRollback(probe));
    }

    private void assertRefusedInSavepoint(Connection connection, Fixture fixture, String expectedConstraint,
                                          Probe probe) throws Exception {
        Savepoint savepoint = connection.setSavepoint();
        PSQLException failure = null;
        try {
            probe.run(connection, fixture);
        } catch (Exception exception) {
            if (exception instanceof SQLException sqlException) {
                failure = postgresException(sqlException);
            } else {
                throw exception;
            }
        } finally {
            connection.rollback(savepoint);
            connection.releaseSavepoint(savepoint);
        }
        assertThat((Object) failure).as("legacy row must be refused before the V3 row is tried").isNotNull();
        if (!CHECK_VIOLATION.equals(failure.getSQLState())
                || !expectedConstraint.equals(serverError(failure).getConstraint())) {
            throw failure;
        }
        assertThat(failure.getSQLState()).isEqualTo(CHECK_VIOLATION);
        assertThat(serverError(failure).getConstraint()).isEqualTo(expectedConstraint);
    }

    private DynamicTest rejects(String name, String sqlState, String constraint, Probe probe) {
        return dynamicTest(name, () -> inRollback((connection, fixture) -> {
            PSQLException failure = null;
            try {
                probe.run(connection, fixture);
            } catch (SQLException exception) {
                failure = postgresException(exception);
            }
            assertThat((Object) failure).as("probe must reach its named PostgreSQL refusal").isNotNull();
            if (!sqlState.equals(failure.getSQLState())
                    || (constraint != null && !constraint.equals(serverError(failure).getConstraint()))) {
                throw failure;
            }
            assertThat(failure.getSQLState()).as("SQLSTATE for " + name).isEqualTo(sqlState);
            if (constraint != null) {
                assertThat(serverError(failure).getConstraint()).as("constraint for " + name)
                        .isEqualTo(constraint);
            }
        }));
    }

    private DynamicTest rejectsTrigger(String name, String expectedMessage, Probe probe) {
        return dynamicTest(name, () -> inRollback((connection, fixture) -> {
            PSQLException failure = null;
            try {
                probe.run(connection, fixture);
            } catch (SQLException exception) {
                failure = postgresException(exception);
            }
            assertThat((Object) failure).as("append-only probe must reach its trigger").isNotNull();
            if (!"P0001".equals(failure.getSQLState())
                    || !expectedMessage.equals(serverError(failure).getMessage())) {
                throw failure;
            }
            assertThat(failure.getSQLState()).as("append-only SQLSTATE").isEqualTo("P0001");
            assertThat(serverError(failure).getMessage()).as("trigger message").isEqualTo(expectedMessage);
        }));
    }

    private DynamicTest rejectsWithMessagePrefix(String name, String sqlState, String messagePrefix, Probe probe) {
        return dynamicTest(name, () -> inRollback((connection, fixture) -> {
            PSQLException failure = null;
            try {
                probe.run(connection, fixture);
            } catch (SQLException exception) {
                failure = postgresException(exception);
            }
            assertThat((Object) failure).as("probe must reach its role-specific PostgreSQL refusal").isNotNull();
            if (!sqlState.equals(failure.getSQLState())
                    || !serverError(failure).getMessage().startsWith(messagePrefix)) {
                throw failure;
            }
            assertThat(failure.getSQLState()).as("SQLSTATE for " + name).isEqualTo(sqlState);
            assertThat(serverError(failure).getMessage()).as("message prefix for " + name)
                    .startsWith(messagePrefix);
        }));
    }

    private void inRollback(Probe probe) throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                Fixture fixture = seedFixture(connection);
                probe.run(connection, fixture);
            } finally {
                connection.rollback();
            }
        }
    }

    private static DiagramContract diagramContract() throws Exception {
        String specification = Files.readString(Path.of(
                ".sdd/specs/platform/features/data-model/SPEC.md"));
        int section = specification.indexOf("#### §19.4 Physical database diagram");
        int diagramStart = specification.indexOf("erDiagram", section);
        int diagramEnd = specification.indexOf("```", diagramStart);
        if (section < 0 || diagramStart < 0 || diagramEnd < 0) {
            throw new IllegalStateException("Could not find the §19.4 Mermaid diagram");
        }
        String diagram = specification.substring(diagramStart, diagramEnd);
        Set<String> tables = new HashSet<>();
        Matcher entities = DIAGRAM_ENTITY.matcher(diagram);
        while (entities.find()) {
            tables.add(entities.group(1));
        }
        Set<String> foreignKeys = new HashSet<>();
        Matcher relations = DIAGRAM_FOREIGN_KEY.matcher(diagram);
        while (relations.find()) {
            foreignKeys.add(relations.group(2));
        }
        return new DiagramContract(Set.copyOf(tables), Set.copyOf(foreignKeys));
    }

    private static Set<String> baselineForeignKeys() throws Exception {
        String baseline = Files.readString(Path.of("src/main/resources/db/migration/V1__baseline.sql"));
        Set<String> foreignKeys = new HashSet<>();
        Matcher constraints = MIGRATION_FOREIGN_KEY.matcher(baseline);
        while (constraints.find()) {
            foreignKeys.add(constraints.group(1));
        }
        return Set.copyOf(foreignKeys);
    }

    private static Fixture seedFixture(Connection connection) throws SQLException {
        long adminId = user(connection, "ADMIN");
        long mentorId = user(connection, "MENTOR");
        long internId = user(connection, "INTERN");
        insert(connection, """
                INSERT INTO intern_profiles (user_id, student_code, internship_start_date, internship_end_date)
                VALUES (?, ?, CURRENT_DATE - 100, CURRENT_DATE + 300)
                """, internId, "student-" + UUID.randomUUID());
        long policyId = scalarLong(connection,
                "SELECT id FROM attendance_policy_versions WHERE effective_from = DATE '1970-01-01'");
        long projectId = generatedId(connection, """
                INSERT INTO projects (mentor_user_id, name, start_date, end_date)
                VALUES (?, ?, CURRENT_DATE - 10, CURRENT_DATE + 365) RETURNING id
                """, mentorId, "probe-" + UUID.randomUUID());
        long membershipId = generatedId(connection, """
                INSERT INTO project_memberships (project_id, intern_user_id, added_by_user_id)
                VALUES (?, ?, ?) RETURNING id
                """, projectId, internId, mentorId);
        generatedId(connection, """
                INSERT INTO project_leadership_terms (project_id, membership_id, appointed_by_mentor_user_id)
                VALUES (?, ?, ?) RETURNING id
                """, projectId, membershipId, mentorId);
        long taskId = generatedId(connection, """
                INSERT INTO tasks (project_id, assignee_membership_id, title,
                                   created_by_membership_id, assigned_by_membership_id)
                VALUES (?, ?, 'Probe task', ?, ?) RETURNING id
                """, projectId, membershipId, membershipId, membershipId);
        long attendanceId = generatedId(connection, """
                INSERT INTO attendance_records (intern_user_id, work_date, policy_version_id, check_in_at)
                VALUES (?, CURRENT_DATE - 1, ?, CURRENT_TIMESTAMP - INTERVAL '8 hours') RETURNING id
                """, internId, policyId);
        return new Fixture(adminId, mentorId, internId, policyId, projectId, membershipId, taskId, attendanceId);
    }

    private static long user(Connection connection, String role) throws SQLException {
        String email = role.toLowerCase() + "-" + UUID.randomUUID() + "@example.test";
        return generatedId(connection, """
                INSERT INTO app_users (email, display_name, password_hash, global_role, account_status, activated_at)
                VALUES (?, ?, 'probe-hash', ?, 'ACTIVE', CURRENT_TIMESTAMP) RETURNING id
                """, email, role, role);
    }

    private static long period(Connection connection, long internId, Date month, String status,
                               Timestamp finalizedAt) throws SQLException {
        return generatedId(connection, """
                INSERT INTO attendance_periods (intern_user_id, period_month, status, finalized_at)
                VALUES (?, ?, ?, ?) RETURNING id
                """, internId, month, status, finalizedAt);
    }

    private static long reopen(Connection connection, long periodId, long requesterId, Date start, Date end,
                               String reason, String status, Long adminId, Timestamp decidedAt,
                               String rejectionReason) throws SQLException {
        return generatedId(connection, """
                INSERT INTO attendance_period_reopens
                  (attendance_period_id, requester_user_id, start_date, end_date, reason, requested_at,
                   status, decided_by_admin_user_id, decided_at, rejection_reason)
                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?, ?, ?, ?) RETURNING id
                """, periodId, requesterId, start, end, reason, status, adminId, decidedAt, rejectionReason);
    }

    private static long attendanceException(Connection connection, long attendanceId, String kind,
                                            String source, String reason, String status) throws SQLException {
        return generatedId(connection, """
                INSERT INTO attendance_exceptions
                  (attendance_record_id, violation_kind, source, reason, submitted_at,
                   submission_deadline, decision_deadline, status)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP + INTERVAL '48 hours',
                        CURRENT_TIMESTAMP + INTERVAL '96 hours', ?) RETURNING id
                """, attendanceId, kind, source, reason, status);
    }

    private static long pendingLeave(Connection connection, long internId, Date start, Date end)
            throws SQLException {
        return generatedId(connection, """
                INSERT INTO leave_requests
                  (intern_user_id, start_date, end_date, reason, status, submitted_at, first_counted_start_at)
                VALUES (?, ?, ?, 'Probe request', 'PENDING', CURRENT_TIMESTAMP,
                        CURRENT_TIMESTAMP + INTERVAL '1 second') RETURNING id
                """, internId, start, end);
    }

    private static long leaveDecision(Connection connection, long leaveId, String kind, String outcome,
                                      long actorId, String note, String reason) throws SQLException {
        return generatedId(connection, """
                INSERT INTO leave_request_decisions
                  (leave_request_id, decision_kind, outcome, decision_note, actor_user_id, occurred_at, reason)
                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?) RETURNING id
                """, leaveId, kind, outcome, note, actorId, reason);
    }

    private static long exceptionDecision(Connection connection, long exceptionId, String kind, String outcome,
                                          long actorId, String note, String reason) throws SQLException {
        return generatedId(connection, """
                INSERT INTO attendance_exception_decisions
                  (attendance_exception_id, decision_kind, outcome, decision_note, actor_user_id, occurred_at, reason)
                VALUES (?, ?, ?, ?, ?, CURRENT_TIMESTAMP, ?) RETURNING id
                """, exceptionId, kind, outcome, note, actorId, reason);
    }

    private static long pendingCorrection(Connection connection, long attendanceId) throws SQLException {
        return generatedId(connection, """
                INSERT INTO attendance_corrections
                  (attendance_record_id, requested_checkout_at, reason, submitted_at,
                   submission_deadline, decision_deadline)
                VALUES (?, CURRENT_TIMESTAMP, 'Probe correction', CURRENT_TIMESTAMP,
                        CURRENT_TIMESTAMP + INTERVAL '48 hours', CURRENT_TIMESTAMP + INTERVAL '96 hours')
                RETURNING id
                """, attendanceId);
    }

    private static long taskTransition(Connection connection, long taskId, String from, String to,
                                       long actorId, String reason) throws SQLException {
        return generatedId(connection, """
                INSERT INTO task_status_transitions (task_id, from_status, to_status, actor_user_id, occurred_at, reason)
                VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP, ?) RETURNING id
                """, taskId, from, to, actorId, reason);
    }

    private static void projectCancellation(Connection connection, long projectId, long mentorId,
                                           Timestamp activatedAt) throws SQLException {
        insert(connection, """
                UPDATE projects SET status='CANCELLED', activated_at=?, cancelled_by_mentor_user_id=?,
                                    cancelled_at=CURRENT_TIMESTAMP, cancellation_reason='Scope changed'
                WHERE id=?
                """, activatedAt, mentorId, projectId);
    }

    private static void invitation(Connection connection, Fixture f, String status, String resolution, Long actorId)
            throws SQLException {
        insert(connection, """
                INSERT INTO project_invitations
                  (project_id, invited_intern_user_id, issuing_leadership_term_id, status,
                   resolved_at, resolved_by_user_id, resolution_code)
                SELECT ?, ?, id, ?, CURRENT_TIMESTAMP, ?, ? FROM project_leadership_terms WHERE project_id=?
                """, f.projectId, f.internId, status, actorId, resolution, f.projectId);
    }

    private static long generatedId(Connection connection, String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, values);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) {
                    throw new SQLException("INSERT RETURNING produced no row");
                }
                return rows.getLong(1);
            }
        }
    }

    private static int insert(Connection connection, String sql, Object... values) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            bind(statement, values);
            return statement.executeUpdate();
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

    /**
     * Exercises an UPDATE against the append-only tables without touching their generated identity.
     * Protects {@code DB-017} and {@code DB-020}: PostgreSQL must reach the table's refusal trigger,
     * rather than reject assignment to a {@code GENERATED ALWAYS} key before the trigger runs.
     */
    private static void updateById(Connection connection, String table, long id) throws SQLException {
        insert(connection, "UPDATE " + table + " SET occurred_at=occurred_at WHERE id=?", id);
    }

    private static void deleteById(Connection connection, String table, long id) throws SQLException {
        insert(connection, "DELETE FROM " + table + " WHERE id=?", id);
    }

    private static PSQLException postgresException(SQLException exception) {
        SQLException current = exception;
        while (current != null) {
            if (current instanceof PSQLException postgres) {
                return postgres;
            }
            current = current.getNextException();
        }
        throw new AssertionError("Expected PostgreSQL exception", exception);
    }

    private static ServerErrorMessage serverError(PSQLException exception) {
        return exception.getServerErrorMessage();
    }

    @FunctionalInterface
    private interface Probe {
        void run(Connection connection, Fixture fixture) throws Exception;
    }

    private record DiagramContract(Set<String> tables, Set<String> foreignKeys) {}

    private record Fixture(long adminId, long mentorId, long internId, long policyId, long projectId,
                           long membershipId, long taskId, long attendanceId) {}
}
