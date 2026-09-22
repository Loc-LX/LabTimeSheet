package com.lab.labtimesheet.feature.identity.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.util.ArrayList;
import java.util.List;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.identity.model.AccountStatus;
import com.lab.labtimesheet.feature.identity.service.AccountService;
import com.lab.labtimesheet.feature.identity.service.BootstrapService;
import com.lab.labtimesheet.platform.model.GlobalRole;
import com.lab.labtimesheet.platform.model.SecurityMode;
import com.lab.labtimesheet.platform.model.dto.SmtpConnection;
import com.lab.labtimesheet.platform.model.dto.SmtpDraft;
import com.lab.labtimesheet.platform.service.SmtpConfigurationService;
import com.lab.labtimesheet.platform.service.SmtpProbe;
import jakarta.servlet.http.HttpSession;
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

@Import({TestcontainersConfiguration.class, AccountWebIntegrationTest.MailProbeConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AccountWebIntegrationTest {
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

    @BeforeEach
    void initializeAdminAndSmtp() {
        bootstrap.bootstrap("admin@example.com", "Admin", "correct horse battery staple");
        long adminId = accounts.requireActiveAdminId("admin@example.com");
        long draftId = smtp.saveDraft(adminId, new SmtpDraft(
                "mailpit", 1025, SecurityMode.NONE, null, null, "admin@example.com", "Lab Timesheet"));
        smtp.testDraft(draftId, adminId, "admin@example.com");
        smtp.activate(draftId, adminId);
        mail.messages.clear();
    }

    @Test
    void adminCreatesMentorAndInternThenMentorActivatesAuthenticatesAndLogsOut() throws Exception {
        mockMvc.perform(get("/admin/accounts/new").with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("accounts/new"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Internship start")));

        mockMvc.perform(post("/admin/accounts")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .param("email", " MENTOR@EXAMPLE.COM ")
                        .param("displayName", "Mentor One")
                        .param("role", "MENTOR")
                        .param("studentCode", "")
                        .param("internshipStart", "")
                        .param("internshipEnd", ""))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/accounts/new?created"));

        mockMvc.perform(post("/admin/accounts")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .param("email", "intern@example.com")
                        .param("displayName", "Intern One")
                        .param("role", "INTERN")
                        .param("studentCode", "STU-001")
                        .param("internshipStart", "2026-08-01")
                        .param("internshipEnd", "2026-12-31"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/accounts/new?created"));

        var pendingMentor = accounts.requireIdentityByEmail("mentor@example.com");
        assertThat(pendingMentor.role()).isEqualTo(GlobalRole.MENTOR);
        assertThat(pendingMentor.status()).isEqualTo(AccountStatus.PENDING_ACTIVATION);
        assertThat(accounts.requireIdentityByEmail("intern@example.com").role()).isEqualTo(GlobalRole.INTERN);

        String rawToken = mail.activationTokenFor("mentor@example.com");
        mockMvc.perform(get("/activate").param("token", rawToken))
                .andExpect(status().isOk())
                .andExpect(view().name("accounts/activate"));
        mockMvc.perform(post("/activate")
                        .with(csrf())
                        .param("token", rawToken)
                        .param("password", "new secure mentor password")
                        .param("confirmPassword", "new secure mentor password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?activated"));

        var login = mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", " MENTOR@EXAMPLE.COM ")
                        .param("password", "new secure mentor password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(authenticated().withUsername("mentor@example.com"))
                .andReturn();
        HttpSession session = login.getRequest().getSession(false);
        assertThat(session).isNotNull();

        mockMvc.perform(get("/admin/accounts/new").session((org.springframework.mock.web.MockHttpSession) session))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/admin/accounts").session((org.springframework.mock.web.MockHttpSession) session))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/logout")
                        .session((org.springframework.mock.web.MockHttpSession) session)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"))
                .andExpect(unauthenticated());
    }

    @Test
    void invalidAndDuplicateAccountFormsReturnActionableErrorsWithoutCreatingAnotherAccount() throws Exception {
        mockMvc.perform(post("/admin/accounts")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .param("email", "not-an-email")
                        .param("displayName", "Safe display name")
                        .param("role", "INTERN")
                        .param("studentCode", "")
                        .param("internshipStart", "")
                        .param("internshipEnd", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("accounts/new"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("valid email address")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Intern details are required")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Safe display name")));

        mockMvc.perform(post("/admin/accounts")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .param("email", "mentor@example.com")
                        .param("displayName", "Mentor One")
                        .param("role", "MENTOR"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/admin/accounts")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .param("email", " MENTOR@EXAMPLE.COM ")
                        .param("displayName", "Duplicate Mentor")
                        .param("role", "MENTOR"))
                .andExpect(status().isOk())
                .andExpect(view().name("accounts/new"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("already exists")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Duplicate Mentor")));
    }

    @Test
    void duplicateNormalizedStudentCodeIsReportedOnStudentCodeRatherThanEmail() throws Exception {
        mockMvc.perform(post("/admin/accounts")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .param("email", "first-intern@example.com")
                        .param("displayName", "First Intern")
                        .param("role", "INTERN")
                        .param("studentCode", "STU-ROUND-2")
                        .param("internshipStart", "2026-08-01")
                        .param("internshipEnd", "2026-12-31"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/admin/accounts")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .param("email", "second-intern@example.com")
                        .param("displayName", "Second Intern")
                        .param("role", "INTERN")
                        .param("studentCode", "  stu-round-2  ")
                        .param("internshipStart", "2026-08-01")
                        .param("internshipEnd", "2026-12-31"))
                .andExpect(status().isOk())
                .andExpect(view().name("accounts/new"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "An Intern with this student code already exists")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("this email already exists"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Second Intern")));
    }

    @Test
    void additionalAdminActivatesAndAuthenticatesWithoutChangingTheFirstAdmin() throws Exception {
        mockMvc.perform(post("/admin/accounts")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .param("email", "second-admin@example.com")
                        .param("displayName", "Second Admin")
                        .param("role", "ADMIN"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/accounts/new?created"));

        String rawToken = mail.activationTokenFor("second-admin@example.com");
        mockMvc.perform(post("/activate")
                        .with(csrf())
                        .param("token", rawToken)
                        .param("password", "new secure admin password")
                        .param("confirmPassword", "new secure admin password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?activated"));

        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "second-admin@example.com")
                        .param("password", "new secure admin password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(authenticated().withRoles("ADMIN"));
        assertThat(accounts.requireIdentityByEmail("admin@example.com").status()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(accounts.requireIdentityByEmail("admin@example.com").role()).isEqualTo(GlobalRole.ADMIN);
    }

    @Test
    void adminListsAndOpensInternLifecycleAdministrationWithoutDisclosingGuessedIds() throws Exception {
        long adminId = accounts.requireActiveAdminId("admin@example.com");
        var creation = accounts.create(new com.lab.labtimesheet.feature.identity.model.dto.CreateAccountCommand(
                "managed-intern@example.com",
                "Managed Intern",
                GlobalRole.INTERN,
                "STU-MANAGED",
                java.time.LocalDate.of(2026, 8, 1),
                java.time.LocalDate.of(2026, 12, 31)), adminId);
        assertThat(accounts.activate(
                mail.activationTokenFor("managed-intern@example.com"),
                "managed intern password")).isTrue();
        accounts.activateInternship(creation.userId(), adminId);

        mockMvc.perform(get("/admin/accounts").with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("managed-intern@example.com")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("STU-MANAGED")));
        mockMvc.perform(get("/admin/accounts/{id}", creation.userId())
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Ready for terminal action")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Complete internship")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Withdraw internship")));
        mockMvc.perform(get("/admin/accounts/{id}", Long.MAX_VALUE)
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Account not found"))));
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
