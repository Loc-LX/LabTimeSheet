package com.lab.labtimesheet.feature.account.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.account.model.GlobalRole;
import com.lab.labtimesheet.feature.account.model.dto.CreateAccountCommand;
import com.lab.labtimesheet.feature.account.repository.AppUserRepository;
import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.account.service.BootstrapService;
import com.lab.labtimesheet.feature.integration.model.SecurityMode;
import com.lab.labtimesheet.feature.integration.model.dto.SmtpConnection;
import com.lab.labtimesheet.feature.integration.model.dto.SmtpDraft;
import com.lab.labtimesheet.feature.integration.service.SmtpConfigurationService;
import com.lab.labtimesheet.feature.integration.service.SmtpProbe;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@Import({TestcontainersConfiguration.class, PasswordRecoveryWebIntegrationTest.MailProbeConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class PasswordRecoveryWebIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BootstrapService bootstrap;

    @Autowired
    private AccountService accounts;

    @Autowired
    private SmtpConfigurationService smtp;

    @Autowired
    private RecordingSmtpProbe mail;

    @Autowired
    private PasswordEncoder passwords;

    @Autowired
    private AppUserRepository users;

    private long mentorId;

    @BeforeEach
    void initializeAdminAndActiveMentor() throws Exception {
        bootstrap.bootstrap("admin@example.com", "Admin", "correct horse battery staple");
        long adminId = accounts.requireActiveAdminId("admin@example.com");
        long draftId = smtp.saveDraft(adminId, new SmtpDraft(
                "mailpit", 1025, SecurityMode.NONE, null, null, "admin@example.com", "Lab Timesheet"));
        smtp.testDraft(draftId, adminId, "admin@example.com");
        smtp.activate(draftId, adminId);
        mail.messages.clear();

        var creation = accounts.create(new CreateAccountCommand(
                "mentor@example.com", "Mentor One", GlobalRole.MENTOR, null, null, null), adminId);
        mentorId = creation.userId();
        String rawToken = mail.activationTokenFor("mentor@example.com");
        mockMvc.perform(post("/activate")
                        .with(csrf())
                        .param("token", rawToken)
                        .param("password", "new secure mentor password")
                        .param("confirmPassword", "new secure mentor password"))
                .andExpect(status().is3xxRedirection());
        mail.messages.clear();
    }

    @Test
    void forgotPasswordPageIsPublicAndPostingShowsGenericConfirmation() throws Exception {
        mockMvc.perform(get("/forgot-password"))
                .andExpect(status().isOk())
                .andExpect(view().name("accounts/forgot-password"))
                .andExpect(content().string(Matchers.containsString("Reset your password")));

        mockMvc.perform(post("/forgot-password")
                        .with(csrf())
                        .param("email", "mentor@example.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/forgot-password?sent"));
        assertThat(mail.messages).hasSize(1);

        mockMvc.perform(post("/forgot-password")
                        .with(csrf())
                        .param("email", "unknown@example.com"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/forgot-password?sent"));
    }

    @Test
    void resetPasswordPageRendersForValidTokenAndConsumesItOnce() throws Exception {
        mockMvc.perform(post("/forgot-password")
                        .with(csrf())
                        .param("email", "mentor@example.com"))
                .andExpect(status().is3xxRedirection());
        String rawToken = mail.resetTokenFor("mentor@example.com");

        mockMvc.perform(get("/reset-password").param("token", rawToken))
                .andExpect(status().isOk())
                .andExpect(view().name("accounts/reset-password"))
                .andExpect(content().string(Matchers.containsString("Choose a new password")));

        mockMvc.perform(post("/reset-password")
                        .with(csrf())
                        .param("token", rawToken)
                        .param("password", "fresh secure mentor password")
                        .param("confirmPassword", "fresh secure mentor password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?reset"));

        assertThat(passwords.matches("fresh secure mentor password",
                users.findById(mentorId).orElseThrow().getPasswordHash())).isTrue();
    }

    @Test
    void resetPasswordRejectsInvalidTokenAndMismatchedPasswords() throws Exception {
        mockMvc.perform(post("/forgot-password")
                        .with(csrf())
                        .param("email", "mentor@example.com"))
                .andExpect(status().is3xxRedirection());
        String rawToken = mail.resetTokenFor("mentor@example.com");

        mockMvc.perform(get("/reset-password").param("token", "not-the-token"))
                .andExpect(status().isOk())
                .andExpect(view().name("accounts/reset-password"))
                .andExpect(content().string(Matchers.containsString("invalid or no longer usable")));

        mockMvc.perform(post("/reset-password")
                        .with(csrf())
                        .param("token", rawToken)
                        .param("password", "fresh secure mentor password")
                        .param("confirmPassword", "different secure password"))
                .andExpect(status().isOk())
                .andExpect(view().name("accounts/reset-password"))
                .andExpect(content().string(Matchers.containsString("Passwords do not match")));
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
            messages.add(new Message(recipient, body));
        }

        String activationTokenFor(String recipient) {
            String body = messages.stream()
                    .filter(message -> message.recipient().equals(recipient))
                    .findFirst()
                    .orElseThrow()
                    .body();
            int tokenStart = body.indexOf("token=");
            assertThat(tokenStart).isGreaterThanOrEqualTo(0);
            return body.substring(tokenStart + "token=".length()).trim();
        }

        String resetTokenFor(String recipient) {
            String body = messages.stream()
                    .filter(message -> message.recipient().equals(recipient))
                    .findFirst()
                    .orElseThrow()
                    .body();
            int tokenStart = body.indexOf("reset-password?token=");
            assertThat(tokenStart).isGreaterThanOrEqualTo(0);
            return body.substring(tokenStart + "reset-password?token=".length()).trim();
        }
    }

    record Message(String recipient, String body) {
    }
}