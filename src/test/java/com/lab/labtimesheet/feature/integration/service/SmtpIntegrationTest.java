package com.lab.labtimesheet.feature.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.account.service.BootstrapService;
import com.lab.labtimesheet.feature.integration.model.SecurityMode;
import com.lab.labtimesheet.feature.integration.model.SmtpStatus;
import com.lab.labtimesheet.feature.integration.model.dto.SmtpConnection;
import com.lab.labtimesheet.feature.integration.model.dto.SmtpDraft;
import com.lab.labtimesheet.feature.integration.repository.SmtpConfigurationRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

@Import({TestcontainersConfiguration.class, SmtpIntegrationTest.MailProbeConfiguration.class})
@SpringBootTest
@ActiveProfiles("test")
class SmtpIntegrationTest {

    @Autowired
    private BootstrapService bootstrapService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private SmtpConfigurationService smtpService;

    @Autowired
    private RecordingSmtpProbe smtpProbe;

    @Autowired
    private SmtpConfigurationRepository configurations;

    @Test
    void failedSmtpTestNeverActivatesDraftAndSecretsRemainEncrypted() {
        bootstrapService.bootstrap("admin@example.com", "Admin", "correct horse battery staple");
        long adminId = accountService.requireActiveAdminId("admin@example.com");
        long draftId = smtpService.saveDraft(adminId, new SmtpDraft(
                "mailpit", 1025, SecurityMode.NONE, "smtp-user", "smtp-password", "admin@example.com", "Lab"));

        var savedDraft = configurations.findById(draftId).orElseThrow();
        byte[] ciphertext = savedDraft.getPasswordCiphertext();
        assertThat(new String(ciphertext, StandardCharsets.ISO_8859_1)).doesNotContain("smtp-password");
        assertThat(savedDraft.getPasswordNonce()).hasSize(12);
        assertThat(savedDraft.getSecretKeyVersion()).isEqualTo(1);
        smtpProbe.fail = true;
        assertThatThrownBy(() -> smtpService.testDraft(draftId, adminId, "admin@example.com"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(configurations.findById(draftId).orElseThrow().getStatus()).isEqualTo(SmtpStatus.DRAFT);
        assertThat(configurations.findById(draftId).orElseThrow().getTestedAt()).isNull();
        assertThatThrownBy(() -> smtpService.activate(draftId, adminId)).isInstanceOf(IllegalStateException.class);

        smtpProbe.fail = false;
        smtpService.testDraft(draftId, adminId, "admin@example.com");
        smtpService.activate(draftId, adminId);

        assertThat(configurations.findById(draftId).orElseThrow().getStatus()).isEqualTo(SmtpStatus.ACTIVE);
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
        private boolean fail;

        @Override
        public void send(SmtpConnection connection, String recipient, String subject,
                String body) {
            if (fail) {
                throw new IllegalStateException("simulated SMTP failure");
            }
        }
    }
}
