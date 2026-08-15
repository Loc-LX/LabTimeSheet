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
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

class ApplicationTimeZoneIntegrationTest {

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
}
