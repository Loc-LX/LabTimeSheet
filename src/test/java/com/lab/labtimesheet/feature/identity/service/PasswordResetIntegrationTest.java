package com.lab.labtimesheet.feature.identity.service;

import com.lab.labtimesheet.feature.internship.service.InternshipService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import com.lab.labtimesheet.feature.identity.model.AccountStatus;
import com.lab.labtimesheet.feature.identity.model.TokenPurpose;
import com.lab.labtimesheet.feature.identity.model.dto.CreateAccountCommand;
import com.lab.labtimesheet.feature.identity.model.entity.UserActionToken;
import com.lab.labtimesheet.feature.identity.repository.AppUserRepository;
import com.lab.labtimesheet.feature.identity.repository.UserActionTokenRepository;
import com.lab.labtimesheet.platform.model.GlobalRole;
import com.lab.labtimesheet.platform.model.SecurityMode;
import com.lab.labtimesheet.platform.model.dto.SmtpConnection;
import com.lab.labtimesheet.platform.model.dto.SmtpDraft;
import com.lab.labtimesheet.platform.service.SmtpConfigurationService;
import com.lab.labtimesheet.platform.service.SmtpProbe;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;

/**
 * Proves the externally reachable password-reset lifecycle against PostgreSQL and the session registry.
 *
 * <p>The mail adapter records only the immediate in-memory delivery boundary. The database assertions inspect
 * persisted token state without exposing persistence types to other features.</p>
 */
@Import(PasswordResetIntegrationTest.IntegrationConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PasswordResetIntegrationTest {
    private static final Instant BASE_TIME = Instant.parse("2026-08-14T00:00:00Z");
    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String ADMIN_PASSWORD = "correct horse battery staple";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BootstrapService bootstrap;

    @Autowired
    private AccountService accounts;

    @Autowired
    private InternshipService internships;

    @Autowired
    private SmtpConfigurationService smtp;

    @Autowired
    private RecordingSmtpProbe mail;

    @Autowired
    private UserActionTokenRepository tokens;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private MutableClock clock;

    @Test
    void unknownForgotPostReturnsTheSameGenericResponseWithoutIssuingMail() throws Exception {
        enableSmtp();

        mockMvc.perform(post("/forgot-password")
                        .with(csrf())
                        .param("email", "missing@example.com"))
                .andExpect(redirectedUrl("/forgot-password?requested"));

        assertThat(mail.bodies()).isEmpty();
        assertThat(tokens.findAll()).isEmpty();
    }

    @Test
    void forgotPostUsesOneGenericResponseForActivePendingLockedAndUnknownAccounts() throws Exception {
        long activeId = createActiveMentor("forgot-active@example.com");
        long lockedId = createActiveMentor("forgot-locked@example.com");
        long adminId = accounts.requireActiveAdminId(ADMIN_EMAIL);
        internships.create(new CreateAccountCommand(
                "forgot-pending@example.com", "Pending Mentor", GlobalRole.MENTOR, null, null, null), adminId);
        accounts.lockAccount(lockedId, adminId);
        mail.clear();

        assertThat(requestForgot("forgot-active@example.com")).isEqualTo("/forgot-password?requested");
        assertThat(requestForgot("forgot-pending@example.com")).isEqualTo("/forgot-password?requested");
        assertThat(requestForgot("forgot-locked@example.com")).isEqualTo("/forgot-password?requested");
        assertThat(requestForgot("forgot-unknown@example.com")).isEqualTo("/forgot-password?requested");
        assertThat(users.findById(activeId)).isPresent();
    }

    @Test
    void forgotPostRemainsGenericAndDoesNotIssueTokenWithoutTestedSmtp() throws Exception {
        bootstrap.bootstrap(ADMIN_EMAIL, "Admin", ADMIN_PASSWORD);

        assertThat(requestForgot(ADMIN_EMAIL)).isEqualTo("/forgot-password?requested");
        assertThat(requestForgot("missing-without-smtp@example.com")).isEqualTo("/forgot-password?requested");
        assertThat(smtp.hasActiveConfiguration()).isFalse();
        assertThat(mail.bodies()).isEmpty();
        assertThat(tokens.findAll()).isEmpty();
    }

    @Test
    void resetPostRejectsPasswordsOutsideTheTwelveToOneTwentyEightCharacterBounds() throws Exception {
        long userId = createActiveMentor("reset-bounds@example.com");
        assertThat(accounts.requestPasswordReset("reset-bounds@example.com")).isTrue();
        String rawToken = mail.lastToken();

        mockMvc.perform(post("/reset-password")
                        .with(csrf())
                        .param("token", rawToken)
                        .param("password", "x".repeat(11))
                        .param("confirmPassword", "x".repeat(11)))
                .andExpect(view().name("accounts/reset-password"));

        mockMvc.perform(post("/reset-password")
                        .with(csrf())
                        .param("token", rawToken)
                        .param("password", "x".repeat(129))
                        .param("confirmPassword", "x".repeat(129)))
                .andExpect(view().name("accounts/reset-password"));

        assertThat(accounts.resetPassword(rawToken, "x".repeat(12))).isTrue();
        assertThat(resetTokenFor(userId).getUsedAt()).isEqualTo(BASE_TIME);
    }

    @Test
    void invalidResetPostReturnsTheSameGenericResponseForAnUnknownToken() throws Exception {
        bootstrap.bootstrap(ADMIN_EMAIL, "Admin", ADMIN_PASSWORD);

        mockMvc.perform(post("/reset-password")
                        .with(csrf())
                        .param("token", "opaque-token")
                        .param("password", "a secure password")
                        .param("confirmPassword", "a secure password"))
                .andExpect(view().name("accounts/reset-password"))
                .andExpect(content().string(containsString("invalid or no longer usable")));
    }

    @Test
    void resetTokenPersistsOnlyItsHashExpiresExclusivelyAndIsSingleUse() {
        long userId = createActiveMentor("reset-lifecycle@example.com");
        assertThat(accounts.requestPasswordReset("reset-lifecycle@example.com")).isTrue();
        String rawToken = mail.lastToken();
        UserActionToken token = resetTokenFor(userId);

        assertThat(token.getTokenHash()).containsExactly(sha256(rawToken));
        assertThat(new String(token.getTokenHash(), StandardCharsets.UTF_8)).doesNotContain(rawToken);
        assertThat(token.getExpiresAt()).isEqualTo(BASE_TIME.plus(Duration.ofMinutes(30)));

        assertThat(accounts.resetPassword(rawToken, "a secure reset password")).isTrue();
        assertThat(accounts.resetPassword(rawToken, "another secure password")).isFalse();
        assertThat(tokens.findById(token.getId()).orElseThrow().getUsedAt()).isEqualTo(BASE_TIME);
    }

    @Test
    void resetTokenExpiresAtExactlyThirtyMinutes() {
        long userId = createActiveMentor("reset-expiry@example.com");
        assertThat(accounts.requestPasswordReset("reset-expiry@example.com")).isTrue();
        String rawToken = mail.lastToken();

        clock.set(BASE_TIME.plus(Duration.ofMinutes(30)));

        assertThat(accounts.resetPassword(rawToken, "a secure reset password")).isFalse();
        assertThat(resetTokenFor(userId).getUsedAt()).isNull();
    }

    @Test
    void twelveAndOneTwentyEightCharacterPasswordsResetAndReplaceTheLoginCredential() throws Exception {
        createActiveMentor("reset-twelve@example.com");
        assertThat(accounts.requestPasswordReset("reset-twelve@example.com")).isTrue();
        String twelveToken = mail.lastToken();
        String twelvePassword = "x".repeat(12);

        assertThat(accounts.resetPassword(twelveToken, twelvePassword)).isTrue();
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "reset-twelve@example.com")
                        .param("password", "a secure mentor password"))
                .andExpect(unauthenticated());
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "reset-twelve@example.com")
                        .param("password", twelvePassword))
                .andExpect(authenticated().withUsername("reset-twelve@example.com"));

        createActiveMentor("reset-one-twenty-eight@example.com");
        assertThat(accounts.requestPasswordReset("reset-one-twenty-eight@example.com")).isTrue();
        assertThat(accounts.resetPassword(mail.lastToken(), "x".repeat(128))).isTrue();
    }

    @Test
    void issuingAReplacementInvalidatesTheOlderResetToken() {
        long userId = createActiveMentor("reset-replacement@example.com");
        assertThat(accounts.requestPasswordReset("reset-replacement@example.com")).isTrue();
        String first = mail.lastToken();
        mail.clear();
        assertThat(accounts.requestPasswordReset("reset-replacement@example.com")).isTrue();
        String second = mail.lastToken();

        assertThat(second).isNotEqualTo(first);
        assertThat(accounts.resetPassword(first, "a secure reset password")).isFalse();
        assertThat(accounts.resetPassword(second, "another secure password")).isTrue();
        assertThat(tokens.findAll().stream()
                .filter(token -> token.getUserId().equals(userId) && token.getPurpose() == TokenPurpose.PASSWORD_RESET)
                .filter(token -> token.getInvalidatedAt() != null)
                .count()).isEqualTo(1);
    }

    @Test
    void failedResetDeliveryInvalidatesTheFreshToken() {
        long userId = createActiveMentor("reset-delivery-failure@example.com");
        mail.failDelivery();

        assertThat(accounts.requestPasswordReset("reset-delivery-failure@example.com")).isTrue();
        String rawToken = mail.lastToken();
        UserActionToken token = resetTokenFor(userId);

        assertThat(token.getInvalidatedAt()).isEqualTo(BASE_TIME);
        assertThat(accounts.resetPassword(rawToken, "a secure reset password")).isFalse();
    }

    @Test
    void successfulResetExpiresTheAffectedAuthenticatedSession() throws Exception {
        long userId = createActiveMentor("reset-session@example.com");
        assertThat(accounts.requestPasswordReset("reset-session@example.com")).isTrue();
        String rawToken = mail.lastToken();

        var login = mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "reset-session@example.com")
                        .param("password", "a secure mentor password"))
                .andExpect(authenticated().withUsername("reset-session@example.com"))
                .andReturn();
        MockHttpSession session = (MockHttpSession) login.getRequest().getSession(false);

        assertThat(accounts.resetPassword(rawToken, "a secure reset password")).isTrue();
        mockMvc.perform(get("/dashboard").session(session))
                .andExpect(redirectedUrl("/login"))
                .andExpect(unauthenticated());
        assertThat(users.findById(userId).orElseThrow().getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    private void enableSmtp() {
        bootstrap.bootstrap(ADMIN_EMAIL, "Admin", ADMIN_PASSWORD);
        long adminId = accounts.requireActiveAdminId(ADMIN_EMAIL);
        long draftId = smtp.saveDraft(adminId,
                new SmtpDraft("mailpit", 1025, SecurityMode.NONE, null, null, ADMIN_EMAIL, "Lab Timesheet"));
        smtp.testDraft(draftId, adminId, ADMIN_EMAIL);
        smtp.activate(draftId, adminId);
        mail.clear();
    }

    private String requestForgot(String email) throws Exception {
        return mockMvc.perform(post("/forgot-password")
                        .with(csrf())
                        .param("email", email))
                .andReturn()
                .getResponse()
                .getRedirectedUrl();
    }

    private long createActiveMentor(String email) {
        if (!smtp.hasActiveConfiguration()) {
            enableSmtp();
        }
        var creation = internships.create(new CreateAccountCommand(
                email, "Reset Mentor", GlobalRole.MENTOR, null, null, null),
                accounts.requireActiveAdminId(ADMIN_EMAIL));
        assertThat(accounts.activate(mail.lastToken(), "a secure mentor password")).isTrue();
        mail.clear();
        return creation.userId();
    }

    private UserActionToken resetTokenFor(long userId) {
        return tokens.findAll().stream()
                .filter(token -> token.getUserId().equals(userId) && token.getPurpose() == TokenPurpose.PASSWORD_RESET)
                .findFirst()
                .orElseThrow();
    }

    private static byte[] sha256(String rawToken) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class IntegrationConfiguration {
        @Bean
        @ServiceConnection
        PostgreSQLContainer postgresContainer() {
            return new PostgreSQLContainer(DockerImageName.parse("postgres:18.4"));
        }

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(BASE_TIME);
        }

        @Bean
        @Primary
        RecordingSmtpProbe recordingSmtpProbe() {
            return new RecordingSmtpProbe();
        }
    }

    static final class RecordingSmtpProbe implements SmtpProbe {
        private final List<String> sentBodies = new ArrayList<>();
        private volatile boolean fail;

        @Override
        public synchronized void send(SmtpConnection connection, String recipient, String subject, String body) {
            sentBodies.add(body);
            if (fail) {
                throw new IllegalStateException("simulated SMTP delivery failure");
            }
        }

        synchronized void clear() {
            sentBodies.clear();
            fail = false;
        }

        synchronized void failDelivery() {
            fail = true;
        }

        synchronized List<String> bodies() {
            return List.copyOf(sentBodies);
        }

        synchronized String lastToken() {
            String body = sentBodies.getLast();
            int marker = body.indexOf("token=");
            assertThat(marker).isGreaterThanOrEqualTo(0);
            return body.substring(marker + "token=".length()).trim();
        }
    }

    static final class MutableClock extends Clock {
        private volatile Instant current;

        MutableClock(Instant initial) {
            current = initial;
        }

        void set(Instant instant) {
            current = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("Asia/Ho_Chi_Minh");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }
    }
}
