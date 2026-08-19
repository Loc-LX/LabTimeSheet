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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.account.model.AccountStatus;
import com.lab.labtimesheet.feature.account.model.GlobalRole;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@Import({TestcontainersConfiguration.class, AccountAdminEditWebIntegrationTest.MailProbeConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AccountAdminEditWebIntegrationTest {
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
    private AppUserRepository users;

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
                        .param("email", "mentor@example.com")
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
    void adminSeesEditFormPrefilledAndOnlyAdminMayRequestIt() throws Exception {
        mockMvc.perform(get("/admin/accounts/" + mentorId + "/edit").with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("accounts/edit"))
                .andExpect(content().string(Matchers.containsString("Edit account")))
                .andExpect(content().string(Matchers.containsString("Mentor One")))
                .andExpect(content().string(Matchers.containsString("mentor@example.com")));

        mockMvc.perform(get("/admin/accounts/" + internId + "/edit")
                        .with(user("intern@example.com").roles("INTERN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEditsMentorThroughFormAndRedirectsToDetail() throws Exception {
        var detail = accounts.requireAccountDetail(mentorId);

        mockMvc.perform(post("/admin/accounts/" + mentorId + "/edit")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .param("userVersion", String.valueOf(detail.userVersion()))
                        .param("profileVersion", detail.profileVersion() == null ? "" : String.valueOf(detail.profileVersion()))
                        .param("email", "mentor-new@example.com")
                        .param("displayName", "Mentor Renamed"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/accounts/" + mentorId + "?updated"));

        var mentor = users.findById(mentorId).orElseThrow();
        assertThat(mentor.getEmail()).isEqualTo("mentor-new@example.com");
        assertThat(mentor.getDisplayName()).isEqualTo("Mentor Renamed");
        assertThat(mentor.getGlobalRole()).isEqualTo(GlobalRole.MENTOR);
        assertThat(mentor.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);

        mockMvc.perform(get("/admin/accounts/" + mentorId).with(user("admin@example.com").roles("ADMIN")))
                .andExpect(content().string(Matchers.containsString("Mentor Renamed")))
                .andExpect(content().string(Matchers.containsString("mentor-new@example.com")));
    }

    @Test
    void editFormShowsFieldErrorForDuplicateEmailAndConflictForStaleVersion() throws Exception {
        var mentorDetail = accounts.requireAccountDetail(mentorId);

        mockMvc.perform(post("/admin/accounts/" + mentorId + "/edit")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .param("userVersion", String.valueOf(mentorDetail.userVersion()))
                        .param("profileVersion", "")
                        .param("email", "intern@example.com")
                        .param("displayName", "Mentor Renamed"))
                .andExpect(status().isOk())
                .andExpect(view().name("accounts/edit"))
                .andExpect(content().string(Matchers.containsString("already exists")));

        mockMvc.perform(post("/admin/accounts/" + mentorId + "/edit")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .param("userVersion", String.valueOf(mentorDetail.userVersion() - 1))
                        .param("profileVersion", "")
                        .param("email", "mentor@example.com")
                        .param("displayName", "Mentor One"))
                .andExpect(status().isOk())
                .andExpect(view().name("accounts/edit"))
                .andExpect(content().string(Matchers.containsString("changed by another request")));
    }

    @Test
    void editFormValidatesInternFieldsAndOnlyAdminMaySubmit() throws Exception {
        var internDetail = accounts.requireAccountDetail(internId);

        mockMvc.perform(post("/admin/accounts/" + internId + "/edit")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .param("userVersion", String.valueOf(internDetail.userVersion()))
                        .param("profileVersion", String.valueOf(internDetail.profileVersion()))
                        .param("email", "intern@example.com")
                        .param("displayName", "Intern One")
                        .param("studentCode", "STU-001")
                        .param("internshipStart", "2026-09-01")
                        .param("internshipEnd", "2026-12-15"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/accounts/" + internId + "?updated"));

        var intern = users.findById(internId).orElseThrow();
        assertThat(intern.getGlobalRole()).isEqualTo(GlobalRole.INTERN);
        assertThat(intern.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);

        mockMvc.perform(post("/admin/accounts/" + mentorId + "/edit")
                        .with(user("mentor@example.com").roles("MENTOR"))
                        .with(csrf())
                        .param("userVersion", "1")
                        .param("email", "hacked@example.com")
                        .param("displayName", "Hacked"))
                .andExpect(status().isForbidden());
    }

    private void activate(String email, String password) throws Exception {
        String rawToken = mail.activationTokenFor(email);
        mockMvc.perform(post("/activate")
                        .with(csrf())
                        .param("token", rawToken)
                        .param("password", password)
                        .param("confirmPassword", password))
                .andExpect(status().is3xxRedirection());
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