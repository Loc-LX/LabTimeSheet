package com.lab.labtimesheet.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.management.ManagementFactory;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/** Production-shaped startup contract for the local deterministic E2E profile and its development configuration. */
@SpringBootTest
@ActiveProfiles("e2e")
@Import(E2eProfileIntegrationTest.E2ePostgresConfiguration.class)
@TestPropertySource(properties = {
        "LAB_DB_URL=jdbc:postgresql://localhost:5432/e2e",
        "LAB_DB_USERNAME=e2e",
        "LAB_DB_PASSWORD=e2e",
        "LAB_SMTP_HOST=localhost",
        "LAB_SMTP_PORT=1025",
        "LAB_SERVER_PORT=0",
        "LAB_FORWARD_HEADERS_STRATEGY=none",
        "LAB_PUBLIC_ORIGIN=http://localhost:8080",
        "LAB_SECURITY_MASTER_KEY=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="
})
class E2eProfileIntegrationTest {
    @Autowired
    private Clock clock;

    @Autowired
    private Environment environment;

    @Test
    void e2eStartupUsesDevelopmentDatasourceAndAdvancingClock() throws InterruptedException {
        // The clock is anchored when its bean is created, so the distance from the anchor is the
        // startup time left after that moment, which bean order decides. The JVM uptime bounds it
        // without assuming how fast this machine starts.
        Instant anchor = Instant.parse("2026-08-22T02:00:00Z");
        Instant firstReading = clock.instant();
        assertThat(firstReading)
                .isBetween(anchor, anchor.plusMillis(ManagementFactory.getRuntimeMXBean().getUptime()));

        Instant beforeWait = clock.instant();
        Instant realBeforeWait = Instant.now();
        Thread.sleep(Duration.ofMillis(200));
        long clockAdvance = Duration.between(beforeWait, clock.instant()).toMillis();
        long realAdvance = Duration.between(realBeforeWait, Instant.now()).toMillis();
        assertThat(clockAdvance).isBetween(realAdvance - 50, realAdvance + 50);
        assertThat(environment.getProperty("server.port")).isEqualTo("0");
        assertThat(environment.getProperty("spring.mail.host")).isEqualTo("localhost");
        assertThat(environment.getProperty("lab.public-origin")).isEqualTo("http://localhost:8080");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class E2ePostgresConfiguration {
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgresContainer() {
            return new PostgreSQLContainer(DockerImageName.parse("postgres:18.4"));
        }
    }
}
