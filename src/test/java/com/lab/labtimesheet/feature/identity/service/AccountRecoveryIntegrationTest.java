package com.lab.labtimesheet.feature.identity.service;

import com.lab.labtimesheet.feature.internship.service.InternshipService;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.identity.model.dto.CreateAccountCommand;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@Import({TestcontainersConfiguration.class, AccountRecoveryIntegrationTest.MailProbeConfiguration.class})
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AccountRecoveryIntegrationTest {
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
    private UserActionTokenRepository tokens;

    @Test
    void resendInvalidatesPriorActivationTokenBeforeSendingFreshLink() {
        bootstrap.bootstrap("admin@example.com", "Admin", "correct horse battery staple");
        long adminId = accounts.requireActiveAdminId("admin@example.com");
        long draftId = smtp.saveDraft(adminId,
                new SmtpDraft("mailpit", 1025, SecurityMode.NONE, null, null, "admin@example.com", "Lab Timesheet"));
        smtp.testDraft(draftId, adminId, "admin@example.com");
        smtp.activate(draftId, adminId);

        var creation = internships.create(new CreateAccountCommand(
                "pending@example.com", "Pending", GlobalRole.MENTOR, null, null, null), adminId);
        String first = mail.activationToken();
        accounts.resendActivation(creation.userId(), adminId);
        String second = mail.activationToken();

        assertThat(second).isNotEqualTo(first);
        assertThat(accounts.activate(first, "a secure password one")).isFalse();
        assertThat(accounts.activate(second, "a secure password two")).isTrue();
        assertThat(tokens.findAll()).hasSize(2);
    }

    @Test
    void resetConsumptionAndReplacementIssuanceUseOneUserThenTokenLockOrder() throws Exception {
        bootstrap.bootstrap("admin@example.com", "Admin", "correct horse battery staple");
        long adminId = accounts.requireActiveAdminId("admin@example.com");
        long draftId = smtp.saveDraft(adminId,
                new SmtpDraft("mailpit", 1025, SecurityMode.NONE, null, null, "admin@example.com", "Lab Timesheet"));
        smtp.testDraft(draftId, adminId, "admin@example.com");
        smtp.activate(draftId, adminId);

        var creation = internships.create(new CreateAccountCommand(
                "reset-race@example.com", "Reset Race", GlobalRole.MENTOR, null, null, null), adminId);
        assertThat(accounts.activate(mail.activationToken(), "a secure password one")).isTrue();
        assertThat(accounts.requestPasswordReset("reset-race@example.com")).isTrue();
        String originalResetToken = mail.activationToken();

        for (int round = 0; round < 8; round++) {
            String roundToken = originalResetToken;
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
                var consume = executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return accounts.resetPassword(roundToken, "a secure password two");
                });
                var replace = executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return accounts.requestPasswordReset("reset-race@example.com");
                });
                assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
                start.countDown();

                assertThat(consume.get(10, TimeUnit.SECONDS)).isIn(true, false);
                assertThat(replace.get(10, TimeUnit.SECONDS)).isTrue();
            }
            mail.clear();
            assertThat(accounts.requestPasswordReset("reset-race@example.com")).isTrue();
            originalResetToken = mail.activationToken();
        }
        assertThat(creation.userId()).isPositive();
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
        private final List<String> activationTokens = new ArrayList<>();

        @Override
        public synchronized void send(SmtpConnection connection, String recipient, String subject, String body) {
            int marker = body.indexOf("token=");
            if (marker >= 0) {
                activationTokens.add(body.substring(marker + "token=".length()).trim());
            }
        }

        synchronized String activationToken() {
            return activationTokens.getLast();
        }

        synchronized void clear() {
            activationTokens.clear();
        }
    }
}
