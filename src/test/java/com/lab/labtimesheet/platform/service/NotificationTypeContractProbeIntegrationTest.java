package com.lab.labtimesheet.platform.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.notification.model.NotificationType;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** Keeps every stored notification family aligned with PostgreSQL's named check constraint. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class NotificationTypeContractProbeIntegrationTest {

    @Autowired
    private DataSource dataSource;

    /**
     * Protects {@code NOT-002} and the enum/schema contract: an enum family rejected by PostgreSQL cannot be published.
     * Observable break: a newly designated attendance exception family is absent from the schema predicate. Expected:
     * every {@link NotificationType} inserts successfully, including both exception families, before rollback.
     */
    @Test
    void everyNotificationTypeIsAcceptedByPostgreSql() throws Exception {
        List<String> rejected = new ArrayList<>();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try {
                long recipientId = insertRecipient(connection);
                for (NotificationType type : NotificationType.values()) {
                    Savepoint savepoint = connection.setSavepoint();
                    try (PreparedStatement statement = connection.prepareStatement("""
                            INSERT INTO notifications (recipient_user_id, notification_type, title, body)
                            VALUES (?, ?, 'Contract probe', 'Probe body')
                            """)) {
                        statement.setLong(1, recipientId);
                        statement.setString(2, type.name());
                        statement.executeUpdate();
                    } catch (Exception rejectedType) {
                        rejected.add(type.name());
                        connection.rollback(savepoint);
                    } finally {
                        connection.releaseSavepoint(savepoint);
                    }
                }
            } finally {
                connection.rollback();
            }
        }
        assertThat(rejected).as("notification families rejected by ck_notifications_type").isEmpty();
    }

    private static long insertRecipient(Connection connection) throws Exception {
        String email = "notification-contract-" + UUID.randomUUID() + "@example.test";
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO app_users (email, display_name, password_hash, global_role, account_status, activated_at)
                VALUES (?, 'Probe recipient', '{noop}probe-password', 'ADMIN', 'ACTIVE', now()) RETURNING id
                """)) {
            statement.setString(1, email);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }
}
