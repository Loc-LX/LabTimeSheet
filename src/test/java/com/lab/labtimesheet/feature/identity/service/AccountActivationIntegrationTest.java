package com.lab.labtimesheet.feature.identity.service;

import com.lab.labtimesheet.feature.internship.service.InternshipService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.identity.model.AccountStatus;
import com.lab.labtimesheet.feature.internship.model.InternshipStatus;
import com.lab.labtimesheet.feature.identity.model.TokenPurpose;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@Import({TestcontainersConfiguration.class, AccountActivationIntegrationTest.MailProbeConfiguration.class})
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AccountActivationIntegrationTest {

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
    private PasswordEncoder passwords;

    @Test
    void smtpGatedCreationHashesSingleUseActivationAndRetainsFailedDeliveryHistory() throws Exception {
        bootstrap.bootstrap("admin@example.com", "Admin", "correct horse battery staple");
        long adminId = accounts.requireActiveAdminId("admin@example.com");

        var mentor = new CreateAccountCommand(
                " MENTOR@EXAMPLE.COM ", " Mentor One ", GlobalRole.MENTOR, null, null, null);
        assertThatThrownBy(() -> internships.create(mentor, adminId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SMTP");
        assertThat(users.count()).isEqualTo(1);

        activateSmtp(adminId);
        mail.messages.clear();

        var mentorCreation = internships.create(mentor, adminId);
        assertThat(mentorCreation.deliverySucceeded()).isTrue();
        var pendingMentor = users.findById(mentorCreation.userId()).orElseThrow();
        assertThat(pendingMentor.getEmail()).isEqualTo("mentor@example.com");
        assertThat(pendingMentor.getDisplayName()).isEqualTo("Mentor One");
        assertThat(pendingMentor.getGlobalRole()).isEqualTo(GlobalRole.MENTOR);
        assertThat(pendingMentor.getAccountStatus()).isEqualTo(AccountStatus.PENDING_ACTIVATION);
        assertThat(pendingMentor.getPasswordHash()).isNull();

        String rawMentorToken = mail.onlyActivationToken();
        var mentorToken = tokens.findAll().stream()
                .filter(token -> token.getUserId().equals(mentorCreation.userId()))
                .findFirst()
                .orElseThrow();
        assertThat(mentorToken.getPurpose()).isEqualTo(TokenPurpose.ACTIVATION);
        assertThat(mentorToken.getTokenHash()).containsExactly(sha256(rawMentorToken));
        assertThat(mentorToken.getExpiresAt()).isEqualTo(Instant.parse("2026-08-15T00:00:00Z"));
        assertThat(mentorToken.isUsableAt(mentorToken.getExpiresAt())).isFalse();
        assertThat(HexFormat.of().formatHex(mentorToken.getTokenHash())).doesNotContain(rawMentorToken);

        assertThat(accounts.activate("not-the-token", "new secure mentor password")).isFalse();
        assertThat(accounts.activate(rawMentorToken, "new secure mentor password")).isTrue();
        assertThat(accounts.activate(rawMentorToken, "another secure password")).isFalse();
        var activeMentor = users.findById(mentorCreation.userId()).orElseThrow();
        assertThat(activeMentor.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(passwords.matches("new secure mentor password", activeMentor.getPasswordHash())).isTrue();
        assertThat(tokens.findById(mentorToken.getId()).orElseThrow().getUsedAt()).isNotNull();

        mail.fail = true;
        var failedIntern = internships.create(new CreateAccountCommand(
                "intern-failed@example.com", "Failed Intern", GlobalRole.INTERN, "STU-FAIL",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 12, 31)), adminId);
        assertThat(failedIntern.deliverySucceeded()).isFalse();
        assertThat(users.findById(failedIntern.userId()).orElseThrow().getAccountStatus())
                .isEqualTo(AccountStatus.PENDING_ACTIVATION);
        assertThat(tokens.findAll().stream()
                .filter(token -> token.getUserId().equals(failedIntern.userId()))
                .findFirst().orElseThrow().getInvalidatedAt()).isNotNull();

        mail.fail = false;
        mail.messages.clear();
        var activeInternCreation = internships.create(new CreateAccountCommand(
                "intern@example.com", "Active Intern", GlobalRole.INTERN, "STU-001",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 12, 31)), adminId);
        assertThat(accounts.activate(mail.onlyActivationToken(), "new secure intern password")).isTrue();
        internships.activateInternship(activeInternCreation.userId(), adminId);

        var profile = internProfiles.findById(activeInternCreation.userId()).orElseThrow();
        assertThat(profile.getInternshipStatus()).isEqualTo(InternshipStatus.ACTIVE);
        assertThat(internships.isEligibleIntern(activeInternCreation.userId(), LocalDate.of(2026, 8, 1))).isTrue();
        assertThat(internships.isEligibleIntern(activeInternCreation.userId(), LocalDate.of(2026, 12, 31))).isTrue();
        assertThat(internships.isEligibleIntern(activeInternCreation.userId(), LocalDate.of(2027, 1, 1))).isFalse();

        var summary = internships.summary();
        assertThat(summary.activeAccounts()).isEqualTo(3);
        assertThat(summary.pendingActivations()).isEqualTo(1);
        assertThat(summary.activeInternships()).isEqualTo(1);
    }

    @Test
    void internshipCannotActivateBeforeItsBusinessStartDate() {
        bootstrap.bootstrap("admin@example.com", "Admin", "correct horse battery staple");
        long adminId = accounts.requireActiveAdminId("admin@example.com");
        activateSmtp(adminId);
        mail.messages.clear();

        var creation = internships.create(new CreateAccountCommand(
                "future-intern@example.com", "Future Intern", GlobalRole.INTERN, "STU-FUTURE",
                LocalDate.of(2026, 8, 15), LocalDate.of(2026, 12, 31)), adminId);
        assertThat(accounts.activate(mail.onlyActivationToken(), "future secure password")).isTrue();

        assertThatThrownBy(() -> internships.activateInternship(creation.userId(), adminId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("start date");
        assertThat(internProfiles.findById(creation.userId()).orElseThrow().getInternshipStatus())
                .isEqualTo(InternshipStatus.NOT_STARTED);
    }

    private void activateSmtp(long adminId) {
        long draftId = smtp.saveDraft(adminId, new SmtpDraft(
                "mailpit", 1025, SecurityMode.NONE, null, null, "admin@example.com", "Lab Timesheet"));
        smtp.testDraft(draftId, adminId, "admin@example.com");
        smtp.activate(draftId, adminId);
    }

    private static byte[] sha256(String value) throws Exception {
        return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
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
            assertThat(messages).hasSize(1);
            String body = messages.getFirst().body();
            int tokenStart = body.indexOf("token=");
            assertThat(tokenStart).isGreaterThanOrEqualTo(0);
            return body.substring(tokenStart + "token=".length()).trim();
        }
    }

    record Message(String recipient, String subject, String body) {
    }
}
