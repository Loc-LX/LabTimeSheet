package com.lab.labtimesheet.feature.internship.service;

import com.lab.labtimesheet.feature.identity.service.AccountService;
import com.lab.labtimesheet.feature.identity.service.BootstrapService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.identity.model.AccountStatus;
import com.lab.labtimesheet.feature.identity.model.dto.AccountDirectoryFilter;
import com.lab.labtimesheet.feature.identity.model.dto.AccountIdentityCorrection;
import com.lab.labtimesheet.feature.identity.model.dto.CreateAccountCommand;
import com.lab.labtimesheet.feature.identity.repository.AppUserRepository;
import com.lab.labtimesheet.feature.internship.repository.InternProfileRepository;
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
    private InternshipService internships;

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
        createPendingMentor(adminId, "mentor.directory@example.com", "Professor North");
        createPendingIntern(adminId, "intern.directory@example.com", "Student South", "STU-42");

        assertThat(internships.administrationViews(adminId, new AccountDirectoryFilter("  DIRECTORY@EXAMPLE.COM ", null)))
                .extracting(view -> view.email())
                .containsExactly("mentor.directory@example.com", "intern.directory@example.com");
        assertThat(internships.administrationViews(adminId, new AccountDirectoryFilter(" professor ", null)))
                .extracting(view -> view.displayName())
                .containsExactly("Professor North");
        assertThat(internships.administrationViews(adminId, new AccountDirectoryFilter(" stu-42 ", GlobalRole.INTERN)))
                .extracting(view -> view.email())
                .containsExactly("intern.directory@example.com");
        assertThat(internships.administrationViews(adminId, new AccountDirectoryFilter("directory", GlobalRole.MENTOR)))
                .extracting(view -> view.role())
                .containsExactly(GlobalRole.MENTOR);
    }

    /**
     * Protects ACC-017's OR search and ARC-010's bounded directory composition. If either the identity or Student
     * Code side is dropped, one of the first two accounts disappears; if the two sides are concatenated, the third
     * account appears twice. The expected order is the hand-derived ascending account-ID order of the three creates.
     */
    @Test
    void directorySearchUnionsIdentityAndStudentCodeMatchesWithoutDuplicates() {
        bootstrap.bootstrap(ADMIN_EMAIL, "Primary Admin", ADMIN_PASSWORD);
        long adminId = accounts.requireActiveAdminId(ADMIN_EMAIL);
        enableSmtp(adminId);
        createPendingMentor(adminId, "union-key.mentor@example.com", "Identity Match");
        createPendingIntern(adminId, "profile-only@example.com", "Profile Match", "UNION-KEY");
        createPendingIntern(adminId, "union-key.intern@example.com", "Both Match", "UNION-KEY-BOTH");

        assertThat(internships.administrationViews(adminId, new AccountDirectoryFilter("union-key", null)))
                .extracting(view -> view.email())
                .containsExactly(
                        "union-key.mentor@example.com",
                        "profile-only@example.com",
                        "union-key.intern@example.com");
    }

    @Test
    void pendingEmailCorrectionReplacesActivationOnlyAfterRequiredDelivery() {
        bootstrap.bootstrap(ADMIN_EMAIL, "Primary Admin", ADMIN_PASSWORD);
        long adminId = accounts.requireActiveAdminId(ADMIN_EMAIL);
        enableSmtp(adminId);
        long userId = createPendingIntern(adminId, "pending.old@example.com", "Pending Intern", "STU-OLD");
        String oldToken = mail.lastToken();
        mail.clear();

        internships.correctAccount(userId, adminId, new AccountIdentityCorrection(
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

        internships.correctAccount(userId, adminId, new AccountIdentityCorrection(
                "active.new@example.com", null, null, null));
        assertThat(users.findById(userId).orElseThrow().getEmail()).isEqualTo("active.new@example.com");
        assertThat(sessions.getSessionInformation("session-1").isExpired()).isTrue();
        assertThat(mail.lastRecipient()).isEqualTo("active.new@example.com");

        accounts.lockAccount(userId, adminId);
        sessions.registerNewSession("session-2", User.withUsername("active.new@example.com")
                .password("unused")
                .roles("MENTOR")
                .build());
        internships.correctAccount(userId, adminId, new AccountIdentityCorrection(
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

        assertThatThrownBy(() -> internships.correctAccount(userId, adminId, new AccountIdentityCorrection(
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
        internships.activateInternship(userId, adminId);

        internships.correctAccount(userId, adminId, new AccountIdentityCorrection(
                null, "STU-ACTIVE", null, null));
        assertThat(internProfiles.findById(userId).orElseThrow().getStudentCode()).isEqualTo("STU-ACTIVE");
        assertThatThrownBy(() -> internships.correctAccount(userId, adminId, new AccountIdentityCorrection(
                null, null, LocalDate.of(2026, 8, 2), LocalDate.of(2026, 12, 31))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NOT_STARTED");
        accounts.deactivateAccount(userId, adminId);
        assertThatThrownBy(() -> internships.correctAccount(userId, adminId, new AccountIdentityCorrection(
                "terminal.new@example.com", null, null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("read-only");
    }

    @Test
    void omittedInternshipDateIsResolvedFromTheLockedProfile() {
        bootstrap.bootstrap(ADMIN_EMAIL, "Primary Admin", ADMIN_PASSWORD);
        long adminId = accounts.requireActiveAdminId(ADMIN_EMAIL);
        enableSmtp(adminId);
        long userId = createPendingIntern(adminId, "date-omitted@example.com", "Date Omitted", "STU-DATE");

        internships.correctAccount(userId, adminId, new AccountIdentityCorrection(
                null, null, LocalDate.of(2026, 8, 2), null));

        var profile = internProfiles.findById(userId).orElseThrow();
        assertThat(profile.getInternshipStartDate()).isEqualTo(LocalDate.of(2026, 8, 2));
        assertThat(profile.getInternshipEndDate()).isEqualTo(LocalDate.of(2026, 12, 31));
    }

    @Test
    void uniquenessFailureDoesNotDeliverOrExpireSessions() {
        bootstrap.bootstrap(ADMIN_EMAIL, "Primary Admin", ADMIN_PASSWORD);
        long adminId = accounts.requireActiveAdminId(ADMIN_EMAIL);
        enableSmtp(adminId);
        long firstId = createActiveIntern(adminId, "first-unique@example.com", "First Unique", "STU-UNIQUE");
        long secondId = createActiveIntern(adminId, "second-unique@example.com", "Second Unique", "STU-OTHER");
        sessions.registerNewSession("unique-session", User.withUsername("second-unique@example.com")
                .password("unused")
                .roles("INTERN")
                .build());
        mail.clear();

        assertThatThrownBy(() -> internships.correctAccount(secondId, adminId, new AccountIdentityCorrection(
                "second-corrected@example.com", "STU-UNIQUE", null, null)))
                .isInstanceOf(RuntimeException.class);

        assertThat(mail.messages).isEmpty();
        assertThat(sessions.getSessionInformation("unique-session").isExpired()).isFalse();
        assertThat(users.findById(secondId).orElseThrow().getEmail()).isEqualTo("second-unique@example.com");
        assertThat(internProfiles.findById(secondId).orElseThrow().getStudentCode()).isEqualTo("STU-OTHER");
        assertThat(users.findById(firstId)).isPresent();
    }

    @Test
    void nonAdminAndGuessedTargetCannotUseCorrectionBoundary() {
        bootstrap.bootstrap(ADMIN_EMAIL, "Primary Admin", ADMIN_PASSWORD);
        long adminId = accounts.requireActiveAdminId(ADMIN_EMAIL);
        enableSmtp(adminId);
        long mentorId = createActiveMentor(adminId, "non-admin@example.com", "Non Admin");
        long targetId = createPendingMentor(adminId, "target-guess@example.com", "Target Guess");

        assertThatThrownBy(() -> internships.correctAccount(targetId, mentorId, new AccountIdentityCorrection(
                "forbidden@example.com", null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("active Admin");
        assertThatThrownBy(() -> internships.correctAccount(999_999L, adminId, new AccountIdentityCorrection(
                "missing@example.com", null, null, null)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Account not found");
    }

    @Test
    void completedAndWithdrawnInternProfilesRemainReadOnly() {
        bootstrap.bootstrap(ADMIN_EMAIL, "Primary Admin", ADMIN_PASSWORD);
        long adminId = accounts.requireActiveAdminId(ADMIN_EMAIL);
        enableSmtp(adminId);
        long completedId = createActiveIntern(adminId, "completed@example.com", "Completed Intern", "STU-COMPLETE");
        internships.completeInternship(completedId, adminId);
        assertThatThrownBy(() -> internships.correctAccount(completedId, adminId, new AccountIdentityCorrection(
                null, "STU-COMPLETE-NEW", null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("read-only");

        long withdrawnId = createActiveIntern(adminId, "withdrawn@example.com", "Withdrawn Intern", "STU-WITHDRAW");
        internships.withdrawInternship(withdrawnId, adminId);
        assertThatThrownBy(() -> internships.correctAccount(withdrawnId, adminId, new AccountIdentityCorrection(
                null, "STU-WITHDRAW-NEW", null, null)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("read-only");
    }

    private long createPendingMentor(long adminId, String email, String name) {
        long id = internships.create(new CreateAccountCommand(email, name, GlobalRole.MENTOR, null, null, null), adminId)
                .userId();
        mail.clear();
        return id;
    }

    private long createPendingIntern(long adminId, String email, String name, String studentCode) {
        long id = internships.create(new CreateAccountCommand(email, name, GlobalRole.INTERN, studentCode,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 12, 31)), adminId).userId();
        return id;
    }

    private long createActiveMentor(long adminId, String email, String name) {
        long id = internships.create(new CreateAccountCommand(email, name, GlobalRole.MENTOR, null, null, null), adminId)
                .userId();
        accounts.activate(mail.lastToken(), "mentor secure password");
        mail.clear();
        return id;
    }

    private long createActiveIntern(long adminId, String email, String name, String studentCode) {
        long id = createPendingIntern(adminId, email, name, studentCode);
        accounts.activate(mail.lastToken(), "intern secure password");
        internships.activateInternship(id, adminId);
        mail.clear();
        return id;
    }

    private void enableSmtp(long adminId) {
        long draftId = smtp.saveDraft(adminId, new SmtpDraft(
                "mailpit", 1025, SecurityMode.NONE, null, null, ADMIN_EMAIL, "Lab Timesheet"));
        smtp.testDraft(draftId, adminId, ADMIN_EMAIL);
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
