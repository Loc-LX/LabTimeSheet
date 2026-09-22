package com.lab.labtimesheet.feature.integration.controller;

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
import com.lab.labtimesheet.feature.account.service.BootstrapService;
import com.lab.labtimesheet.platform.model.dto.SmtpConnection;
import com.lab.labtimesheet.platform.service.SmtpProbe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mail.MailSendException;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@Import({TestcontainersConfiguration.class, SmtpOnboardingWebIntegrationTest.ProbeConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class SmtpOnboardingWebIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BootstrapService bootstrap;

    @Autowired
    private RecordingProbe probe;

    @BeforeEach
    void initializeAdmin() {
        bootstrap.bootstrap("admin@example.com", "Admin", "correct horse battery staple");
    }

    @Test
    void adminCanSaveTestAndActivateSmtpWithVisibleStatus() throws Exception {
        mockMvc.perform(post("/admin/smtp/draft")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .param("host", "mailpit")
                        .param("port", "1025")
                        .param("securityMode", "NONE")
                        .param("username", "smtp-user")
                        .param("password", "smtp-secret")
                        .param("fromAddress", "notifications@example.com")
                        .param("fromName", "Lab Timesheet"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/smtp?saved"));

        var draftPage = mockMvc.perform(get("/admin/smtp").with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Draft saved")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Test connection")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Activate SMTP"))))
                .andReturn();
        String html = draftPage.getResponse().getContentAsString();
        String draftId = html.replaceAll("(?s).*name=\"draftId\" value=\"([0-9]+)\".*", "$1");
        assertThat(draftId).matches("[0-9]+");

        mockMvc.perform(post("/admin/smtp/test")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .param("draftId", draftId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/smtp?tested"));
        assertThat(probe.recipients).contains("admin@example.com");

        mockMvc.perform(get("/admin/smtp").with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Test passed")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Activate SMTP")));

        mockMvc.perform(post("/admin/smtp/activate")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .param("draftId", draftId))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/smtp?activated"));

        mockMvc.perform(get("/admin/smtp").with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("SMTP is active")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("restricted installation"))));

        mockMvc.perform(get("/admin/accounts/new").with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("installation remains restricted"))));
    }

    @Test
    void invalidDraftRetainsOnlySafeFieldsAndRendersValidationErrors() throws Exception {
        mockMvc.perform(post("/admin/smtp/draft")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .param("host", "")
                        .param("port", "70000")
                        .param("securityMode", "")
                        .param("username", "smtp-user")
                        .param("password", "")
                        .param("fromAddress", "not-an-email")
                        .param("fromName", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("smtp/form"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/assets/theme.js")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"smtp-form-error-summary\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "aria-labelledby=\"smtp-form-error-summary-title\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"smtp-host\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "aria-describedby=\"smtp-host-error\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"smtp-host-error\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"smtp-port\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "aria-describedby=\"smtp-port-error\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"smtp-port-error\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"smtp-security-mode\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "aria-describedby=\"smtp-security-mode-error\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "id=\"smtp-security-mode-error\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"smtp-username\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"smtp-password\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "aria-describedby=\"smtp-authentication-error\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "id=\"smtp-username\" autocomplete=\"username\" aria-invalid=\"true\""
                                + " aria-describedby=\"smtp-authentication-error\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "id=\"smtp-authentication-error\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"smtp-from-address\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "aria-describedby=\"smtp-from-address-error\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "id=\"smtp-from-address-error\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("id=\"smtp-from-name\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "aria-describedby=\"smtp-from-name-error\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "id=\"smtp-from-name-error\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Host is required")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Port must be between")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("valid email address")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("value=\"smtp-secret\""))));
    }

    @Test
    void invalidDraftRetainsSafeValuesButNeverTheSubmittedPassword() throws Exception {
        mockMvc.perform(post("/admin/smtp/draft")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .param("host", "")
                        .param("port", "1025")
                        .param("securityMode", "NONE")
                        .param("username", "safe-smtp-user")
                        .param("password", "must-not-be-rendered")
                        .param("fromAddress", "notifications@example.com")
                        .param("fromName", "Safe sender name"))
                .andExpect(status().isOk())
                .andExpect(view().name("smtp/form"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("safe-smtp-user")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Safe sender name")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("must-not-be-rendered"))));
    }

    @Test
    void restrictedWarningPersistsOnAdminPagesUntilActivationAndMutationsRequireCsrf() throws Exception {
        mockMvc.perform(get("/admin/accounts/new").with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "installation remains restricted")));

        mockMvc.perform(post("/admin/smtp/draft")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .param("host", "mailpit")
                        .param("port", "1025")
                        .param("securityMode", "NONE")
                        .param("fromAddress", "admin@example.com")
                        .param("fromName", "Lab Timesheet"))
                .andExpect(status().isForbidden());
    }

    @Test
    void failedSmtpTestRendersActionableFeedbackWithoutActivatingTheDraft() throws Exception {
        mockMvc.perform(post("/admin/smtp/draft")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .param("host", "mailpit")
                        .param("port", "1025")
                        .param("securityMode", "NONE")
                        .param("fromAddress", "notifications@example.com")
                        .param("fromName", "Lab Timesheet"))
                .andExpect(status().is3xxRedirection());

        String html = mockMvc.perform(get("/admin/smtp")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andReturn().getResponse().getContentAsString();
        String draftId = html.replaceAll("(?s).*name=\"draftId\" value=\"([0-9]+)\".*", "$1");
        String rawDiagnostic = "AUTH rejected for smtp-secret-raw-diagnostic";
        probe.failure = new MailSendException(rawDiagnostic);

        mockMvc.perform(post("/admin/smtp/test")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf())
                        .param("draftId", draftId))
                .andExpect(status().isOk())
                .andExpect(view().name("smtp/form"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "SMTP test failed. Verify the draft settings and server availability, then try again.")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString(rawDiagnostic))))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Activate SMTP"))));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeConfiguration {
        @Bean
        @Primary
        RecordingProbe recordingProbe() {
            return new RecordingProbe();
        }
    }

    static final class RecordingProbe implements SmtpProbe {
        private final List<String> recipients = new ArrayList<>();
        private MailSendException failure;

        @Override
        public void send(SmtpConnection connection, String recipient, String subject, String body) {
            if (failure != null) {
                throw failure;
            }
            recipients.add(recipient);
        }
    }
}
