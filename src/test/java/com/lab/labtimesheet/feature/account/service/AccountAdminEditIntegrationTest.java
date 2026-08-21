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
import com.lab.labtimesheet.feature.account.model.entity.AccountAdminEditEvent;
import com.lab.labtimesheet.feature.account.model.entity.AccountAdminEditField;
import com.lab.labtimesheet.feature.account.repository.AccountAdminEditEventRepository;
import com.lab.labtimesheet.feature.account.repository.AppUserRepository;
import com.lab.labtimesheet.feature.account.repository.InternProfileRepository;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@Import({TestcontainersConfiguration.class, AccountAdminEditIntegrationTest.MailProbeConfiguration.class})
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AccountAdminEditIntegrationTest {
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
    private AccountAdminEditEventRepository editEvents;

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
    void adminEditsMentorEmailAndDisplayNameAndAppendsAuditRows() {
        var before = accounts.requireAccountDetail(mentorId);

        var updated = accounts.updateAccountAdminFields(
                mentorId, adminId, before.userVersion(), null,
                "mentor-new@example.com", "Mentor Renamed", null, null, null);

        assertThat(updated.email()).isEqualTo("mentor-new@example.com");
        assertThat(updated.displayName()).isEqualTo("Mentor Renamed");
        assertThat(updated.role()).isEqualTo(GlobalRole.MENTOR);
        assertThat(updated.status()).isEqualTo(AccountStatus.ACTIVE);

        var mentor = users.findById(mentorId).orElseThrow();
        assertThat(mentor.getEmail()).isEqualTo("mentor-new@example.com");
        assertThat(mentor.getDisplayName()).isEqualTo("Mentor Renamed");

        var events = editEvents.findByTargetUserIdOrderByOccurredAtAscIdAsc(mentorId);
        assertThat(events).hasSize(2);
        assertThat(events).extracting(AccountAdminEditEvent::getField)
                .containsExactly(AccountAdminEditField.EMAIL, AccountAdminEditField.DISPLAY_NAME);
        assertThat(events).extracting(AccountAdminEditEvent::getActorUserId).containsOnly(adminId);
        assertThat(events.get(0).getOldValue()).isEqualTo("mentor@example.com");
        assertThat(events.get(0).getNewValue()).isEqualTo("mentor-new@example.com");
        assertThat(events.get(1).getOldValue()).isEqualTo("Mentor One");
        assertThat(events.get(1).getNewValue()).isEqualTo("Mentor Renamed");
    }

    @Test
    void adminEditsInternFieldsAndAppendsAuditRows() {
        var before = accounts.requireAccountDetail(internId);

        var updated = accounts.updateAccountAdminFields(
                internId, adminId, before.userVersion(), before.profileVersion(),
                "intern-new@example.com", "Intern Renamed", "STU-999",
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 12, 15));

        assertThat(updated.email()).isEqualTo("intern-new@example.com");
        assertThat(updated.displayName()).isEqualTo("Intern Renamed");
        assertThat(updated.studentCode()).isEqualTo("STU-999");
        assertThat(updated.internshipStartDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(updated.internshipEndDate()).isEqualTo(LocalDate.of(2026, 12, 15));

        var profile = internProfiles.findById(internId).orElseThrow();
        assertThat(profile.getStudentCode()).isEqualTo("STU-999");
        assertThat(profile.getInternshipStartDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(profile.getInternshipEndDate()).isEqualTo(LocalDate.of(2026, 12, 15));

        var events = editEvents.findByTargetUserIdOrderByOccurredAtAscIdAsc(internId);
        assertThat(events).hasSize(5);
        assertThat(events).extracting(AccountAdminEditEvent::getField)
                .containsExactly(
                        AccountAdminEditField.EMAIL,
                        AccountAdminEditField.DISPLAY_NAME,
                        AccountAdminEditField.STUDENT_CODE,
                        AccountAdminEditField.INTERNSHIP_START,
                        AccountAdminEditField.INTERNSHIP_END);
    }

    @Test
    void staleUserVersionRejectsEditWithoutSideEffects() {
        var before = accounts.requireAccountDetail(mentorId);
        accounts.updateAccountAdminFields(
                mentorId, adminId, before.userVersion(), null,
                "first@example.com", "First Change", null, null, null);

        var after = accounts.requireAccountDetail(mentorId);
        assertThatThrownBy(() -> accounts.updateAccountAdminFields(
                mentorId, adminId, before.userVersion(), null,
                "second@example.com", "Stale Change", null, null, null))
                .isInstanceOf(IllegalStateException.class);

        assertThat(accounts.requireAccountDetail(mentorId).email()).isEqualTo("first@example.com");
        assertThat(editEvents.findByTargetUserIdOrderByOccurredAtAscIdAsc(mentorId)).hasSize(2);
    }

    @Test
    void duplicateEmailAndStudentCodeRejectEditWithoutSideEffects() {
        var secondIntern = accounts.create(new CreateAccountCommand(
                "second@example.com", "Second Intern", GlobalRole.INTERN, "STU-002",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 12, 31)), adminId);
        accounts.activate(mail.onlyActivationToken(), "new secure second password");
        long secondInternId = secondIntern.userId();

        var mentorBefore = accounts.requireAccountDetail(mentorId);
        assertThatThrownBy(() -> accounts.updateAccountAdminFields(
                mentorId, adminId, mentorBefore.userVersion(), null,
                "intern@example.com", "Mentor Renamed", null, null, null))
                .isInstanceOf(DataIntegrityViolationException.class);

        var internBefore = accounts.requireAccountDetail(internId);
        assertThatThrownBy(() -> accounts.updateAccountAdminFields(
                internId, adminId, internBefore.userVersion(), internBefore.profileVersion(),
                "intern@example.com", "Intern Renamed", "STU-002",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 12, 31)))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(accounts.requireAccountDetail(mentorId).email()).isEqualTo("mentor@example.com");
        assertThat(editEvents.findByTargetUserIdOrderByOccurredAtAscIdAsc(mentorId)).isEmpty();
        assertThat(editEvents.findByTargetUserIdOrderByOccurredAtAscIdAsc(internId)).isEmpty();
        assertThat(editEvents.findByTargetUserIdOrderByOccurredAtAscIdAsc(secondInternId)).isEmpty();
        assertThat(internProfiles.findById(internId).orElseThrow().getStudentCode()).isEqualTo("STU-001");
    }

    @Test
    void nonAdminCannotEditAccountFields() {
        var before = accounts.requireAccountDetail(mentorId);
        assertThatThrownBy(() -> accounts.updateAccountAdminFields(
                mentorId, mentorId, before.userVersion(), null,
                "hacked@example.com", "Hacked", null, null, null))
                .isInstanceOf(IllegalArgumentException.class);
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