package com.lab.labtimesheet.feature.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.account.model.GlobalRole;
import com.lab.labtimesheet.feature.account.model.InternshipStatus;
import com.lab.labtimesheet.feature.account.repository.AppUserRepository;
import com.lab.labtimesheet.feature.account.repository.InternProfileRepository;
import com.lab.labtimesheet.feature.account.model.dto.CreateAccountCommand;
import com.lab.labtimesheet.feature.integration.model.SecurityMode;
import com.lab.labtimesheet.feature.integration.model.dto.SmtpConnection;
import com.lab.labtimesheet.feature.integration.model.dto.SmtpDraft;
import com.lab.labtimesheet.feature.integration.service.SmtpConfigurationService;
import com.lab.labtimesheet.feature.integration.service.SmtpProbe;
import com.lab.labtimesheet.feature.project.model.dto.ProjectCreateCommand;
import com.lab.labtimesheet.feature.project.repository.ProjectRepository;
import com.lab.labtimesheet.feature.project.service.ProjectService;
import com.lab.labtimesheet.feature.task.model.dto.CreateTaskCommand;
import com.lab.labtimesheet.feature.task.service.TaskService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.transaction.support.TransactionTemplate;

@Import({TestcontainersConfiguration.class, InternshipLifecycleIntegrationTest.MailProbeConfiguration.class})
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class InternshipLifecycleIntegrationTest {

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
    private UserDetailsService userDetails;

    @Autowired
    private SessionRegistry sessionRegistry;

    @Autowired
    private InternshipStartScheduler scheduler;

    @Autowired
    private ProjectService projects;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private TaskService tasks;

    @Autowired
    private TransactionTemplate transactions;

    private long adminId;
    private long mentorId;
    private long internId;

    @BeforeEach
    void initializeAccounts() {
        bootstrap.bootstrap("admin@example.com", "Admin", "correct horse battery staple");
        adminId = accounts.requireActiveAdminId("admin@example.com");
        activateSmtp(adminId);
        mail.messages.clear();

        var mentor = accounts.create(new CreateAccountCommand(
                "mentor@example.com", "Mentor One", GlobalRole.MENTOR, null, null, null), adminId);
        accounts.activate(mail.onlyActivationToken(), "new secure mentor password");
        mentorId = mentor.userId();

        var intern = accounts.create(new CreateAccountCommand(
                "intern@example.com", "Intern One", GlobalRole.INTERN, "STU-001",
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 12, 31)), adminId);
        accounts.activate(mail.onlyActivationToken(), "new secure intern password");
        internId = intern.userId();
    }

    @Test
    void requestTimeEligibilityStartsDueInternshipAndSchedulerStartsTheSameRule() {
        assertThat(internProfiles.findById(internId).orElseThrow().getInternshipStatus())
                .isEqualTo(InternshipStatus.NOT_STARTED);

        assertThat(accounts.isEligibleIntern(internId)).isTrue();
        assertThat(internProfiles.findById(internId).orElseThrow().getInternshipStatus())
                .isEqualTo(InternshipStatus.ACTIVE);

        var future = accounts.create(new CreateAccountCommand(
                "future@example.com", "Future Intern", GlobalRole.INTERN, "STU-FUTURE",
                LocalDate.of(2026, 8, 21), LocalDate.of(2026, 12, 31)), adminId);
        accounts.activate(mail.onlyActivationToken(), "future secure password");
        assertThat(accounts.isEligibleIntern(future.userId())).isFalse();
        assertThat(internProfiles.findById(future.userId()).orElseThrow().getInternshipStatus())
                .isEqualTo(InternshipStatus.NOT_STARTED);

        scheduler.activateDueInternships();
        assertThat(internProfiles.findById(internId).orElseThrow().getInternshipStatus())
                .isEqualTo(InternshipStatus.ACTIVE);
    }

    @Test
    void completionKeepsAuthenticationButRemovesMutationEligibility() {
        accounts.isEligibleIntern(internId);

        accounts.completeInternship(internId, adminId);

        assertThat(internProfiles.findById(internId).orElseThrow().getInternshipStatus())
                .isEqualTo(InternshipStatus.COMPLETED);
        assertThat(userDetails.loadUserByUsername("intern@example.com").isEnabled()).isTrue();
        assertThat(accounts.isEligibleIntern(internId)).isFalse();
    }

    @Test
    void withdrawalDisablesAuthenticationAndExpiresExistingSessions() {
        sessionRegistry.registerNewSession("intern-session", "intern@example.com");

        accounts.withdrawInternship(internId, adminId);

        assertThat(internProfiles.findById(internId).orElseThrow().getInternshipStatus())
                .isEqualTo(InternshipStatus.WITHDRAWN);
        assertThat(userDetails.loadUserByUsername("intern@example.com").isEnabled()).isFalse();
        assertThat(sessionInformation("intern-session").isExpired()).isTrue();
    }

    @Test
    void completionIsBlockedWhileTheInternIsTheCurrentProjectLeader() {
        accounts.isEligibleIntern(internId);
        projects.create(mentorId, new ProjectCreateCommand(
                "Current Leader Project", null,
                LocalDate.of(2026, 8, 20), LocalDate.of(2026, 12, 31), internId));

        assertThatThrownBy(() -> accounts.completeInternship(internId, adminId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("leads a Project");
        assertThat(internProfiles.findById(internId).orElseThrow().getInternshipStatus())
                .isEqualTo(InternshipStatus.ACTIVE);
    }

    @Test
    void withdrawalIsBlockedWhileTheInternOwnsAnUnfinishedTask() {
        assertThat(accounts.isEligibleIntern(internId)).isTrue();
        long projectId = projects.create(mentorId, new ProjectCreateCommand(
                "Task Guard Project", null,
                LocalDate.of(2026, 8, 20), LocalDate.of(2026, 12, 31), internId));
        long replacementLeaderId = createActiveIntern(
                "replacement@example.com", "Replacement Intern", "STU-REPLACEMENT");
        assertThat(accounts.isEligibleIntern(replacementLeaderId)).isTrue();
        projects.addMember(mentorId, projectId, replacementLeaderId);
        projects.changeLeader(mentorId, projectId, replacementLeaderId);
        long membershipId = transactions.execute(status -> projectRepository.findById(projectId).orElseThrow()
                .memberships().stream()
                .filter(membership -> membership.internUserId() == internId)
                .findFirst()
                .orElseThrow()
                .id());
        tasks.create("intern@example.com", new CreateTaskCommand(
                projectId, membershipId, "Open task", null, null));

        assertThatThrownBy(() -> accounts.withdrawInternship(internId, adminId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unfinished Tasks");
        assertThat(internProfiles.findById(internId).orElseThrow().getInternshipStatus())
                .isEqualTo(InternshipStatus.ACTIVE);
    }

    private SessionInformation sessionInformation(String sessionId) {
        var information = sessionRegistry.getSessionInformation(sessionId);
        assertThat(information).isNotNull();
        return information;
    }

    private void activateSmtp(long adminId) {
        long draftId = smtp.saveDraft(adminId, new SmtpDraft(
                "mailpit", 1025, SecurityMode.NONE, null, null, "admin@example.com", "Lab Timesheet"));
        smtp.testDraft(draftId, adminId, "admin@example.com");
        smtp.activate(draftId, adminId);
    }

    private long createActiveIntern(String email, String displayName, String studentCode) {
        var account = accounts.create(new CreateAccountCommand(
                email, displayName, GlobalRole.INTERN, studentCode,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 12, 31)), adminId);
        accounts.activate(mail.onlyActivationToken(), "replacement secure password");
        return account.userId();
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
            String token = body.substring(tokenStart + "token=".length()).trim();
            messages.clear();
            return token;
        }
    }

    record Message(String recipient, String subject, String body) {
    }
}
