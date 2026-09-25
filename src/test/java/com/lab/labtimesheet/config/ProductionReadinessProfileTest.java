package com.lab.labtimesheet.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.lab.labtimesheet.platform.SecurityProperties;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * Protects {@code SEC-013}. Observable break: a development relaxation reaches production because the
 * {@code prod} readiness gate no longer refuses it on its own.
 *
 * <p>Each case binds {@code application-prod.yaml} under the {@code prod} profile with non-secret, test-only
 * values that are safe, changes exactly one value to a relaxation {@code SEC-013} allows only in development or
 * test, and expects startup to fail naming that one check and nothing else.</p>
 */
class ProductionReadinessProfileTest {
    private static final String TEST_ONLY_MASTER_KEY = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";
    private static final String FAILURE = "Production readiness check failed: ";

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(ReadinessOnly.class)
            .withPropertyValues(
                    "spring.profiles.active=prod",
                    "spring.datasource.url=jdbc:postgresql://db.example.test:5432/labtimesheet",
                    "spring.datasource.username=labtimesheet",
                    "spring.datasource.password=test-only-password",
                    "server.forward-headers-strategy=framework",
                    "lab.public-origin=https://timesheet.example.test",
                    "lab.security.master-key=" + TEST_ONLY_MASTER_KEY,
                    "lab.security.trusted-proxy-cidrs=10.20.0.0/24");

    @Test
    void safeProductionValuesPassReadiness() {
        runner.run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void httpPublicOriginIsRefusedAlone() {
        assertRefusedAlone("lab.public-origin=http://timesheet.example.test", "HTTPS public origin");
    }

    @Test
    void localhostPublicOriginIsRefusedAlone() {
        assertRefusedAlone("lab.public-origin=https://localhost", "HTTPS public origin");
    }

    @Test
    void laxSameSiteCookieIsRefusedAlone() {
        assertRefusedAlone("server.servlet.session.cookie.same-site=lax", "SameSite=Strict session cookie");
    }

    @Test
    void sessionCookieWithoutSecureIsRefusedAlone() {
        assertRefusedAlone("server.servlet.session.cookie.secure=false", "Secure session cookie");
    }

    private void assertRefusedAlone(String relaxation, String check) {
        runner.withPropertyValues(relaxation).run(context -> assertThat(context)
                .getFailure()
                .rootCause()
                .hasMessage(FAILURE + check));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(SecurityProperties.class)
    @Import(ProductionReadiness.class)
    static class ReadinessOnly {
    }
}
