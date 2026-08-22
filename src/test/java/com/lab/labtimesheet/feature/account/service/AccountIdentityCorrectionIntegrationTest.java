package com.lab.labtimesheet.feature.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.account.model.AccountStatus;
import com.lab.labtimesheet.feature.account.model.GlobalRole;
import com.lab.labtimesheet.feature.account.model.dto.AccountDirectoryFilter;
import com.lab.labtimesheet.feature.account.model.dto.AccountIdentityCorrection;
import com.lab.labtimesheet.feature.account.model.dto.CreateAccountCommand;
import com.lab.labtimesheet.feature.account.repository.AppUserRepository;
import com.lab.labtimesheet.feature.account.repository.InternProfileRepository;
import com.lab.labtimesheet.feature.account.repository.UserActionTokenRepository;
import com.lab.labtimesheet.feature.integration.model.SecurityMode;
import com.lab.labtimesheet.feature.integration.model.dto.SmtpConnection;
import com.lab.labtimesheet.feature.integration.model.dto.SmtpDraft;
import com.lab.labtimesheet.feature.integration.service.SmtpConfigurationService;
import com.lab.labtimesheet.feature.integration.service.SmtpProbe;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

/** PostgreSQL proof for the Admin account directory and controlled identity-correction boundary. */
@Import({TestcontainersConfiguration.class, AccountIdentityCorrectionIntegrationTest.MailConfiguration.class})
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AccountIdentityCorrectionIntegrationTest {
    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String ADMIN_PASSWORD = "correct horse battery staple";

    @Autowired
    private BootstrapService bootstrap;

    @Autowired
    private AccountService accounts;

    @Autowired
    private SmtpConfigurationService smtp;

    @Autowired
    private RecordingSmtpProbe mail;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private InternProfileRepository internProfiles;

    @Autowired
    private UserActionTokenRepository tokens;

    @Autowired
    private SessionRegistry sessions;

    @Test
    void directorySearchNormalizesDisplayEmailStudentCodeAndKeepsRoleFilterExact() {
        bootstrap.bootstrap(ADMIN_EMAIL, "Primary Admin", ADMIN_PASSWORD);
        long adminId = accounts.requireActiveAdminId(ADMIN_EMAIL);
        enableSmtp(adminId);
        createPendingMentor(adminId, "mentor.directory@example.com", "Mentor Directory");
        createPendingIntern(adminId, "intern.directory@example.com", "Intern Directory", "STU-42");

        assertThat(accounts.administrationViews(adminId, new AccountDirectoryFilter("  DIRECTORY@EXAMPLE.COM ", null)))
                .extracting(view -> view.email())
                .containsExactly("mentor.directory@example.com", "intern.directory@example.com");
        assertThat(accounts.administrationViews(adminId, new AccountDirectoryFilter(" stu-42 ", GlobalRole.INTERN)))
                .extracting(view -> view.email())
                .containsExactly("intern.directory@example.com");
        assertThat(accounts.administrationViews(adminId, new AccountDirectoryFilter("directory", GlobalRole.MENTOR)))
                .extracting(view -> view.role())
                .containsExactly(GlobalRole.MENTOR);
    }

    @Test
    void pendingEmailCorrectionReplacesActivationOnlyAfterRequiredDelivery() {
        bootstrap.bootstrap(ADMIN_EMAIL, "Primary Admin", ADMIN_PASSWORD);
        long adminId = accounts.requireActiveAdminId(ADMIN_EMAIL);
        enableSmtp(adminId);
        long userId = createPendingIntern(adminId, "pending.old@example.com", "Pending Intern", "STU-OLD");
        String oldToken = mail.lastToken();
        mail.clear();

        accounts.correctAccount(userId, adminId, new AccountIdentityCorrection(
                " pending.new@example.com ", null, null, null));

        String newToken = mail.lastToken();
        assertThat(newToken).isNotEqualTo(oldToken);
        assertThat(users.findById(userId).orElseThrow().getEmail()).isEqualTo("pending.new@example.com");
        assertThat(accounts.activate(oldToken, "pending secure password")).isFalse();
        assertThat(accounts.activate(newToken, "pending secure password")).isTrue();
        assertThat(tokens.findAll().stream()
                .filter(token -> token.getUserId().equals(userId))
                .filter(token -> token.getInvalidatedAt() != null)
                .count()).isEqualTo(1);
    }

    @Test
    void activeAndLockedEmailCorrectionDeliversNoticeAndExpiresOldSessions() {
        bootstrap.bootstrap(ADMIN_EMAIL, "Primary Admin", ADMIN_PASSWORD);
        long adminId = accounts.requireActiveAdminId(ADMIN_EMAIL);
        enableSmtp(adminId);
        long userId = createActiveMentor(adminId, "active.old@example.com", "Active Mentor");
        sessions.registerNewSession("session-1", User.withUsername("active.old@example.com")
                .password("unused")
                .roles("MENTOR")
                .build());

        accounts.correctAccount(userId, adminId, new AccountIdentityCorrection(
                "active.new@example.com", null, null, null));
        assertThat(users.findById(userId).orElseThrow().getEmail()).isEqualTo("active.new@example.com");
        assertThat(sessions.getSessionInformation("session-1").isExpired()).isTrue();
        assertThat(mail.lastRecipient()).isEqualTo("active.new@example.com");

        accounts.lockAccount(userId, adminId);
        sessions.registerNewSession("session-2", User.withUsername("active.new@example.com")
                .password("unused")
                .roles("MENTOR")
                .build());
        accounts.correctAccount(userId, adminId, new AccountIdentityCorrection(
                "locked.new@example.com", null, null, null));
        assertThat(users.findById(userId).orElseThrow().getAccountStatus()).isEqualTo(AccountStatus.LOCKED);
        assertThat(sessions.getSessionInformation("session-2").isExpired()).isTrue();
    }

    @Test
    void deliveryFailureLeavesIdentityAndInternProfileUnchanged() {
        bootstrap.bootstrap(ADMIN_EMAIL, "Primary Admin", ADMIN_PASSWORD);
        long adminId = accounts.requireActiveAdminId(ADMIN_EMAIL);
        enableSmtp(adminId);
        long userId = createPendingIntern(adminId, "rollback.old@example.com", "Rollback Intern", "STU-OLD");
        mail.fail = true;

        assertThatThrownBy(() -> accounts.correctAccount(userId, adminId, new AccountIdentityCorrection(
                "rollback.new@example.com", "STU-NEW", LocalDate.of(2026, 8, 2), LocalDate.of(2026, 12, 31))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("delivery");
        assertThat(users.findById(userId).orElseThrow().getEmail()).isEqualTo("rollback.old@example.com");
        var profile = internProfiles.findById(userId).orElseThrow();
        assertThat(profile.getStudentCode()).isEqualTo("STU-OLD");
        assertThat(profile.getInternshipStartDate()).isEqualTo(LocalDate.of(2026, 8, 1));
    }

    @Test
    void studentCodeAndDateCorrectionsRespectInternshipLifecycle() {
        bootstrap.bootstrap(ADMIN_EMAIL, "Primary Admin", ADMIN_PASSWORD);
        long adminId = accounts.requireActiveAdminId(ADMIN_EMAIL);
        enableSmtp(adminId);
        long userId = createPendingIntern(adminId, "lifecycle@example.com", "Lifecycle Intern", "STU-OLD");
        accounts.activate(mail.lastToken(), "lifecycle secure password");
        accounts.activateInternship(userId, adminId);

        accounts.correctAccount(userId, adminId, new AccountIdentityCorrection(
                null, "STU-ACTIVE", null, null));
        assertThat(internProfiles.findById(userId).orElseThrow().getStudentCode()).isEqualTo("STU-ACTIVE");
        assertThatThrownBy(() -> accounts.correctAccount(userId, adminId, new AccountIdentityCorrection(
                null, null, LocalDate.of(2026, 8, 2), LocalDate.of(2026, 12, 31))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NOT_STARTED");
        accounts.deactivateAccount(userId, adminId);
        assertThatThrownBy(() -> accounts.correctAccount(userId, adminId, new AccountIdentityCorrection(
                "terminal.new@example.com", null, null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("read-only");
    }

    private void createPendingMentor(long adminId, String email, String name) {
        accounts.create(new CreateAccountCommand(email, name, GlobalRole.MENTOR, null, null, null), adminId);
        mail.clear();
    }

    private long createPendingIntern(long adminId, String email, String name, String studentCode) {
        long id = accounts.create(new CreateAccountCommand(email, name, GlobalRole.INTERN, studentCode,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 12, 31)), adminId).userId();
        return id;
    }

    private long createActiveMentor(long adminId, String email, String name) {
        long id = accounts.create(new CreateAccountCommand(email, name, GlobalRole.MENTOR, null, null, null), adminId)
                .userId();
        accounts.activate(mail.lastToken(), "mentor secure password");
        mail.clear();
        return id;
    }

    private void enableSmtp(long adminId) {
        long draftId = smtp.saveDraft(adminId, new SmtpDraft(
                "mailpit", 1025, SecurityMode.NONE, null, null, ADMIN_EMAIL, "Lab Timesheet"));
        smtp.testDraft(draftId, adminId);
        smtp.activate(draftId, adminId);
        mail.clear();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MailConfiguration {
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

        String lastToken() {
            String body = messages.getLast().body();
            int marker = body.indexOf("token=");
            return marker < 0 ? null : body.substring(marker + "token=".length()).trim();
        }

        String lastRecipient() {
            return messages.getLast().recipient();
        }

        void clear() {
            messages.clear();
        }
    }

    record Message(String recipient, String subject, String body) {
    }
}
