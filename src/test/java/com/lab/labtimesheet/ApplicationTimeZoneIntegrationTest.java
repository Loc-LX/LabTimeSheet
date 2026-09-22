package com.lab.labtimesheet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mockStatic;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.TimeZone;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Keeps a Windows machine whose JVM reports the legacy {@code Asia/Saigon} alias able to reach
 * PostgreSQL, both when the application starts and when a Spring Boot test context starts.
 *
 * <p>Protects {@code ARC-003}, which requires PostgreSQL for development and integration testing,
 * and {@code GOV-011}, which takes business dates from the attendance policy version rather than
 * from a timezone forced onto the JVM. Decision {@code D19} requires the suite to pass on Windows
 * without a timezone flag.
 */
class ApplicationTimeZoneIntegrationTest {

    /**
     * Protects {@code ARC-003}. Observable break: the development application started on Windows
     * sends {@code Asia/Saigon} to PostgreSQL and fails to open its first connection. Expected:
     * after {@code main} the JVM default is {@code Asia/Ho_Chi_Minh}, the canonical name of the same
     * zone, and Spring is started with the original arguments.
     */
    @Test
    void mainCanonicalizesLegacyAliasBeforeStartingSpring() {
        TimeZone originalTimeZone = TimeZone.getDefault();
        String[] args = {"--spring.profiles.active=test"};

        try (MockedStatic<SpringApplication> springApplication = mockStatic(SpringApplication.class)) {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Saigon"));

            LabtimesheetApplication.main(args);

            assertThat(TimeZone.getDefault().getID()).isEqualTo("Asia/Ho_Chi_Minh");
            springApplication.verify(() -> SpringApplication.run(LabtimesheetApplication.class, args));
        } finally {
            TimeZone.setDefault(originalTimeZone);
        }
    }

    /**
     * Protects {@code ARC-003}. Observable break: PostgreSQL 18.4 refuses a session whose timezone is
     * {@code Asia/Saigon}. Expected: the connection fails with that alias in the message, and
     * succeeds once the alias is canonicalized.
     *
     * @throws SQLException if the canonicalized connection cannot be opened
     */
    @Test
    void canonicalizesLegacyVietnamAliasBeforePostgresConnects() throws SQLException {
        TimeZone originalTimeZone = TimeZone.getDefault();

        try (PostgreSQLContainer postgres =
                new PostgreSQLContainer(DockerImageName.parse("postgres:18.4"))) {
            postgres.start();
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Saigon"));

            assertThat(TimeZone.getDefault().getID()).isEqualTo("Asia/Saigon");
            assertThatThrownBy(() -> openConnection(postgres))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("Asia/Saigon");

            LabtimesheetApplication.normalizeDefaultTimeZone();

            assertThat(TimeZone.getDefault().getID()).isEqualTo("Asia/Ho_Chi_Minh");
            try (Connection connection = openConnection(postgres)) {
                assertThat(connection.isValid(1)).isTrue();
            }
        } finally {
            TimeZone.setDefault(originalTimeZone);
        }
    }

    /**
     * Protects {@code D19} for {@code ARC-003}. Observable break: a Spring Boot test context never
     * passes through {@code main}, so on Windows every integration test sends {@code Asia/Saigon}
     * and fails unless the suite is started with {@code -Duser.timezone}. Expected: starting a
     * Spring Boot context alone turns the alias into {@code Asia/Ho_Chi_Minh}.
     */
    @Test
    void springBootContextCanonicalizesLegacyAliasWithoutPassingThroughMain() {
        TimeZone originalTimeZone = TimeZone.getDefault();

        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Saigon"));
            SpringApplication application = new SpringApplication(EmptyContext.class);
            application.setWebApplicationType(WebApplicationType.NONE);

            try (ConfigurableApplicationContext ignored = application.run()) {
                assertThat(TimeZone.getDefault().getID()).isEqualTo("Asia/Ho_Chi_Minh");
            }
        } finally {
            TimeZone.setDefault(originalTimeZone);
        }
    }

    /**
     * Protects {@code GOV-011}. Observable break: the canonicalization replaces any JVM default, so a
     * server in another zone silently runs on Vietnam time and a business-date bug that depends on
     * the policy timezone stops showing. Expected: {@code Europe/Paris} stays {@code Europe/Paris}.
     */
    @Test
    void leavesSupportedSystemTimeZoneUnchanged() {
        TimeZone originalTimeZone = TimeZone.getDefault();

        try {
            TimeZone.setDefault(TimeZone.getTimeZone("Europe/Paris"));

            LabtimesheetApplication.normalizeDefaultTimeZone();

            assertThat(TimeZone.getDefault().getID()).isEqualTo("Europe/Paris");
        } finally {
            TimeZone.setDefault(originalTimeZone);
        }
    }

    private static Connection openConnection(PostgreSQLContainer postgres) throws SQLException {
        return DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword());
    }

    /** A context with no beans, so the test observes only what starting Spring Boot itself does. */
    @Configuration(proxyBeanMethods = false)
    static class EmptyContext {
    }
}
