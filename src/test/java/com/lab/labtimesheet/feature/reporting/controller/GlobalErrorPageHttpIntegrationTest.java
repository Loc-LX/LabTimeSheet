package com.lab.labtimesheet.feature.reporting.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.account.service.BootstrapService;
import com.lab.labtimesheet.feature.integration.service.SmtpConfigurationService;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@Import({TestcontainersConfiguration.class, GlobalErrorPageHttpIntegrationTest.ErrorEndpointConfiguration.class})
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class GlobalErrorPageHttpIntegrationTest {

    @LocalServerPort
    private int port;

    @Autowired
    private BootstrapService bootstrap;

    @MockitoSpyBean
    private SmtpConfigurationService smtpConfiguration;

    @BeforeEach
    void initializeApplication() {
        bootstrap.bootstrap("admin@example.test", "Admin", "correct horse battery staple");
    }

    @AfterEach
    void resetSmtpConfigurationSpy() {
        reset(smtpConfiguration);
    }

    @Test
    void embeddedContainerRedispatchesControllerFailureToSafe500Html() throws Exception {
        HttpResponse<String> response = get(
                "/test-errors/controller-failure?submitted=secret-submitted-value");

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(contentType -> assertThat(contentType).startsWith("text/html"));
        assertThat(response.body()).contains("Something went wrong");
        assertThat(response.body()).doesNotContain(
                "controller-exception-secret",
                "/test-errors/controller-failure",
                "java.lang.IllegalStateException",
                "SecretController.java:42",
                "secret-submitted-value",
                "SELECT * FROM smtp_configurations");
    }

    @Test
    void embeddedContainerBypassesGlobalAdviceDuringErrorRedispatch() throws Exception {
        doThrow(new IllegalStateException(
                "smtp-advice-secret; java.lang.IllegalStateException; "
                        + "SmtpWarningAdvice.java:22; advice-submitted-value; "
                        + "SELECT * FROM smtp_configurations"))
                .when(smtpConfiguration).hasActiveConfiguration();

        HttpResponse<String> response = get(
                "/test-errors/advice-failure?submitted=advice-submitted-value");

        assertThat(response.statusCode()).isEqualTo(500);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(contentType -> assertThat(contentType).startsWith("text/html"));
        assertThat(response.body()).contains("Something went wrong");
        assertThat(response.body()).doesNotContain(
                "smtp-advice-secret",
                "/test-errors/advice-failure",
                "java.lang.IllegalStateException",
                "SmtpWarningAdvice.java:22",
                "advice-submitted-value",
                "SELECT * FROM smtp_configurations");
        verify(smtpConfiguration, times(1)).hasActiveConfiguration();
    }

    @Test
    void embeddedContainerRedispatchesRoleDenialToSafe403Html() throws Exception {
        HttpResponse<String> response = get(
                "/test-errors/role-denied",
                basicCredentials("mentor", "correct horse battery staple"));

        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(contentType -> assertThat(contentType).startsWith("text/html"));
        assertThat(response.body()).contains("You do not have access to this page");
        assertThat(response.body()).contains("Return to dashboard");
        assertThat(response.body()).doesNotContain("Sign in", "anonymousUser", "mentor");
    }

    @Test
    void embeddedContainerKeepsJsonNegotiationNonHtml() throws Exception {
        HttpResponse<String> response = get(
                "/test-errors/unknown-resource?submitted=json-secret-value", null, "application/json");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(contentType -> assertThat(contentType).startsWith("application/json"));
        assertThat(response.body()).doesNotContain(
                "<html", "Lab Timesheet", "Page not found", "Something went wrong");
    }

    private HttpResponse<String> get(String path) throws Exception {
        return get(path, null, "text/html");
    }

    private HttpResponse<String> get(String path, String authorization) throws Exception {
        return get(path, authorization, "text/html");
    }

    private HttpResponse<String> get(String path, String authorization, String accept) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .cookieHandler(new CookieManager(null, CookiePolicy.ACCEPT_ALL))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Accept", accept);
        if (authorization != null) {
            request.header("Authorization", authorization);
        }
        return client.send(request.GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    private static String basicCredentials(String username, String password) {
        return "Basic " + java.util.Base64.getEncoder()
                .encodeToString((username + ":" + password).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ErrorEndpointConfiguration {

        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE)
        SecurityFilterChain testRoleDenialSecurity(HttpSecurity http) throws Exception {
            return http.securityMatcher("/test-errors/role-denied")
                    .authenticationProvider(new TestRoleAuthenticationProvider())
                    .httpBasic(Customizer.withDefaults())
                    .authorizeHttpRequests(authorize -> authorize.anyRequest().hasRole("ADMIN"))
                    .build();
        }

        @Bean
        @Order(Ordered.HIGHEST_PRECEDENCE + 1)
        SecurityFilterChain testErrorEndpointSecurity(HttpSecurity http) throws Exception {
            return http.securityMatcher("/test-errors/**")
                    .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll())
                    .build();
        }

        @Bean
        ErrorEndpointController errorEndpointController() {
            return new ErrorEndpointController();
        }
    }

    @RestController
    static class ErrorEndpointController {

        @GetMapping("/test-errors/controller-failure")
        String controllerFailure() {
            throw new IllegalStateException(
                    "controller-exception-secret; java.lang.IllegalStateException; "
                            + "SecretController.java:42; secret-submitted-value; "
                            + "SELECT * FROM smtp_configurations");
        }

        @GetMapping("/test-errors/advice-failure")
        String adviceFailure() {
            return "unreachable";
        }

        @GetMapping("/test-errors/role-denied")
        String roleDenied() {
            return "unreachable";
        }
    }

    static final class TestRoleAuthenticationProvider implements AuthenticationProvider {

        @Override
        public Authentication authenticate(Authentication authentication) throws AuthenticationException {
            if (!supports(authentication.getClass())) {
                return null;
            }
            if ("mentor".equals(authentication.getName())
                    && "correct horse battery staple".equals(authentication.getCredentials())) {
                return UsernamePasswordAuthenticationToken.authenticated(
                        "mentor",
                        null,
                        java.util.List.of(new SimpleGrantedAuthority("ROLE_MENTOR")));
            }
            throw new BadCredentialsException("Unsupported test credentials");
        }

        @Override
        public boolean supports(Class<?> authentication) {
            return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
        }
    }
}
