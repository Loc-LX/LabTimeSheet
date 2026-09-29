package com.lab.labtimesheet.feature.identity.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.identity.model.AccountStatus;
import com.lab.labtimesheet.feature.identity.model.TokenPurpose;
import com.lab.labtimesheet.feature.identity.model.dto.AccountCreation;
import com.lab.labtimesheet.feature.identity.model.dto.CreateAccountCommand;
import com.lab.labtimesheet.feature.identity.model.entity.UserActionToken;
import com.lab.labtimesheet.feature.identity.repository.AppUserRepository;
import com.lab.labtimesheet.feature.identity.repository.UserActionTokenRepository;
import com.lab.labtimesheet.feature.internship.service.InternshipService;
import com.lab.labtimesheet.platform.model.GlobalRole;
import com.lab.labtimesheet.platform.model.SecurityMode;
import com.lab.labtimesheet.platform.model.dto.SmtpConnection;
import com.lab.labtimesheet.platform.model.dto.SmtpDraft;
import com.lab.labtimesheet.platform.service.SmtpConfigurationService;
import com.lab.labtimesheet.platform.service.SmtpProbe;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * PostgreSQL and full Spring context integration test for secret activation and password-reset link delivery boundaries.
 *
 * <p>Protects NOT-008 and AC-NOT-003:
 * <ul>
 *   <li>Activation and password-reset links are sent directly and never routed through the ordinary {@code notifications} outbox.</li>
 *   <li>When email delivery fails, the issued token is immediately invalidated with {@code invalidated_at != null} and subsequent consumption is refused.</li>
 *   <li>Explicit regeneration via {@code resendActivation} or a repeated reset request issues a fresh usable token while retaining the invalidation of the failed token.</li>
 *   <li>No raw token, {@code /activate}, or {@code /reset} route is persisted in the {@code notifications} table.</li>
 *   <li>Application logs captured via {@link OutputCaptureExtension} never contain raw bearer tokens.</li>
 * </ul>
 *
 * <p>Observable break: If delivery failure does not invalidate tokens, unreceived tokens remain usable without explicit regeneration;
 * if secret links are logged or stored in {@code notifications}, credentials leak.
 * Hand-derived expected values:
 * <ul>
 *   <li>Account creation with failed delivery: {@code deliverySucceeded() = false}, token has non-null {@code invalidated_at}, {@code activate(failedToken) = false}.</li>
 *   <li>Explicit resend with working delivery: {@code deliverySucceeded() = true}, new token activates account to {@code ACTIVE}, old token remains invalid.</li>
 *   <li>Password reset with failed delivery: token has non-null {@code invalidated_at}, {@code resetPassword(failedToken) = false}.</li>
 *   <li>Repeated password reset with working delivery: new token resets password, old token remains invalid, and the stored hash matches the new password.</li>
 *   <li>Zero occurrences of raw tokens, {@code /activate}, or {@code /reset} across {@code notifications} columns and application logs.</li>
 * </ul>
 */
@Import({TestcontainersConfiguration.class, SecretLinkDeliveryIntegrationTest.MailProbeConfiguration.class})
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@ExtendWith(OutputCaptureExtension.class)
class SecretLinkDeliveryIntegrationTest {

    @Autowired
    private BootstrapService bootstrap;

    @Autowired
    private AccountService accounts;

    @Autowired
    private InternshipService internships;

    @Autowired
    private SmtpConfigurationService smtp;

    @Autowired
    private UserActionTokenRepository tokens;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private PasswordEncoder passwords;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private RecordingSmtpProbe mail;

    @Test
    void acNot003SecretLinkDeliveryFailureInvalidatesTokenAndExcludesRawLinkFromPersistenceAndLogs(
            CapturedOutput output) {
        bootstrap.bootstrap("secret-admin@example.com", "Admin", "correct horse battery staple");
        long adminId = accounts.requireActiveAdminId("secret-admin@example.com");
        activateSmtp(adminId);
        mail.clear();

        // --- Case a: Tạo tài khoản khi gửi mail lỗi ---
        mail.setFail(true);
        var command = new CreateAccountCommand(
                "secret-intern@example.com", "Secret Intern", GlobalRole.INTERN, "STU-SEC-01",
                LocalDate.of(2026, 8, 15), LocalDate.of(2026, 12, 31));
        AccountCreation creation = internships.create(command, adminId);
        assertThat(creation.deliverySucceeded()).isFalse();

        String failedRawActivationToken = mail.lastToken();
        assertThat(failedRawActivationToken).isNotBlank();

        List<UserActionToken> activationTokens = tokens.findAll().stream()
                .filter(t -> t.getUserId().equals(creation.userId()) && t.getPurpose() == TokenPurpose.ACTIVATION)
                .toList();
        assertThat(activationTokens).hasSize(1);
        UserActionToken failedActivationTokenEntity = activationTokens.getFirst();
        assertThat(failedActivationTokenEntity.getInvalidatedAt()).isNotNull();

        assertThat(accounts.activate(failedRawActivationToken, "InitialPassword123!")).isFalse();
        assertThat(users.findById(creation.userId()).orElseThrow().getAccountStatus())
                .isEqualTo(AccountStatus.PENDING_ACTIVATION);

        // Sau đó gọi resendActivation khi gửi mail thành công
        mail.clear();
        mail.setFail(false);
        AccountCreation resendCreation = accounts.resendActivation(creation.userId(), adminId);
        assertThat(resendCreation.deliverySucceeded()).isTrue();

        String newRawActivationToken = mail.lastToken();
        assertThat(newRawActivationToken).isNotBlank().isNotEqualTo(failedRawActivationToken);

        // Token cũ vẫn bị vô hiệu
        assertThat(accounts.activate(failedRawActivationToken, "InitialPassword123!")).isFalse();

        // Có token mới dùng được
        assertThat(accounts.activate(newRawActivationToken, "InitialPassword123!")).isTrue();
        assertThat(users.findById(creation.userId()).orElseThrow().getAccountStatus())
                .isEqualTo(AccountStatus.ACTIVE);

        // --- Case b: Yêu cầu đặt lại mật khẩu khi gửi mail lỗi ---
        mail.clear();
        mail.setFail(true);
        boolean resetRequested = accounts.requestPasswordReset("secret-intern@example.com");
        assertThat(resetRequested).isTrue();

        String failedRawResetToken = mail.lastToken();
        assertThat(failedRawResetToken).isNotBlank();

        List<UserActionToken> resetTokens = tokens.findAll().stream()
                .filter(t -> t.getUserId().equals(creation.userId()) && t.getPurpose() == TokenPurpose.PASSWORD_RESET)
                .toList();
        assertThat(resetTokens).hasSize(1);
        UserActionToken failedResetTokenEntity = resetTokens.getFirst();
        assertThat(failedResetTokenEntity.getInvalidatedAt()).isNotNull();

        // resetPassword bằng token đó trả false
        assertThat(accounts.resetPassword(failedRawResetToken, "ReplacementPassword123!")).isFalse();

        // Yêu cầu lại khi gửi thành công
        mail.clear();
        mail.setFail(false);
        boolean secondResetRequested = accounts.requestPasswordReset("secret-intern@example.com");
        assertThat(secondResetRequested).isTrue();

        String newRawResetToken = mail.lastToken();
        assertThat(newRawResetToken).isNotBlank().isNotEqualTo(failedRawResetToken);

        // Token cũ vẫn trả false
        assertThat(accounts.resetPassword(failedRawResetToken, "ReplacementPassword123!")).isFalse();

        // Token mới dùng được
        assertThat(accounts.resetPassword(newRawResetToken, "ReplacementPassword123!")).isTrue();
        assertThat(passwords.matches(
                "ReplacementPassword123!",
                users.findById(creation.userId()).orElseThrow().getPasswordHash()))
                .isTrue();

        // --- Case c: Trong cả a và b: không dòng nào trong notifications chứa token thô, /activate hay /reset ---
        List<Map<String, Object>> notificationRows = jdbc.queryForList(
                "select title, body, email_subject, email_body from notifications");
        for (Map<String, Object> row : notificationRows) {
            for (String col : List.of("title", "body", "email_subject", "email_body")) {
                Object val = row.get(col);
                if (val instanceof String text) {
                    assertThat(text)
                            .doesNotContain(failedRawActivationToken)
                            .doesNotContain(newRawActivationToken)
                            .doesNotContain(failedRawResetToken)
                            .doesNotContain(newRawResetToken)
                            .doesNotContain("/activate")
                            .doesNotContain("/reset");
                }
            }
        }

        // Log bắt bằng OutputCaptureExtension không chứa token thô
        String logs = output.getAll();
        assertThat(logs)
                .doesNotContain(failedRawActivationToken)
                .doesNotContain(newRawActivationToken)
                .doesNotContain(failedRawResetToken)
                .doesNotContain(newRawResetToken);
    }

    private void activateSmtp(long adminId) {
        long draftId = smtp.saveDraft(adminId, new SmtpDraft(
                "mailpit", 1025, SecurityMode.NONE, null, null, "secret-admin@example.com", "Lab Timesheet"));
        smtp.testDraft(draftId, adminId, "secret-admin@example.com");
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
        private volatile boolean fail;

        @Override
        public synchronized void send(SmtpConnection connection, String recipient, String subject, String body) {
            messages.add(new Message(recipient, subject, body));
            if (fail) {
                throw new IllegalStateException("simulated SMTP delivery failure");
            }
        }

        synchronized void clear() {
            messages.clear();
            fail = false;
        }

        synchronized void setFail(boolean fail) {
            this.fail = fail;
        }

        synchronized String lastToken() {
            assertThat(messages).isNotEmpty();
            String body = messages.getLast().body();
            int tokenStart = body.indexOf("token=");
            assertThat(tokenStart).isGreaterThanOrEqualTo(0);
            return body.substring(tokenStart + "token=".length()).trim();
        }
    }

    record Message(String recipient, String subject, String body) {
    }
}
