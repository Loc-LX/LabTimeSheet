package com.lab.labtimesheet.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Protects {@code SEC-011}, {@code SEC-013} and {@code AC-SEC-008}. Observable break: a production response
 * loses a security header value or a session cookie attribute, or HSTS is not sent on a secure request.
 *
 * <p>The application runs on a real server under the {@code prod} profile with non-secret, test-only values and a
 * database of its own. Requests arrive from {@code 127.0.0.1} with {@code X-Forwarded-Proto: https}; the trusted
 * proxy range exists only in this test's configuration. The session cookie is read from the real
 * {@code Set-Cookie} header, since the servlet container applies its attributes.</p>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "server.forward-headers-strategy=framework",
        "lab.public-origin=https://timesheet.example.test",
        "lab.security.master-key=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
        "lab.security.trusted-proxy-cidrs=127.0.0.1/32,::1/128"})
@ActiveProfiles("prod")
class ProductionResponseSecurityIntegrationTest {
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:18.4"));

    static {
        POSTGRES.start();
    }

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Test
    void hstsIsSentOnASecureResponse() throws Exception {
        String hsts = secureResponse().headers().firstValue("Strict-Transport-Security").orElse("");

        assertThat(directives(hsts)).containsExactlyInAnyOrder("max-age=31536000", "includeSubDomains", "preload");
    }

    @Test
    void contentSecurityPolicyRestrictsToTheApplication() throws Exception {
        assertThat(secureResponse().headers().firstValue("Content-Security-Policy"))
                .hasValue("default-src 'self'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'");
    }

    @Test
    void referrerIsNeverDisclosed() throws Exception {
        assertThat(secureResponse().headers().firstValue("Referrer-Policy")).hasValue("no-referrer");
    }

    @Test
    void sessionCookieIsSecure() throws Exception {
        assertThat(sessionCookieAttributes()).contains("secure");
    }

    @Test
    void sessionCookieIsHttpOnly() throws Exception {
        assertThat(sessionCookieAttributes()).contains("httponly");
    }

    @Test
    void sessionCookieIsSameSiteStrict() throws Exception {
        assertThat(sessionCookieAttributes()).contains("samesite=strict");
    }

    private HttpResponse<String> secureResponse() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/bootstrap"))
                .header("X-Forwarded-Proto", "https")
                .GET()
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private Set<String> sessionCookieAttributes() throws IOException, InterruptedException {
        String cookie = secureResponse().headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith("JSESSIONID="))
                .findFirst()
                .orElse("");
        return directives(cookie).stream()
                .skip(1)
                .map(attribute -> attribute.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
    }

    private static List<String> directives(String header) {
        return Arrays.stream(header.split(";"))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
    }
}
