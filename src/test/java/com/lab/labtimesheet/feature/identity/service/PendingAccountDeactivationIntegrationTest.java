package com.lab.labtimesheet.feature.identity.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.identity.model.AccountStatus;
import com.lab.labtimesheet.feature.identity.model.TokenPurpose;
import com.lab.labtimesheet.feature.identity.model.dto.CreateAccountCommand;
import com.lab.labtimesheet.feature.identity.model.entity.UserActionToken;
import com.lab.labtimesheet.feature.identity.repository.AppUserRepository;
import com.lab.labtimesheet.feature.identity.repository.UserActionTokenRepository;
import com.lab.labtimesheet.feature.internship.service.InternshipService;
import com.lab.labtimesheet.platform.model.GlobalRole;
import com.lab.labtimesheet.platform.model.SecurityMode;
import com.lab.labtimesheet.platform.model.dto.SmtpDraft;
import com.lab.labtimesheet.platform.service.SmtpConfigurationService;
import com.lab.labtimesheet.platform.service.SmtpProbe;
import org.junit.jupiter.api.Test;
import org.assertj.core.api.SoftAssertions;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

/** PostgreSQL integration coverage for pending-account deactivation and its one-time links. */
@Import({TestcontainersConfiguration.class, PendingAccountDeactivationIntegrationTest.MailConfiguration.class})
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PendingAccountDeactivationIntegrationTest {
    @Autowired private BootstrapService bootstrap;
    @Autowired private AccountService accounts;
    @Autowired private InternshipService internships;
    @Autowired private SmtpConfigurationService smtp;
    @Autowired private AppUserRepository users;
    @Autowired private UserActionTokenRepository tokens;
    @Autowired private RecordingProbe mail;

    /**
     * Protects ACC-014, ACC-016, ACC-028, ACC-029, ACC-030 and AC-ACC-020. Observable breaks include a
     * live activation/reset link after pending deactivation, a reset token that works after reinstatement
     * and activation, a distinguishable reset response, a newly sent reset message, or deleted history rows.
     * The hand-derived result is unchanged identity/token row counts, invalidated old links, no credentials
     * while deactivated, the generic reset response, and no effect from the old reset bearer after activation.
     */
    @Test
    void deactivatingPendingAccountInvalidatesActivationAndResetTokensWithoutAddingCredentials() {
        bootstrap.bootstrap("admin@example.test", "Admin", "correct horse battery staple");
        long adminId = accounts.requireActiveAdminId("admin@example.test");
        long smtpId = smtp.saveDraft(adminId, new SmtpDraft("mailpit", 1025, SecurityMode.NONE,
                null, null, "admin@example.test", "Lab Timesheet"));
        smtp.testDraft(smtpId, adminId, "admin@example.test");
        smtp.activate(smtpId, adminId);
        var created = internships.create(new CreateAccountCommand(
                "pending@example.test", "Pending User", GlobalRole.MENTOR, null, null, null), adminId);
        Instant now = Instant.parse("2026-09-26T12:00:00Z");
        String rawResetToken = "known-pending-reset-token-for-c04";
        UserActionToken reset = tokens.saveAndFlush(UserActionToken.passwordReset(
                created.userId(), sha256(rawResetToken), now.plusSeconds(1800), now));
        SoftAssertions lifecycleAssertions = new SoftAssertions();
        long userRowsBefore = users.count();
        long targetTokenRowsBefore = targetTokenRows(created.userId());

        accounts.deactivateAccount(created.userId(), adminId);

        var account = users.findById(created.userId()).orElseThrow();
        assertThat(account.getAccountStatus()).isEqualTo(AccountStatus.DEACTIVATED);
        assertThat(account.getPasswordHash()).isNull();
        assertThat(account.getActivatedAt()).isNull();
        assertThat(account.getEmail()).isEqualTo("pending@example.test");
        assertThat(account.getGlobalRole()).isEqualTo(GlobalRole.MENTOR);
        assertThat(tokens.findAll().stream()
                        .filter(token -> token.getUserId().equals(created.userId()))
                        .filter(token -> token.getPurpose() == TokenPurpose.ACTIVATION)
                        .toList())
                .isNotEmpty()
                .allSatisfy(token -> assertThat(token.getInvalidatedAt()).isNotNull());
        lifecycleAssertions.assertThat(tokens.findById(reset.getId()).orElseThrow().getInvalidatedAt()).isNotNull();
        assertThat(accounts.activate(mail.activationToken, "another secure password")).isFalse();
        assertThat(accounts.resetPassword(rawResetToken, "reset password while deactivated")).isFalse();
        assertThat(users.findById(created.userId()).orElseThrow().getPasswordHash()).isNull();
        assertThat(users.count()).isEqualTo(userRowsBefore);
        assertThat(targetTokenRows(created.userId())).isEqualTo(targetTokenRowsBefore);

        int resetMessagesBefore = mail.resetMessageCount;
        boolean targetResetResponse = accounts.requestPasswordReset("pending@example.test");
        boolean unknownResetResponse = accounts.requestPasswordReset("missing@example.test");
        assertThat(targetResetResponse).isEqualTo(unknownResetResponse);
        assertThat(targetTokenRows(created.userId())).isEqualTo(targetTokenRowsBefore);
        tokens.findAll().stream()
                .filter(token -> token.getUserId().equals(created.userId()))
                .filter(token -> token.getPurpose() == TokenPurpose.PASSWORD_RESET)
                .toList()
                .forEach(token -> lifecycleAssertions.assertThat(token.getInvalidatedAt()).isNotNull());
        assertThat(mail.resetMessageCount).isEqualTo(resetMessagesBefore);

        accounts.reinstateAccount(created.userId(), adminId);
        assertThat(users.findById(created.userId()).orElseThrow().getAccountStatus())
                .isEqualTo(AccountStatus.PENDING_ACTIVATION);
        accounts.resendActivation(created.userId(), adminId);
        assertThat(accounts.activate(mail.activationToken, "new secure activated password")).isTrue();
        String activatedPasswordHash = users.findById(created.userId()).orElseThrow().getPasswordHash();
        lifecycleAssertions.assertThat(accounts.resetPassword(rawResetToken, "stale reset must remain invalid"))
                .isFalse();
        lifecycleAssertions.assertThat(users.findById(created.userId()).orElseThrow().getPasswordHash())
                .isEqualTo(activatedPasswordHash);
        lifecycleAssertions.assertAll();
    }

    private long targetTokenRows(long userId) {
        return tokens.findAll().stream().filter(token -> token.getUserId().equals(userId)).count();
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MailConfiguration {
        @Bean @Primary RecordingProbe recordingProbe() { return new RecordingProbe(); }
    }

    static final class RecordingProbe implements SmtpProbe {
        private String activationToken;
        private int resetMessageCount;
        @Override public void send(com.lab.labtimesheet.platform.model.dto.SmtpConnection connection,
                String recipient, String subject, String body) {
            int marker = body.indexOf("token=");
            if (marker >= 0) activationToken = body.substring(marker + "token=".length()).trim();
            if (subject.startsWith("Reset your")) resetMessageCount++;
        }
    }
}
