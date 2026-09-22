package com.lab.labtimesheet.feature.account.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.account.model.AccountStatus;
import com.lab.labtimesheet.feature.account.model.dto.CreateAccountCommand;
import com.lab.labtimesheet.feature.account.repository.AppUserRepository;
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
    private AppUserRepository users;

    @Autowired
    private RecordingSmtpProbe mail;

    @Test
    void lockUnlockAndDeactivatePreserveRoleAndAttribution() {
        bootstrap.bootstrap("admin@example.com", "Admin", "correct horse battery staple");
        long adminId = accounts.requireActiveAdminId("admin@example.com");
        long draftId = smtp.saveDraft(adminId,
                new SmtpDraft("mailpit", 1025, SecurityMode.NONE, null, null, "admin@example.com", "Lab Timesheet"));
        smtp.testDraft(draftId, adminId, "admin@example.com");
        smtp.activate(draftId, adminId);
        var creation = accounts.create(new CreateAccountCommand(
                "mentor@example.com", "Mentor", GlobalRole.MENTOR, null, null, null), adminId);
        assertThat(accounts.activate(mail.activationToken(), "a secure mentor password")).isTrue();

        accounts.lockAccount(creation.userId(), adminId);
        var locked = users.findById(creation.userId()).orElseThrow();
        assertThat(locked.getAccountStatus()).isEqualTo(AccountStatus.LOCKED);
        assertThat(locked.getGlobalRole()).isEqualTo(GlobalRole.MENTOR);

        accounts.unlockAccount(creation.userId(), adminId);
        assertThat(users.findById(creation.userId()).orElseThrow().getAccountStatus())
                .isEqualTo(AccountStatus.ACTIVE);

        accounts.deactivateAccount(creation.userId(), adminId);
        var deactivated = users.findById(creation.userId()).orElseThrow();
        assertThat(deactivated.getAccountStatus()).isEqualTo(AccountStatus.DEACTIVATED);
        assertThat(deactivated.getGlobalRole()).isEqualTo(GlobalRole.MENTOR);
        assertThat(deactivated.getDeactivatedAt()).isNotNull();
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
        private String lastToken;

        @Override
        public void send(SmtpConnection connection, String recipient, String subject, String body) {
            int marker = body.indexOf("token=");
            if (marker >= 0) {
                lastToken = body.substring(marker + "token=".length()).trim();
            }
        }

        String activationToken() {
            return lastToken;
        }
    }
}
