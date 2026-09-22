package com.lab.labtimesheet.feature.identity.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.identity.model.AccountStatus;
import com.lab.labtimesheet.feature.identity.model.dto.CreateAccountCommand;
import com.lab.labtimesheet.feature.identity.service.AccountService;
import com.lab.labtimesheet.feature.identity.service.BootstrapService;
import com.lab.labtimesheet.platform.model.GlobalRole;
import com.lab.labtimesheet.platform.model.SecurityMode;
import com.lab.labtimesheet.platform.model.dto.SmtpConnection;
import com.lab.labtimesheet.platform.model.dto.SmtpDraft;
import com.lab.labtimesheet.platform.service.SmtpConfigurationService;
import com.lab.labtimesheet.platform.service.SmtpProbe;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.mock.web.MockHttpSession;

@Import({TestcontainersConfiguration.class, AccountSessionInvalidationWebIntegrationTest.MailProbeConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AccountSessionInvalidationWebIntegrationTest {
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

    @Test
    void adminLockUnlockAndDeactivateRoutesEnforceLoginStateAndExpireSessions() throws Exception {
        bootstrap.bootstrap("admin@example.com", "Admin", "correct horse battery staple");
        long adminId = accounts.requireActiveAdminId("admin@example.com");
        long draftId = smtp.saveDraft(adminId,
                new SmtpDraft("mailpit", 1025, SecurityMode.NONE, null, null, "admin@example.com", "Lab Timesheet"));
        smtp.testDraft(draftId, adminId, "admin@example.com");
        smtp.activate(draftId, adminId);

        var creation = accounts.create(new CreateAccountCommand(
                "mentor@example.com", "Mentor", GlobalRole.MENTOR, null, null, null), adminId);
        assertThat(accounts.activate(mail.token(), "a secure mentor password")).isTrue();

        var login = mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "mentor@example.com")
                        .param("password", "a secure mentor password"))
                .andExpect(authenticated().withUsername("mentor@example.com"))
                .andReturn();
        HttpSession session = login.getRequest().getSession(false);
        mockMvc.perform(post("/admin/accounts/{id}/lock", creation.userId())
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(redirectedUrl("/admin/accounts/" + creation.userId()));
        assertThat(accounts.requireIdentityById(creation.userId()).status()).isEqualTo(AccountStatus.LOCKED);

        mockMvc.perform(get("/admin/accounts/new").session((MockHttpSession) session))
                .andExpect(redirectedUrl("/login"))
                .andExpect(unauthenticated());

        mockMvc.perform(post("/admin/accounts/{id}/unlock", creation.userId())
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(redirectedUrl("/admin/accounts/" + creation.userId()));
        var secondLogin = mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "mentor@example.com")
                        .param("password", "a secure mentor password"))
                .andExpect(authenticated().withUsername("mentor@example.com"))
                .andReturn();
        MockHttpSession secondSession = (MockHttpSession) secondLogin.getRequest().getSession(false);

        mockMvc.perform(post("/admin/accounts/{id}/deactivate", creation.userId())
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(redirectedUrl("/admin/accounts/" + creation.userId()));
        assertThat(accounts.requireIdentityById(creation.userId()).status()).isEqualTo(AccountStatus.DEACTIVATED);
        mockMvc.perform(get("/dashboard").session(secondSession))
                .andExpect(redirectedUrl("/login"))
                .andExpect(unauthenticated());
        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", "mentor@example.com")
                        .param("password", "a secure mentor password"))
                .andExpect(unauthenticated());
    }

    @Test
    void successfulLoginRotatesThePreAuthenticationSessionId() throws Exception {
        bootstrap.bootstrap("admin@example.com", "Admin", "correct horse battery staple");

        MockHttpSession preAuthenticationSession = (MockHttpSession) mockMvc.perform(get("/login"))
                .andReturn()
                .getRequest()
                .getSession(false);
        String preAuthenticationId = preAuthenticationSession.getId();

        var login = mockMvc.perform(post("/login")
                        .session(preAuthenticationSession)
                        .with(csrf())
                        .param("username", "admin@example.com")
                        .param("password", "correct horse battery staple"))
                .andExpect(authenticated().withUsername("admin@example.com"))
                .andReturn();

        assertThat(login.getRequest().getSession(false).getId()).isNotEqualTo(preAuthenticationId);
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
        private String token;

        @Override
        public void send(SmtpConnection connection, String recipient, String subject, String body) {
            int marker = body.indexOf("token=");
            if (marker >= 0) {
                token = body.substring(marker + "token=".length()).trim();
            }
        }

        String token() {
            return token;
        }
    }
}
