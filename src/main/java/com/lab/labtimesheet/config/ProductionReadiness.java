package com.lab.labtimesheet.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.lab.labtimesheet.platform.SecurityProperties;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Fails production startup before readiness when transport, origin, datasource, proxy, cookie, error, or key
 * requirements are unsafe. Dev/test profiles do not create this bean and retain their explicit local relaxations.
 */
@Component
@Profile("prod")
final class ProductionReadiness {
    private final Inputs inputs;

    ProductionReadiness(
            SecurityProperties security,
            @Value("${lab.public-origin:}") String publicOrigin,
            @Value("${spring.datasource.url:}") String datasourceUrl,
            @Value("${spring.datasource.username:}") String datasourceUsername,
            @Value("${spring.datasource.password:}") String datasourcePassword,
            @Value("${server.forward-headers-strategy:}") String forwardHeadersStrategy,
            @Value("${server.servlet.session.cookie.secure:false}") boolean secureCookie,
            @Value("${server.servlet.session.cookie.same-site:}") String sameSite,
            @Value("${server.error.include-message:}") String includeMessage,
            @Value("${server.error.include-stacktrace:}") String includeStacktrace) {
        this.inputs = new Inputs(
                publicOrigin, datasourceUrl, datasourceUsername, datasourcePassword, forwardHeadersStrategy,
                security.getTrustedProxyCidrs(), secureCookie, sameSite, includeMessage, includeStacktrace, security);
    }

    /** Validates the resolved production configuration during application context creation. */
    @PostConstruct
    void verify() {
        validate(inputs);
    }

    /**
     * Validates one resolved production configuration without logging any supplied secret.
     *
     * @param inputs resolved production settings
     * @throws IllegalStateException when one or more readiness gates fail
     */
    static void validate(Inputs inputs) {
        List<String> failures = new ArrayList<>();
        try {
            canonicalOrigin(inputs.publicOrigin());
        } catch (IllegalArgumentException failure) {
            failures.add("HTTPS public origin");
        }
        if (inputs.datasourceUrl() == null || !inputs.datasourceUrl().startsWith("jdbc:postgresql://")) {
            failures.add("PostgreSQL datasource URL");
        }
        if (blank(inputs.datasourceUsername()) || blank(inputs.datasourcePassword())) {
            failures.add("datasource credentials");
        }
        if (!"framework".equalsIgnoreCase(value(inputs.forwardHeadersStrategy()))) {
            failures.add("framework forwarded-header strategy");
        }
        try {
            TrustedProxyMatcher.parse(inputs.trustedProxyCidrs());
        } catch (IllegalStateException failure) {
            failures.add("trusted proxy policy");
        }
        if (!inputs.secureCookie()) {
            failures.add("Secure session cookie");
        }
        if (!"strict".equalsIgnoreCase(value(inputs.sameSite()))) {
            failures.add("SameSite=Strict session cookie");
        }
        if (!"never".equalsIgnoreCase(value(inputs.includeMessage()))) {
            failures.add("safe error messages");
        }
        if (!"never".equalsIgnoreCase(value(inputs.includeStacktrace()))) {
            failures.add("safe error stack traces");
        }
        try {
            inputs.security().decodedMasterKey();
        } catch (RuntimeException failure) {
            failures.add("256-bit application master key");
        }
        if (!failures.isEmpty()) {
            throw new IllegalStateException("Production readiness check failed: " + String.join(", ", failures));
        }
    }

    /**
     * Canonicalizes a configured origin and rejects paths, credentials, fragments, local hosts, and non-HTTPS schemes.
     *
     * @param value configured or request-derived origin
     * @return canonical scheme/authority origin without a trailing slash, lower-case host, or default HTTPS port
     * @throws IllegalArgumentException when the value is not a valid production origin
     */
    static String canonicalOrigin(String value) {
        if (blank(value)) {
            throw new IllegalArgumentException("Public origin is required");
        }
        try {
            URI uri = new URI(value.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(scheme) || host == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null
                    || (uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath()))) {
                throw new IllegalArgumentException("Invalid public origin");
            }
            String normalizedHost = host.toLowerCase(Locale.ROOT);
            if (normalizedHost.startsWith("[") && normalizedHost.endsWith("]")) {
                normalizedHost = normalizedHost.substring(1, normalizedHost.length() - 1);
            }
            if (normalizedHost.equals("localhost") || normalizedHost.endsWith(".localhost")
                    || normalizedHost.equals("127.0.0.1") || normalizedHost.equals("::1")) {
                throw new IllegalArgumentException("Local public origin is not production-safe");
            }
            String canonicalHost = normalizedHost.contains(":") ? "[" + normalizedHost + "]" : normalizedHost;
            String canonicalPort = uri.getPort() == -1 || uri.getPort() == 443 ? "" : ":" + uri.getPort();
            return "https://" + canonicalHost + canonicalPort;
        } catch (URISyntaxException failure) {
            throw new IllegalArgumentException("Invalid public origin", failure);
        }
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static String value(String value) {
        return value == null ? "" : value.trim();
    }

    /** Resolved values validated by the production startup gate. */
    record Inputs(
            String publicOrigin,
            String datasourceUrl,
            String datasourceUsername,
            String datasourcePassword,
            String forwardHeadersStrategy,
            String trustedProxyCidrs,
            boolean secureCookie,
            String sameSite,
            String includeMessage,
            String includeStacktrace,
            SecurityProperties security) {
    }
}
