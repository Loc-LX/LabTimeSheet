package com.lab.labtimesheet.feature.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.account.model.AccountStatus;
import com.lab.labtimesheet.feature.account.model.GlobalRole;
import com.lab.labtimesheet.feature.account.model.TokenPurpose;
import com.lab.labtimesheet.feature.account.model.dto.CreateAccountCommand;
import com.lab.labtimesheet.feature.account.repository.AppUserRepository;
import com.lab.labtimesheet.feature.account.repository.UserActionTokenRepository;
import com.lab.labtimesheet.feature.integration.model.SecurityMode;
import com.lab.labtimesheet.feature.integration.model.dto.SmtpConnection;
import com.lab.labtimesheet.feature.integration.model.dto.SmtpDraft;
import com.lab.labtimesheet.feature.integration.repository.SmtpConfigurationRepository;
import com.lab.labtimesheet.feature.integration.service.SmtpConfigurationService;
import com.lab.labtimesheet.feature.integration.service.SmtpProbe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@Import({TestcontainersConfiguration.class, AccountRecoveryIntegrationTest.MailProbeConfiguration.class})
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AccountRecoveryIntegrationTest {
    @Autowired
    private BootstrapService bootstrap;

    @Autowired
    private AccountService accounts;

    @Autowired
    private SmtpConfigurationService smtp;

    @Autowired
    private SmtpConfigurationRepository smtpConfigurations;

    @Autowired
    private RecordingSmtpProbe mail;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private UserActionTokenRepository tokens;

    @Autowired
    private PasswordEncoder passwords;

    @Autowired
    private SessionRegistry sessionRegistry;

    private long adminId;
    private long mentorId;
    private long internId;

    @BeforeEach
    void initializeAdminAccountsAndSmtp() {
        bootstrap.bootstrap("admin@example.com", "Admin", "correct horse battery staple");
        adminId = accounts.requireActiveAdminId("admin@example.com");
        activateSmtp(adminId);
        mail.messages.clear();

        var mentorCreation = accounts.create(new CreateAccountCommand(
                "mentor@example.com", "Mentor One", GlobalRole.MENTOR, null, null, null), adminId);
        accounts.activate(mail.onlyActivationToken(), "new secure mentor password");
        mentorId = mentorCreation.userId();

        var internCreation = accounts.create(new CreateAccountCommand(
                "intern@example.com", "Intern One", GlobalRole.INTERN, "STU-001",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 12, 31)), adminId);
        accounts.activate(mail.onlyActivationToken(), "new secure intern password");
        internId = internCreation.userId();
    }

    @Test
    void resendInvalidatesPriorActivationTokenAndOnlyNewTokenActivates() {
        mail.messages.clear();
        var pending = accounts.create(new CreateAccountCommand(
                "pending@example.com", "Pending One", GlobalRole.MENTOR, null, null, null), adminId);
        var firstRawToken = mail.onlyActivationToken();
        var firstToken = tokens.findAll().stream()
                .filter(token -> token.getUserId().equals(pending.userId()))
                .findFirst()
                .orElseThrow();

        mail.messages.clear();
        var resend = accounts.resendActivation(pending.userId(), adminId);
        assertThat(resend.deliverySucceeded()).isTrue();
        var secondRawToken = mail.onlyActivationToken();

        var reloadedFirst = tokens.findById(firstToken.getId()).orElseThrow();
        assertThat(reloadedFirst.getInvalidatedAt()).isNotNull();
        var secondToken = tokens.findAll().stream()
                .filter(token -> token.getUserId().equals(pending.userId()))
                .filter(token -> !token.getId().equals(firstToken.getId()))
                .findFirst()
                .orElseThrow();
        assertThat(secondToken.getPurpose()).isEqualTo(TokenPurpose.ACTIVATION);
        assertThat(secondToken.getInvalidatedAt()).isNull();

        assertThat(accounts.activate(firstRawToken, "new secure pending password")).isFalse();
        assertThat(accounts.activate(secondRawToken, "new secure pending password")).isTrue();
    }

    @Test
    void resendRequiresPendingAccountAndActiveSmtp() {
        assertThatThrownBy(() -> accounts.resendActivation(mentorId, adminId))
                .isInstanceOf(IllegalArgumentException.class);

        var pending = accounts.create(new CreateAccountCommand(
                "pending-smtp@example.com", "Pending SMTP", GlobalRole.MENTOR, null, null, null), adminId);
        smtpConfigurations.deleteAll();
        mail.messages.clear();

        assertThatThrownBy(() -> accounts.resendActivation(pending.userId(), adminId))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void passwordResetDeliversSingleUseThirtyMinuteTokenOnlyForActiveAccounts() {
        mail.messages.clear();
        var sent = accounts.requestPasswordReset("mentor@example.com");
        assertThat(sent).isTrue();
        assertThat(mail.messages).hasSize(1);

        var resetToken = tokens.findAll().stream()
                .filter(token -> token.getUserId().equals(mentorId))
                .filter(token -> token.getPurpose() == TokenPurpose.PASSWORD_RESET)
                .findFirst()
                .orElseThrow();
        assertThat(resetToken.getPurpose()).isEqualTo(TokenPurpose.PASSWORD_RESET);
        assertThat(resetToken.getExpiresAt()).isEqualTo(Instant.parse("2026-08-14T00:30:00Z"));
        assertThat(resetToken.getUsedAt()).isNull();
        assertThat(resetToken.getInvalidatedAt()).isNull();
        assertThat(accounts.resetPassword(mail.onlyResetToken(), "fresh secure mentor password")).isTrue();
        assertThat(accounts.resetPassword(mail.onlyResetToken(), "another secure password")).isFalse();
    }

    @Test
    void passwordResetDeliversGenericResponseForIneligibleOrUnknownEmail() {
        accounts.lockAccount(mentorId, adminId);
        var pending = accounts.create(new CreateAccountCommand(
                "pending@example.com", "Pending One", GlobalRole.MENTOR, null, null, null), adminId);

        for (String email : List.of("unknown@example.com", "mentor@example.com", "pending@example.com")) {
            mail.messages.clear();
            assertThat(accounts.requestPasswordReset(email)).isFalse();
            assertThat(mail.messages).isEmpty();
        }
        assertThat(tokens.findAll()).noneMatch(token -> token.getPurpose() == TokenPurpose.PASSWORD_RESET);
    }

    @Test
    void resetPasswordUpdatesCredentialsAndExpiresSessions() {
        sessionRegistry.registerNewSession("mentor-session-1", "mentor@example.com");

        mail.messages.clear();
        accounts.requestPasswordReset("mentor@example.com");
        assertThat(accounts.resetPassword(mail.onlyResetToken(), "fresh secure mentor password")).isTrue();

        var mentor = users.findById(mentorId).orElseThrow();
        assertThat(mentor.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(mentor.getGlobalRole()).isEqualTo(GlobalRole.MENTOR);
        assertThat(passwords.matches("fresh secure mentor password", mentor.getPasswordHash())).isTrue();
        assertThat(passwords.matches("new secure mentor password", mentor.getPasswordHash())).isFalse();

        SessionInformation session = sessionRegistry.getSessionInformation("mentor-session-1");
        assertThat(session).isNotNull();
        assertThat(session.isExpired()).isTrue();
    }

    @Test
    void resetPasswordRejectsInvalidTokenAndWeakPassword() {
        mail.messages.clear();
        accounts.requestPasswordReset("mentor@example.com");
        String rawToken = mail.onlyResetToken();

        assertThat(accounts.resetPassword("not-the-token", "fresh secure mentor password")).isFalse();
        assertThatThrownBy(() -> accounts.resetPassword(rawToken, "short"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> accounts.resetPassword(rawToken, "x".repeat(129)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(accounts.resetPassword(rawToken, "12charslong1")).isTrue();
    }

    @Test
    void resendFailureInvalidatesNewlyIssuedActivationToken() {
        mail.messages.clear();
        var pending = accounts.create(new CreateAccountCommand(
                "pending-fail@example.com", "Pending Fail", GlobalRole.MENTOR, null, null, null), adminId);
        var issuedToken = tokens.findAll().stream()
                .filter(token -> token.getUserId().equals(pending.userId()))
                .findFirst()
                .orElseThrow();

        mail.fail = true;
        mail.messages.clear();
        var resend = accounts.resendActivation(pending.userId(), adminId);
        assertThat(resend.deliverySucceeded()).isFalse();
        assertThat(tokens.findById(issuedToken.getId()).orElseThrow().getInvalidatedAt()).isNotNull();
    }

    private void activateSmtp(long adminId) {
        long draftId = smtp.saveDraft(adminId, new SmtpDraft(
                "mailpit", 1025, SecurityMode.NONE, null, null, "admin@example.com", "Lab Timesheet"));
        smtp.testDraft(draftId, adminId, "admin@example.com");
        smtp.activate(draftId, adminId);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MailProbeConfiguration {
        @Bean
        @Primary
        RecordingSmtpProbe recordingSmtpProbe() {
            return new RecordingSmtpProbe();
        }
    }

    static final class RecordingSmtpProbe implements SmtpProbe {
        private final List<Message> messages = new ArrayList<>();
        private boolean fail;

        @Override
        public void send(SmtpConnection connection, String recipient, String subject, String body) {
            if (fail) {
                throw new IllegalStateException("simulated SMTP failure");
            }
            messages.add(new Message(recipient, subject, body));
        }

        String onlyActivationToken() {
            assertThat(messages).isNotEmpty();
            String body = messages.getLast().body();
            int tokenStart = body.indexOf("token=");
            assertThat(tokenStart).isGreaterThanOrEqualTo(0);
            return body.substring(tokenStart + "token=".length()).trim();
        }

        String onlyResetToken() {
            assertThat(messages).isNotEmpty();
            String body = messages.getLast().body();
            int tokenStart = body.indexOf("reset-password?token=");
            assertThat(tokenStart).isGreaterThanOrEqualTo(0);
            return body.substring(tokenStart + "reset-password?token=".length()).trim();
        }
    }

    record Message(String recipient, String subject, String body) {
    }
}
