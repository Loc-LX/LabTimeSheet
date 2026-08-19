package com.lab.labtimesheet.feature.account.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.account.model.GlobalRole;
import com.lab.labtimesheet.feature.account.model.InternshipStatus;
import com.lab.labtimesheet.feature.account.model.dto.CreateAccountCommand;
import com.lab.labtimesheet.feature.account.repository.InternProfileRepository;
import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.account.service.BootstrapService;
import com.lab.labtimesheet.feature.integration.model.SecurityMode;
import com.lab.labtimesheet.feature.integration.model.dto.SmtpConnection;
import com.lab.labtimesheet.feature.integration.model.dto.SmtpDraft;
import com.lab.labtimesheet.feature.integration.service.SmtpConfigurationService;
import com.lab.labtimesheet.feature.integration.service.SmtpProbe;
import java.util.ArrayList;
import java.util.List;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@Import({TestcontainersConfiguration.class, InternshipLifecycleWebIntegrationTest.MailProbeConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class InternshipLifecycleWebIntegrationTest {

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
    private InternProfileRepository internProfiles;

    private long adminId;
    private long internId;

    @BeforeEach
    void initializeAdminAndIntern() {
        bootstrap.bootstrap("admin@example.com", "Admin", "correct horse battery staple");
        adminId = accounts.requireActiveAdminId("admin@example.com");
        long draftId = smtp.saveDraft(adminId, new SmtpDraft(
                "mailpit", 1025, SecurityMode.NONE, null, null, "admin@example.com", "Lab Timesheet"));
        smtp.testDraft(draftId, adminId, "admin@example.com");
        smtp.activate(draftId, adminId);
        mail.messages.clear();

        var intern = accounts.create(new CreateAccountCommand(
                "intern@example.com", "Intern One", GlobalRole.INTERN, "STU-001",
                java.time.LocalDate.of(2026, 8, 1), java.time.LocalDate.of(2026, 12, 31)), adminId);
        accounts.activate(mail.onlyActivationToken(), "new secure intern password");
        internId = intern.userId();
    }

    @Test
    void adminCompletionRouteRequiresCsrfShowsConsequencesAndCompletes() throws Exception {
        assertThat(accounts.isEligibleIntern(internId)).isTrue();

        mockMvc.perform(get("/admin/accounts/" + internId)
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Complete this internship?")))
                .andExpect(content().string(Matchers.containsString("read-only access to history")));

        mockMvc.perform(post("/admin/accounts/" + internId + "/complete-internship")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/admin/accounts/" + internId + "/complete-internship")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/accounts/" + internId + "?completed"));

        assertThat(internProfiles.findById(internId).orElseThrow().getInternshipStatus())
                .isEqualTo(InternshipStatus.COMPLETED);
    }

    @Test
    void withdrawalRouteRequiresAdminAndExplainsAuthenticationConsequence() throws Exception {
        mockMvc.perform(post("/admin/accounts/" + internId + "/withdraw-internship")
                        .with(user("intern@example.com").roles("INTERN"))
                        .with(csrf()))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/admin/accounts/" + internId)
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(Matchers.containsString("Normal sign-in will stop")));

        mockMvc.perform(post("/admin/accounts/" + internId + "/withdraw-internship")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/accounts/" + internId + "?withdrawn"));

        assertThat(internProfiles.findById(internId).orElseThrow().getInternshipStatus())
                .isEqualTo(InternshipStatus.WITHDRAWN);
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

    record Message(String recipient, String body) {
    }
}
