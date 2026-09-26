package com.lab.labtimesheet.feature.identity.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.TimeZone;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Verifies that the identity contract migration accepts account rows lawful under V1-V3 unchanged.
 *
 * <p>Protects {@code DB-022}, {@code AC-DB-009}, {@code ACC-016}, and {@code D38}: the migration
 * must preserve pending, active, locked, deactivated-active, and legacy deactivated rows whose lock
 * was already cleared. The pre-V4 RED is the asserted Flyway version remaining at 3.
 */
@Testcontainers(disabledWithoutDocker = true)
class AccountContractIntegrationTest {

    @Container
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:18.4"));

    @Test
    void v4PreservesEveryAccountShapeWritableByV1ThroughV3() throws Exception {
        TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        try {
            Flyway v3 = configuration().cleanDisabled(false).target("3").load();
            v3.clean();
            v3.migrate();

            List<Long> ids = new ArrayList<>();
            try (Connection connection = connect()) {
                ids.add(account(connection, "PENDING_ACTIVATION", null, null, null, null));
                ids.add(account(connection, "ACTIVE", "{noop}active-password", NOW, null, null));
                ids.add(account(connection, "LOCKED", "{noop}locked-password", NOW, NOW, null));
                ids.add(account(connection, "DEACTIVATED", "{noop}active-password", NOW, null, NOW));
                ids.add(account(connection, "DEACTIVATED", "{noop}legacy-locked-password", NOW, null, NOW));
            }

            List<AccountRow> before = rows(ids);
            Flyway all = configuration().load();
            all.migrate();
            assertThat(all.info().current().getVersion().getVersion()).as("V4 is applied").isEqualTo("4");
            assertThat(rows(ids)).as("migration preserves every pre-V4 row, including a lost historical lock")
                    .containsExactlyElementsOf(before);
        } finally {
            TimeZone.setDefault(original);
        }
    }

    /**
     * Protects DB-022 and AC-DB-009. The trigger must refuse timestamp-only activated_at rewrites and lock
     * changes carried into DEACTIVATED, including DEACTIVATED to ACTIVE with its lock cleared and
     * DEACTIVATED to LOCKED while setting a new lock. The CHECK constraints separately refuse unlawful
     * stored timestamp/status combinations, including clearing activation from ACTIVE or setting a lock
     * on DEACTIVATED without a status transition.
     */
    @Test
    void schemaRefusesIdentityTimestampRewritesOutsideTheirTransitions() throws Exception {
        TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        try {
            Flyway v3 = configuration().cleanDisabled(false).target("3").load();
            v3.clean();
            v3.migrate();
            configuration().load().migrate();

            long pending;
            long active;
            long locked;
            long deactivated;
            long deactivatedWithoutLock;
            try (Connection connection = connect()) {
                pending = account(connection, "PENDING_ACTIVATION", null, null, null, null);
                active = account(connection, "ACTIVE", "{noop}active-password", NOW, null, null);
                locked = account(connection, "LOCKED", "{noop}locked-password", NOW, NOW, null);
                deactivated = account(connection, "DEACTIVATED", "{noop}deactivated-password", NOW, NOW, NOW);
                deactivatedWithoutLock = account(connection, "DEACTIVATED", "{noop}legacy-password", NOW,
                        null, NOW);
            }

            SoftAssertions triggerAssertions = new SoftAssertions();
            refuseByTrigger(triggerAssertions,
                    "UPDATE app_users SET account_status='ACTIVE', locked_at=NULL, deactivated_at=NULL WHERE id=?",
                    deactivated, "locked_at may change only");
            refuseByTrigger(triggerAssertions,
                    "UPDATE app_users SET account_status='LOCKED', locked_at=?, deactivated_at=NULL WHERE id=?",
                    NOW, deactivatedWithoutLock, "locked_at may change only");
            triggerAssertions.assertAll();
            refuseByTrigger("UPDATE app_users SET activated_at=? WHERE id=?", NOW.plusSeconds(1), pending,
                    "activated_at may change only");
            refuse("UPDATE app_users SET activated_at=NULL WHERE id=?", active);
            refuseByTrigger("UPDATE app_users SET activated_at=? WHERE id=?", NOW.plusSeconds(1), active,
                    "activated_at may change only");
            refuse("UPDATE app_users SET activated_at=NULL WHERE id=?", locked);
            refuseByTrigger("UPDATE app_users SET activated_at=? WHERE id=?", NOW.plusSeconds(1), locked,
                    "activated_at may change only");
            refuse("UPDATE app_users SET activated_at=NULL WHERE id=?", deactivated);
            refuseByTrigger("UPDATE app_users SET activated_at=? WHERE id=?", NOW.plusSeconds(1), deactivated,
                    "activated_at may change only");
            refuseByTrigger("UPDATE app_users SET locked_at=? WHERE id=?", NOW, deactivatedWithoutLock,
                    "locked_at may change only");
            refuseByTrigger("UPDATE app_users SET locked_at=NULL WHERE id=?", deactivated,
                    "locked_at may change only");
            refuseByTrigger("UPDATE app_users SET locked_at=? WHERE id=?", NOW.plusSeconds(1), deactivated,
                    "locked_at may change only");

            execute("UPDATE app_users SET activated_at=activated_at WHERE id=?", active);
            execute("UPDATE app_users SET locked_at=locked_at WHERE id=?", deactivated);
        } finally {
            TimeZone.setDefault(original);
        }
    }

    /**
     * Protects {@code DB-022} and {@code AC-DB-009}: every status is written directly with every
     * combination of password hash (none, blank, non-blank), activation, lock and deactivation
     * timestamps. Hand-derived from {@code DB-022}, exactly six of the 96 rows are lawful: pending with
     * nothing; active with hash and activation; locked with hash, activation and lock; deactivated
     * with deactivation and either nothing else or hash and activation, the latter with or without a
     * lock. Every other row must be refused with {@code 23514}. Before V4, V3's relaxed checks admit
     * rows this matrix refuses, which is the observable RED.
     */
    @Test
    void schemaAcceptsExactlyTheLawfulAccountRowShapes() throws Exception {
        TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        try {
            Flyway v3 = configuration().cleanDisabled(false).target("3").load();
            v3.clean();
            v3.migrate();
            configuration().load().migrate();

            int lawful = 0;
            List<String> mismatches = new ArrayList<>();
            try (Connection connection = connect()) {
                for (String status : new String[] {"PENDING_ACTIVATION", "ACTIVE", "LOCKED", "DEACTIVATED"}) {
                    for (String hash : new String[] {null, "  ", "{noop}matrix-password"}) {
                        for (Instant activated : new Instant[] {null, NOW}) {
                            for (Instant locked : new Instant[] {null, NOW}) {
                                for (Instant deactivated : new Instant[] {null, NOW}) {
                                    boolean expected = lawfulShape(status, hash, activated, locked, deactivated);
                                    String refusal = null;
                                    try {
                                        account(connection, status, hash, activated, locked, deactivated);
                                    } catch (SQLException exception) {
                                        refusal = exception.getSQLState();
                                    }
                                    if (expected) {
                                        lawful++;
                                    }
                                    boolean acceptedAsExpected = expected && refusal == null;
                                    boolean refusedAsExpected = !expected && "23514".equals(refusal);
                                    if (!acceptedAsExpected && !refusedAsExpected) {
                                        mismatches.add(status + " hash=" + hash + " activated=" + activated
                                                + " locked=" + locked + " deactivated=" + deactivated
                                                + " refusal=" + refusal);
                                    }
                                }
                            }
                        }
                    }
                }
            }
            assertThat(lawful).as("DB-022 hand count of lawful shapes").isEqualTo(6);
            assertThat(mismatches).as("rows accepted or refused against DB-022").isEmpty();
        } finally {
            TimeZone.setDefault(original);
        }
    }

    /**
     * Protects {@code DB-022}, {@code ACC-014}, {@code ACC-016}, {@code ACC-028} and {@code ACC-029}:
     * each of the nine transitions of ACC-014 commits under V4's timestamp trigger: PENDING_ACTIVATION to
     * ACTIVE, ACTIVE to LOCKED, LOCKED to ACTIVE, ACTIVE to DEACTIVATED, LOCKED to DEACTIVATED,
     * PENDING_ACTIVATION to DEACTIVATED, DEACTIVATED to ACTIVE, DEACTIVATED to LOCKED, and
     * DEACTIVATED to PENDING_ACTIVATION. This guard detects an overbroad trigger that rejects any lawful edge.
     * Updates name {@code locked_at} and {@code activated_at} unchanged where the application's full
     * entity update would, so the trigger fires as it does in production.
     */
    @Test
    void schemaCommitsEveryPermittedTransition() throws Exception {
        TimeZone original = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        try {
            Flyway v3 = configuration().cleanDisabled(false).target("3").load();
            v3.clean();
            v3.migrate();
            configuration().load().migrate();

            long lifecycle;
            long neverActivated;
            long activatedUnlocked;
            try (Connection connection = connect()) {
                lifecycle = account(connection, "PENDING_ACTIVATION", null, null, null, null);
                neverActivated = account(connection, "PENDING_ACTIVATION", null, null, null, null);
                activatedUnlocked = account(connection, "PENDING_ACTIVATION", null, null, null, null);
            }
            String later = "TIMESTAMPTZ '2026-09-26 13:00:00+00'";
            execute("UPDATE app_users SET account_status='ACTIVE', password_hash='{noop}p', activated_at="
                    + later + ", locked_at=locked_at WHERE id=?", lifecycle);
            execute("UPDATE app_users SET account_status='LOCKED', locked_at=" + later
                    + ", activated_at=activated_at WHERE id=?", lifecycle);
            execute("UPDATE app_users SET account_status='ACTIVE', locked_at=NULL, activated_at=activated_at"
                    + " WHERE id=?", lifecycle);
            execute("UPDATE app_users SET account_status='LOCKED', locked_at=" + later
                    + ", activated_at=activated_at WHERE id=?", lifecycle);
            execute("UPDATE app_users SET account_status='DEACTIVATED', deactivated_at=" + later
                    + ", locked_at=locked_at, activated_at=activated_at WHERE id=?", lifecycle);
            execute("UPDATE app_users SET account_status='ACTIVE', password_hash='{noop}p', activated_at="
                    + later + ", locked_at=locked_at WHERE id=?", activatedUnlocked);
            execute("UPDATE app_users SET account_status='DEACTIVATED', deactivated_at=" + later
                    + ", locked_at=locked_at, activated_at=activated_at WHERE id=?", activatedUnlocked);
            execute("UPDATE app_users SET account_status='ACTIVE', deactivated_at=NULL, locked_at=locked_at,"
                    + " activated_at=activated_at WHERE id=?", activatedUnlocked);
            execute("UPDATE app_users SET account_status='LOCKED', deactivated_at=NULL, locked_at=locked_at,"
                    + " activated_at=activated_at WHERE id=?", lifecycle);
            execute("UPDATE app_users SET account_status='DEACTIVATED', deactivated_at=" + later
                    + ", locked_at=locked_at, activated_at=activated_at WHERE id=?", neverActivated);
            execute("UPDATE app_users SET account_status='PENDING_ACTIVATION', deactivated_at=NULL,"
                    + " locked_at=locked_at, activated_at=activated_at WHERE id=?", neverActivated);
        } finally {
            TimeZone.setDefault(original);
        }
    }

    private static boolean lawfulShape(String status, String hash, Instant activated, Instant locked,
                                       Instant deactivated) {
        boolean usableHash = hash != null && !hash.isBlank();
        return switch (status) {
            case "PENDING_ACTIVATION" -> hash == null && activated == null && locked == null && deactivated == null;
            case "ACTIVE" -> usableHash && activated != null && locked == null && deactivated == null;
            case "LOCKED" -> usableHash && activated != null && locked != null && deactivated == null;
            case "DEACTIVATED" -> deactivated != null
                    && ((hash == null && activated == null && locked == null) || (usableHash && activated != null));
            default -> false;
        };
    }

    private static final Instant NOW = Instant.parse("2026-09-26T12:00:00Z");

    private static FluentConfiguration configuration() {
        return Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration");
    }

    private static Connection connect() throws Exception {
        return java.sql.DriverManager.getConnection(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static long account(Connection connection, String status, String hash, Instant activated,
                                Instant locked, Instant deactivated) throws Exception {
        try (PreparedStatement insert = connection.prepareStatement("""
                INSERT INTO app_users (email, display_name, password_hash, global_role, account_status,
                                       activated_at, locked_at, deactivated_at)
                VALUES (?, ?, ?, 'INTERN', ?, ?, ?, ?)
                RETURNING id
                """)) {
            String unique = UUID.randomUUID().toString();
            insert.setString(1, "contract-" + unique + "@example.test");
            insert.setString(2, "Contract " + unique);
            insert.setString(3, hash);
            insert.setString(4, status);
            timestamp(insert, 5, activated);
            timestamp(insert, 6, locked);
            timestamp(insert, 7, deactivated);
            try (ResultSet result = insert.executeQuery()) {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private static void timestamp(PreparedStatement statement, int parameter, Instant value) throws Exception {
        if (value == null) {
            statement.setTimestamp(parameter, null);
        } else {
            statement.setTimestamp(parameter, Timestamp.from(value));
        }
    }

    private static List<AccountRow> rows(List<Long> ids) throws Exception {
        List<AccountRow> result = new ArrayList<>();
        try (Connection connection = connect(); PreparedStatement query = connection.prepareStatement("""
                SELECT id, account_status, password_hash, activated_at, locked_at, deactivated_at
                FROM app_users WHERE id = ANY (?) ORDER BY id
                """)) {
            query.setArray(1, connection.createArrayOf("bigint", ids.toArray()));
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) {
                    result.add(new AccountRow(rows.getLong("id"), rows.getString("account_status"),
                            rows.getString("password_hash"), instant(rows, "activated_at"),
                            instant(rows, "locked_at"), instant(rows, "deactivated_at")));
                }
            }
        }
        return result;
    }

    private static void refuse(String sql, Object value, long id) throws Exception {
        SQLException failure = null;
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            if (value instanceof Instant instant) {
                statement.setTimestamp(1, Timestamp.from(instant));
                statement.setLong(2, id);
            } else {
                statement.setLong(1, id);
            }
            statement.executeUpdate();
        } catch (SQLException exception) {
            failure = exception;
        }
        assertThat((Object) failure).as("DB-022 refuses unlawful timestamp rewrite: %s", sql).isNotNull();
        assertThat(failure.getSQLState()).isEqualTo("23514");
    }

    private static void refuse(String sql, long id) throws Exception {
        SQLException failure = null;
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.executeUpdate();
        } catch (SQLException exception) {
            failure = exception;
        }
        assertThat((Object) failure).as("DB-022 refuses unlawful timestamp rewrite: %s", sql).isNotNull();
        assertThat(failure.getSQLState()).isEqualTo("23514");
    }

    private static void refuseByTrigger(String sql, Object value, long id, String messageFragment) throws Exception {
        SQLException failure = null;
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            if (value instanceof Instant instant) {
                statement.setTimestamp(1, Timestamp.from(instant));
                statement.setLong(2, id);
            } else {
                statement.setLong(1, id);
            }
            statement.executeUpdate();
        } catch (SQLException exception) {
            failure = exception;
        }
        assertThat((Object) failure).as("DB-022 trigger refuses timestamp rewrite: %s", sql).isNotNull();
        assertThat(failure.getSQLState()).isEqualTo("23514");
        assertThat(failure.getMessage()).contains(messageFragment);
    }

    private static void refuseByTrigger(String sql, long id, String messageFragment) throws Exception {
        SQLException failure = null;
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.executeUpdate();
        } catch (SQLException exception) {
            failure = exception;
        }
        assertThat((Object) failure).as("DB-022 trigger refuses timestamp rewrite: %s", sql).isNotNull();
        assertThat(failure.getSQLState()).isEqualTo("23514");
        assertThat(failure.getMessage()).contains(messageFragment);
    }

    private static void refuseByTrigger(SoftAssertions softly, String sql, Object value, long id,
                                        String messageFragment) throws Exception {
        SQLException failure = null;
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            if (value instanceof Instant instant) {
                statement.setTimestamp(1, Timestamp.from(instant));
                statement.setLong(2, id);
            } else {
                statement.setLong(1, id);
            }
            statement.executeUpdate();
        } catch (SQLException exception) {
            failure = exception;
        }
        softly.assertThat((Object) failure).as("DB-022 trigger refuses timestamp rewrite: %s", sql).isNotNull();
        if (failure != null) {
            softly.assertThat(failure.getSQLState()).isEqualTo("23514");
            softly.assertThat(failure.getMessage()).contains(messageFragment);
        }
    }

    private static void refuseByTrigger(SoftAssertions softly, String sql, long id, String messageFragment)
            throws Exception {
        SQLException failure = null;
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            statement.executeUpdate();
        } catch (SQLException exception) {
            failure = exception;
        }
        softly.assertThat((Object) failure).as("DB-022 trigger refuses timestamp rewrite: %s", sql).isNotNull();
        if (failure != null) {
            softly.assertThat(failure.getSQLState()).isEqualTo("23514");
            softly.assertThat(failure.getMessage()).contains(messageFragment);
        }
    }

    private static void execute(String sql, long id) throws Exception {
        try (Connection connection = connect(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, id);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private static Instant instant(ResultSet rows, String column) throws Exception {
        Timestamp value = rows.getTimestamp(column);
        return value == null ? null : value.toInstant();
    }

    private record AccountRow(long id, String status, String passwordHash, Instant activatedAt,
                              Instant lockedAt, Instant deactivatedAt) { }
}
