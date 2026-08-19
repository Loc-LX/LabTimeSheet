package com.lab.labtimesheet.feature.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.account.model.AccountStatus;
import com.lab.labtimesheet.feature.account.model.GlobalRole;
import com.lab.labtimesheet.feature.account.model.dto.CreateAccountCommand;
import com.lab.labtimesheet.feature.account.repository.AppUserRepository;
import com.lab.labtimesheet.feature.integration.model.SecurityMode;
import com.lab.labtimesheet.feature.integration.model.dto.SmtpConnection;
import com.lab.labtimesheet.feature.integration.model.dto.SmtpDraft;
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
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@Import({TestcontainersConfiguration.class, AccountLifecycleIntegrationTest.MailProbeConfiguration.class})
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AccountLifecycleIntegrationTest {
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
    private UserDetailsService userDetails;

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
    void lockSetsLockedAtDeniesAuthenticationAndUnlockPreservesRoleAndCredentials() {
        String passwordHash = users.findById(mentorId).orElseThrow().getPasswordHash();

        accounts.lockAccount(mentorId, adminId);
        var locked = users.findById(mentorId).orElseThrow();
        assertThat(locked.getAccountStatus()).isEqualTo(AccountStatus.LOCKED);
        assertThat(locked.getLockedAt()).isNotNull();
        assertThat(locked.getDeactivatedAt()).isNull();
        assertThat(userDetails.loadUserByUsername("mentor@example.com").isEnabled()).isFalse();

        accounts.unlockAccount(mentorId, adminId);
        var unlocked = users.findById(mentorId).orElseThrow();
        assertThat(unlocked.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(unlocked.getLockedAt()).isNull();
        assertThat(unlocked.getGlobalRole()).isEqualTo(GlobalRole.MENTOR);
        assertThat(unlocked.getPasswordHash()).isEqualTo(passwordHash);
        assertThat(userDetails.loadUserByUsername("mentor@example.com").isEnabled()).isTrue();
    }

    @Test
    void deactivateDeniesAuthenticationButKeepsHistoricalAttributionVisible() {
        accounts.deactivateAccount(mentorId, adminId);
        var deactivated = users.findById(mentorId).orElseThrow();
        assertThat(deactivated.getAccountStatus()).isEqualTo(AccountStatus.DEACTIVATED);
        assertThat(deactivated.getDeactivatedAt()).isNotNull();
        assertThat(deactivated.getGlobalRole()).isEqualTo(GlobalRole.MENTOR);
        assertThat(deactivated.getPasswordHash()).isNotBlank();
        assertThat(userDetails.loadUserByUsername("mentor@example.com").isEnabled()).isFalse();

        var identity = accounts.requireIdentityById(mentorId);
        assertThat(identity.email()).isEqualTo("mentor@example.com");
        assertThat(identity.role()).isEqualTo(GlobalRole.MENTOR);
        assertThat(identity.status()).isEqualTo(AccountStatus.DEACTIVATED);
    }

    @Test
    void lifecycleTransitionsRejectInvalidSourceStates() {
        assertThatThrownBy(() -> accounts.unlockAccount(internId, adminId))
                .isInstanceOf(IllegalArgumentException.class);

        accounts.lockAccount(mentorId, adminId);
        assertThatThrownBy(() -> accounts.lockAccount(mentorId, adminId))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> accounts.deactivateAccount(mentorId, adminId))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> accounts.unlockAccount(internId, adminId))
                .isInstanceOf(IllegalArgumentException.class);

        assertThatThrownBy(() -> accounts.lockAccount(99_999_999L, adminId))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void lockAndDeactivateExpireTheAffectedUsersExistingSessions() {
        sessionRegistry.registerNewSession("mentor-session-1", "mentor@example.com");
        sessionRegistry.registerNewSession("mentor-session-2", "mentor@example.com");
        sessionRegistry.registerNewSession("intern-session-1", "intern@example.com");

        accounts.lockAccount(mentorId, adminId);
        assertThat(sessionInformation("mentor-session-1").isExpired()).isTrue();
        assertThat(sessionInformation("mentor-session-2").isExpired()).isTrue();
        assertThat(sessionInformation("intern-session-1").isExpired()).isFalse();

        accounts.deactivateAccount(internId, adminId);
        assertThat(sessionInformation("intern-session-1").isExpired()).isTrue();
    }

    private SessionInformation sessionInformation(String sessionId) {
        SessionInformation information = sessionRegistry.getSessionInformation(sessionId);
        assertThat(information).isNotNull();
        return information;
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

        @Override
        public void send(SmtpConnection connection, String recipient, String subject, String body) {
            messages.add(new Message(recipient, subject, body));
        }

        String onlyActivationToken() {
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