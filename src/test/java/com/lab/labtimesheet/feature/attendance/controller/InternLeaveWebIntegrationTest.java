package com.lab.labtimesheet.feature.attendance.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.account.model.GlobalRole;
import com.lab.labtimesheet.feature.account.model.dto.CreateAccountCommand;
import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.integration.model.SecurityMode;
import com.lab.labtimesheet.feature.integration.model.dto.SmtpConnection;
import com.lab.labtimesheet.feature.integration.model.dto.SmtpDraft;
import com.lab.labtimesheet.feature.integration.service.SmtpConfigurationService;
import com.lab.labtimesheet.feature.integration.service.SmtpProbe;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@Import({TestcontainersConfiguration.class, InternLeaveWebIntegrationTest.MailProbeConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class InternLeaveWebIntegrationTest {
    private static final String PASSWORD = "correct horse battery staple";
    private static final String ADMIN_EMAIL = "leave-admin@example.test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountService accounts;

    @Autowired
    private SmtpConfigurationService smtp;

    @Autowired
    private RecordingSmtpProbe mail;

    @Test
    void internCanOpenFormAndSubmittedLeaveMaterializesAndShowsInPriorRequests() throws Exception {
        MockHttpSession adminSession = seedAdmin();
        long adminId = accounts.requireActiveAdminId(ADMIN_EMAIL);
        configureSmtp(adminId);
        createAndActivateIntern(adminId);

        MockHttpSession internSession = login("leave-intern@example.test", PASSWORD, "INTERN");
        mockMvc.perform(get("/intern/leave").session(internSession))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("New request")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Monthly quota")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Prior requests")));

        mockMvc.perform(post("/intern/leave").session(internSession)
                        .with(csrf())
                        .param("startDate", "2026-09-01")
                        .param("endDate", "2026-09-03")
                        .param("reason", "Family trip"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/intern/leave"));

        mockMvc.perform(get("/intern/leave").session(internSession))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Family trip")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("PENDING")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("01/09/2026 → 03/09/2026")));
    }

    @Test
    void mentorAndAdminCannotRequestLeave() throws Exception {
        MockHttpSession adminSession = seedAdmin();
        long adminId = accounts.requireActiveAdminId(ADMIN_EMAIL);

        mockMvc.perform(get("/intern/leave").session(adminSession)).andExpect(status().isForbidden());
        mockMvc.perform(post("/intern/leave").session(adminSession)
                        .with(csrf())
                        .param("startDate", "2026-09-01")
                        .param("endDate", "2026-09-03")
                        .param("reason", "Admin leave"))
                .andExpect(status().isForbidden());

        configureSmtp(adminId);
        createAndActivate(adminId, new CreateAccountCommand(
                "leave-mentor@example.test", "Leave Mentor", GlobalRole.MENTOR, null, null, null));
        MockHttpSession mentorSession = login("leave-mentor@example.test", PASSWORD, "MENTOR");
        mockMvc.perform(get("/intern/leave").session(mentorSession)).andExpect(status().isForbidden());
    }

    private MockHttpSession seedAdmin() throws Exception {
        mockMvc.perform(post("/bootstrap")
                        .with(csrf())
                        .param("email", ADMIN_EMAIL)
                        .param("displayName", "Admin")
                        .param("password", PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/smtp?onboarding"));
        return login(ADMIN_EMAIL, PASSWORD, "ADMIN");
    }

    private void createAndActivateIntern(long adminId) {
        var creation = accounts.create(new CreateAccountCommand(
                "leave-intern@example.test",
                "Leave Intern",
                GlobalRole.INTERN,
                "INT-LEAVE",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 12, 31)), adminId);
        assertThat(creation.deliverySucceeded()).isTrue();
        assertThat(accounts.activate(mail.activationTokenFor("leave-intern@example.test"), PASSWORD)).isTrue();
        accounts.activateInternship(creation.userId(), adminId);
    }

    private MockHttpSession login(String email, String password, String role) throws Exception {
        var result = mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", email)
                        .param("password", password))
                .andExpect(status().is3xxRedirection())
                .andExpect(authenticated().withUsername(email))
                .andExpect(authenticated().withRoles(role))
                .andReturn();
        return (MockHttpSession) result.getRequest().getSession(false);
    }

    private void configureSmtp(long adminId) {
        long draftId = smtp.saveDraft(adminId, new SmtpDraft(
                "mailpit", 1025, SecurityMode.NONE, null, null, ADMIN_EMAIL, "Lab Timesheet"));
        smtp.testDraft(draftId, adminId, ADMIN_EMAIL);
        smtp.activate(draftId, adminId);
        mail.clear();
    }

    private void createAndActivate(long adminId, CreateAccountCommand command) {
        var creation = accounts.create(command, adminId);
        assertThat(creation.deliverySucceeded()).isTrue();
        assertThat(accounts.activate(mail.activationTokenFor(command.email()), PASSWORD)).isTrue();
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

        void clear() {
            messages.clear();
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
    }

    record Message(String recipient, String body) {
    }
}