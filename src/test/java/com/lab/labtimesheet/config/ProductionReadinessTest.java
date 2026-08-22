package com.lab.labtimesheet.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

/** Unit proof for production-only transport, origin, datasource, proxy, and key readiness rules. */
class ProductionReadinessTest {
    private static final String KEY = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";

    @Test
    void validProductionInputsPassAndSMTPMayRemainUnconfigured() {
        SecurityProperties security = securityProperties();

        assertThatCode(() -> ProductionReadiness.validate(new ProductionReadiness.Inputs(
                "https://timesheet.example.edu",
                "jdbc:postgresql://db.example.edu:5432/labtimesheet",
                "labtimesheet",
                "database-password",
                "framework",
                "10.20.0.0/24",
                true,
                "strict",
                "never",
                "never",
                security))).doesNotThrowAnyException();
    }

    @Test
    void unsafeProductionInputsFailWithoutEchoingSecrets() {
        SecurityProperties security = securityProperties();
        ProductionReadiness.Inputs unsafe = new ProductionReadiness.Inputs(
                "http://localhost:8080",
                "jdbc:h2:mem:unsafe",
                "",
                "",
                "none",
                "",
                false,
                "lax",
                "always",
                "always",
                security);

        assertThatThrownBy(() -> ProductionReadiness.validate(unsafe))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Production readiness check failed")
                .hasMessageNotContaining("database-password")
                .hasMessageNotContaining(KEY);
    }

    @Test
    void malformedMasterKeyAndProxyCidrFailReadiness() {
        SecurityProperties security = new SecurityProperties();
        security.setMasterKey("not-a-key");
        ProductionReadiness.Inputs inputs = new ProductionReadiness.Inputs(
                "https://timesheet.example.edu",
                "jdbc:postgresql://db.example.edu:5432/labtimesheet",
                "labtimesheet",
                "database-password",
                "framework",
                "proxy.example.edu",
                true,
                "strict",
                "never",
                "never",
                security);

        assertThatThrownBy(() -> ProductionReadiness.validate(inputs))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("master key")
                .hasMessageContaining("proxy");
    }

    @Test
    void trustedProxyMatcherAcceptsOnlyConfiguredSocketAddresses() {
        TrustedProxyMatcher matcher = TrustedProxyMatcher.parse("10.20.0.0/24, 192.0.2.10");

        org.assertj.core.api.Assertions.assertThat(matcher.matches("10.20.0.44")).isTrue();
        org.assertj.core.api.Assertions.assertThat(matcher.matches("192.0.2.10")).isTrue();
        org.assertj.core.api.Assertions.assertThat(matcher.matches("192.0.2.11")).isFalse();
    }

    @Test
    void catchAllTrustedProxyNetworksAreRejected() {
        SecurityProperties security = securityProperties();

        assertThatThrownBy(() -> ProductionReadiness.validate(inputsWithProxy("0.0.0.0/0", security)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("trusted proxy policy");
        assertThatThrownBy(() -> ProductionReadiness.validate(inputsWithProxy("::/0", security)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("trusted proxy policy");
    }

    @Test
    void productionOriginIsCanonicalAndRejectsBracketedIpv6Loopback() {
        assertThatThrownBy(() -> ProductionReadiness.canonicalOrigin("https://[::1]"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(ProductionReadiness.canonicalOrigin("https://Timesheet.Example.edu"))
                .isEqualTo("https://timesheet.example.edu");
        assertThat(ProductionReadiness.canonicalOrigin("https://Timesheet.Example.edu:443"))
                .isEqualTo("https://timesheet.example.edu");
        assertThat(ProductionReadiness.canonicalOrigin("https://Timesheet.Example.edu:8443"))
                .isEqualTo("https://timesheet.example.edu:8443");
    }

    private static ProductionReadiness.Inputs inputsWithProxy(
            String trustedProxyCidrs, SecurityProperties security) {
        return new ProductionReadiness.Inputs(
                "https://timesheet.example.edu",
                "jdbc:postgresql://db.example.edu:5432/labtimesheet",
                "labtimesheet",
                "database-password",
                "framework",
                trustedProxyCidrs,
                true,
                "strict",
                "never",
                "never",
                security);
    }

    private static SecurityProperties securityProperties() {
        SecurityProperties security = new SecurityProperties();
        security.setMasterKey(KEY);
        return security;
    }
}
