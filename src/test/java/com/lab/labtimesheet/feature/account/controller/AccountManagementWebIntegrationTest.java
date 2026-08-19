package com.lab.labtimesheet.feature.account.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.ArrayList;
import java.util.List;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.account.model.AccountStatus;
import com.lab.labtimesheet.feature.account.model.GlobalRole;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@Import({TestcontainersConfiguration.class, AccountManagementWebIntegrationTest.MailProbeConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AccountManagementWebIntegrationTest {
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

    private long mentorId;
    private long internId;

    @BeforeEach
    void initializeAdminAccountsAndSmtp() throws Exception {
        bootstrap.bootstrap("admin@example.com", "Admin", "correct horse battery staple");
        long adminId = accounts.requireActiveAdminId("admin@example.com");
        long draftId = smtp.saveDraft(adminId, new SmtpDraft(
                "mailpit", 1025, SecurityMode.NONE, null, null, "admin@example.com", "Lab Timesheet"));
        smtp.testDraft(draftId, adminId, "admin@example.com");
        smtp.activate(draftId, adminId);
        mail.messages.clear();

        mockMvc.perform(post("/admin/accounts")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .param("email", " MENTOR@EXAMPLE.COM ")
                        .param("displayName", "Mentor One")
                        .param("role", "MENTOR"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/admin/accounts")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .param("email", "intern@example.com")
                        .param("displayName", "Intern One")
                        .param("role", "INTERN")
                        .param("studentCode", "STU-001")
                        .param("internshipStart", "2026-08-01")
                        .param("internshipEnd", "2026-12-31"))
                .andExpect(status().is3xxRedirection());

        mentorId = accounts.requireIdentityByEmail("mentor@example.com").id();
        internId = accounts.requireIdentityByEmail("intern@example.com").id();

        activate("mentor@example.com", "new secure mentor password");
        activate("intern@example.com", "new secure intern password");
    }

    @Test
    void adminSeesAccountListWithStateColumnsAndNonAdminsAreDenied() throws Exception {
        mockMvc.perform(get("/admin/accounts").with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("accounts/list"))
                .andExpect(content().string(Matchers.containsString("Accounts and internships")))
                .andExpect(content().string(Matchers.containsString("Mentor One")))
                .andExpect(content().string(Matchers.containsString("Intern One")))
                .andExpect(content().string(Matchers.containsString("Last sign-in")));

        mockMvc.perform(get("/admin/accounts").with(user("mentor@example.com").roles("MENTOR")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/accounts").with(user("intern@example.com").roles("INTERN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminFiltersAccountListByRoleAndGuessedIdsReturnNotFoundWithoutDisclosure() throws Exception {
        mockMvc.perform(get("/admin/accounts")
                        .param("role", "INTERN")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("accounts/list"))
                .andExpect(content().string(Matchers.containsString("Intern One")))
                .andExpect(content().string(Matchers.not(Matchers.containsString("Mentor One"))));

        mockMvc.perform(get("/admin/accounts/99999999").with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/admin/accounts/" + mentorId).with(user("mentor@example.com").roles("MENTOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminSeesAccountDetailAndOnlyAdminMayRequestIt() throws Exception {
        mockMvc.perform(get("/admin/accounts/" + mentorId).with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("accounts/detail"))
                .andExpect(content().string(Matchers.containsString("Mentor One")))
                .andExpect(content().string(Matchers.containsString("MENTOR")))
                .andExpect(content().string(Matchers.containsString("Lock account")));

        mockMvc.perform(get("/admin/accounts/" + internId).with(user("intern@example.com").roles("INTERN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminLocksUnlocksAndDeactivatesAndOnlyAdminMayDoSo() throws Exception {
        mockMvc.perform(post("/admin/accounts/" + mentorId + "/lock")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/accounts/" + mentorId + "?locked"));
        assertThat(accounts.requireIdentityById(mentorId).status()).isEqualTo(AccountStatus.LOCKED);

        mockMvc.perform(post("/admin/accounts/" + mentorId + "/unlock")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/accounts/" + mentorId + "?unlocked"));
        assertThat(accounts.requireIdentityById(mentorId).status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(accounts.requireIdentityById(mentorId).role()).isEqualTo(GlobalRole.MENTOR);

        mockMvc.perform(post("/admin/accounts/" + mentorId + "/deactivate")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/accounts/" + mentorId + "?deactivated"));
        assertThat(accounts.requireIdentityById(mentorId).status()).isEqualTo(AccountStatus.DEACTIVATED);
        assertThat(accounts.requireIdentityById(mentorId).role()).isEqualTo(GlobalRole.MENTOR);

        mockMvc.perform(post("/admin/accounts/" + internId + "/lock")
                        .with(user("intern@example.com").roles("INTERN"))
                        .with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void invalidLockTransitionRedirectsToDetailWithErrorInsteadOfCrashing() throws Exception {
        mockMvc.perform(post("/admin/accounts/" + mentorId + "/lock")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());
        mockMvc.perform(post("/admin/accounts/" + mentorId + "/lock")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/accounts/" + mentorId + "?error"));
    }

    private void activate(String email, String password) throws Exception {
        String rawToken = mail.activationTokenFor(email);
        mockMvc.perform(post("/activate")
                        .with(csrf())
                        .param("token", rawToken)
                        .param("password", password)
                        .param("confirmPassword", password))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?activated"));
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
    }

    record Message(String recipient, String body) {
    }
}
