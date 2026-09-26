package com.lab.labtimesheet.feature.internship.service;

import com.lab.labtimesheet.feature.identity.service.AccountService;
import com.lab.labtimesheet.feature.identity.service.BootstrapService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.identity.model.AccountStatus;
import com.lab.labtimesheet.feature.internship.model.InternshipStatus;
import com.lab.labtimesheet.feature.identity.model.dto.AccountCreation;
import com.lab.labtimesheet.feature.identity.model.dto.CreateAccountCommand;
import com.lab.labtimesheet.feature.identity.model.dto.AccountIdentityCorrection;
import com.lab.labtimesheet.feature.identity.repository.AppUserRepository;
import com.lab.labtimesheet.feature.internship.repository.InternProfileRepository;
import com.lab.labtimesheet.platform.model.GlobalRole;
import com.lab.labtimesheet.platform.model.SecurityMode;
import com.lab.labtimesheet.platform.model.dto.SmtpConnection;
import com.lab.labtimesheet.platform.model.dto.SmtpDraft;
import com.lab.labtimesheet.platform.service.SmtpConfigurationService;
import com.lab.labtimesheet.platform.service.SmtpProbe;
import com.lab.labtimesheet.platform.authorization.AuthorizationCapability;
import com.lab.labtimesheet.platform.authorization.AuthorizationColumn;
import com.lab.labtimesheet.platform.authorization.AuthorizationPolicy;
import com.lab.labtimesheet.platform.authorization.AuthorizationRequest;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

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
    private InternshipService internships;

    @Autowired
    private SmtpConfigurationService smtp;

    @Autowired
    private RecordingSmtpProbe mail;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private InternProfileRepository profiles;

    @MockitoSpyBean
    private AuthorizationPolicy authorizationPolicy;

    /** Protects AUTH-012 and B.8: Admin-owned Internship operations use the §5.2 account-lifecycle row. */
    @Test
    void activeAdminLifecycleReadinessUsesAccountLifecyclePolicy() {
        bootstrap.bootstrap("policy-admin@example.com", "Admin", "correct horse battery staple");
        long adminId = accounts.requireActiveAdminId("policy-admin@example.com");
        activateSmtp(adminId);
        var intern = createAndActivate("policy-intern@example.com", "STU-POLICY", adminId);
        org.mockito.Mockito.clearInvocations(authorizationPolicy);

        internships.correctAccount(intern.userId(), adminId,
                new AccountIdentityCorrection(null, "STU-POLICY-UPDATED", null, null));
        internships.administrationViews(adminId);
        internships.administrationView(intern.userId(), adminId);
        internships.internshipLifecycleReadiness(intern.userId(), adminId);
        org.mockito.Mockito.verify(authorizationPolicy, org.mockito.Mockito.times(4)).allows(
                AuthorizationCapability.ACCOUNT_LIFECYCLE,
                new AuthorizationRequest(Set.of(AuthorizationColumn.ADMIN), null, null, null));
    }

    /** Protects AUTH-012 and B.8: an Intern actor supplies no §5.2 column and keeps the existing refusal. */
    @Test
    void internCannotReadLifecycleReadinessThroughAccountLifecyclePolicy() {
        bootstrap.bootstrap("policy-admin@example.com", "Admin", "correct horse battery staple");
        long adminId = accounts.requireActiveAdminId("policy-admin@example.com");
        activateSmtp(adminId);
        var intern = createAndActivate("policy-intern@example.com", "STU-POLICY", adminId);
        org.mockito.Mockito.clearInvocations(authorizationPolicy);
        assertThatThrownBy(() -> internships.internshipLifecycleReadiness(intern.userId(), intern.userId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("An active Admin is required");
        org.mockito.Mockito.verify(authorizationPolicy).allows(
                AuthorizationCapability.ACCOUNT_LIFECYCLE,
                new AuthorizationRequest(Set.of(), null, null, null));
    }

    @Test
    void scheduledStartIsIdempotentAndTerminalActionsApplyGuardsAndPreserveCompletedAuthentication() {
        bootstrap.bootstrap("admin@example.com", "Admin", "correct horse battery staple");
        long adminId = accounts.requireActiveAdminId("admin@example.com");
        activateSmtp(adminId);

        var scheduled = createAndActivate("scheduled@example.com", "STU-SCHEDULED", adminId);
        assertThat(internships.activateDueInternships()).isEqualTo(1);
        assertThat(internships.activateDueInternships()).isZero();
        assertThat(profiles.findById(scheduled.userId()).orElseThrow().getInternshipStatus())
                .isEqualTo(InternshipStatus.ACTIVE);

        var completed = createAndActivate("completed@example.com", "STU-COMPLETED", adminId);
        internships.activateInternship(completed.userId(), adminId);
        internships.completeInternship(completed.userId(), adminId);
        assertThat(profiles.findById(completed.userId()).orElseThrow().getInternshipStatus())
                .isEqualTo(InternshipStatus.COMPLETED);
        assertThat(users.findById(completed.userId()).orElseThrow().getAccountStatus())
                .isEqualTo(AccountStatus.ACTIVE);
        assertThatThrownBy(() -> internships.withdrawInternship(completed.userId(), adminId))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("withdraw");

        var withdrawn = createAndActivate("withdrawn@example.com", "STU-WITHDRAWN", adminId);
        internships.activateInternship(withdrawn.userId(), adminId);
        internships.withdrawInternship(withdrawn.userId(), adminId);
        assertThat(profiles.findById(withdrawn.userId()).orElseThrow().getInternshipStatus())
                .isEqualTo(InternshipStatus.WITHDRAWN);
        assertThat(users.findById(withdrawn.userId()).orElseThrow().getAccountStatus())
                .isEqualTo(AccountStatus.DEACTIVATED);
    }

    private AccountCreation createAndActivate(String email, String studentCode, long adminId) {
        var creation = internships.create(new CreateAccountCommand(
                email, email.substring(0, email.indexOf('@')), GlobalRole.INTERN, studentCode,
                LocalDate.of(2026, 8, 14), LocalDate.of(2026, 12, 31)), adminId);
        assertThat(accounts.activate(mail.token(), "a secure intern password")).isTrue();
        return creation;
    }

    private void activateSmtp(long adminId) {
        long draftId = smtp.saveDraft(adminId,
                new SmtpDraft("mailpit", 1025, SecurityMode.NONE, null, null, "admin@example.com", "Lab Timesheet"));
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
        private String token;

        @Override
        public void send(SmtpConnection connection, String recipient, String subject, String body) {
            int marker = body.indexOf("token=");
            if (marker >= 0) {
                token = body.substring(marker + "token=".length()).trim();
            }
        }

        String token() {
            return token;
        }
    }
}
