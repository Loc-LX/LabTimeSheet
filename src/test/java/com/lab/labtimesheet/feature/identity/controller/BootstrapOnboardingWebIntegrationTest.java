package com.lab.labtimesheet.feature.identity.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class BootstrapOnboardingWebIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void bootstrapOffersSmtpAfterTheFirstAdminSignsIn() throws Exception {
        MockHttpSession session = new MockHttpSession();
        var bootstrapResult = mockMvc.perform(post("/bootstrap")
                        .session(session)
                        .with(csrf())
                        .param("email", " ADMIN@EXAMPLE.COM ")
                        .param("displayName", "First Admin")
                        .param("password", "correct horse battery staple"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/smtp?onboarding"))
                .andReturn();
        assertThat(bootstrapResult.getRequest().getSession(false)).isSameAs(session);

        mockMvc.perform(get("/admin/smtp?onboarding").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));

        mockMvc.perform(post("/login")
                        .session(session)
                        .with(csrf())
                        .param("username", " ADMIN@EXAMPLE.COM ")
                        .param("password", "correct horse battery staple"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", org.hamcrest.Matchers.containsString(
                        "/admin/smtp?onboarding")));

        mockMvc.perform(get("/admin/smtp?onboarding").with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Configure SMTP now")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Defer SMTP")));
    }

    @Test
    void fiveDistinctDeferralConfirmationsAreSequentialAndOnlyTheLastCanFinish() throws Exception {
        initializeAdmin();
        var first = mockMvc.perform(get("/admin/smtp/defer")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("smtp/defer"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("/assets/theme.js")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-sidebar-toggle")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Account onboarding is disabled")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Back")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Configure SMTP")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("Finish without SMTP"))))
                .andReturn();
        MockHttpSession session = (MockHttpSession) first.getRequest().getSession(false);
        assertThat(session).isNotNull();

        assertStep(session, "Activation resend is disabled", false);
        assertStep(session, "Password recovery is disabled", false);
        assertStep(session, "Workflow email delivery is less immediate", false);
        assertStep(session, "I acknowledge this installation remains restricted", true);

        mockMvc.perform(post("/admin/smtp/defer/finish")
                        .session(session)
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"));

        mockMvc.perform(get("/dashboard")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "This installation remains restricted until tested SMTP is active.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/admin/smtp\"")));

        mockMvc.perform(get("/admin/accounts/new")
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "This installation remains restricted until tested SMTP is active.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/admin/smtp\"")));
    }

    @Test
    void bootstrapValidationRetainsSafeFieldsButNeverThePassword() throws Exception {
        mockMvc.perform(post("/bootstrap")
                        .with(csrf())
                        .param("email", "not-an-email")
                        .param("displayName", "Safe Admin Name")
                        .param("password", "must-not-be-rendered"))
                .andExpect(status().isOk())
                .andExpect(view().name("bootstrap/form"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("valid email address")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Safe Admin Name")))
                .andExpect(content().string(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.containsString("must-not-be-rendered"))));
    }

    private void initializeAdmin() throws Exception {
        mockMvc.perform(post("/bootstrap")
                        .with(csrf())
                        .param("email", "admin@example.com")
                        .param("displayName", "Admin")
                        .param("password", "correct horse battery staple"))
                .andExpect(status().is3xxRedirection());
    }

    private void assertStep(MockHttpSession session, String warning, boolean finishVisible) throws Exception {
        mockMvc.perform(post("/admin/smtp/defer/next")
                        .session(session)
                        .with(user("admin@example.com").roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/smtp/defer"));

        var matcher = finishVisible
                ? org.hamcrest.Matchers.containsString("Finish without SMTP")
                : org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Finish without SMTP"));
        mockMvc.perform(get("/admin/smtp/defer")
                        .session(session)
                        .with(user("admin@example.com").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(warning)))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Back")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Configure SMTP")))
                .andExpect(content().string(matcher));
    }
}
