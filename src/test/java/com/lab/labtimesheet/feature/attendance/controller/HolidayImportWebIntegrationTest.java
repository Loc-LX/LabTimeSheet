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
import com.lab.labtimesheet.feature.attendance.model.dto.HolidayCandidate;
import com.lab.labtimesheet.feature.attendance.service.HolidayApiClient;
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
import org.springframework.util.StringUtils;

@Import({
    TestcontainersConfiguration.class,
    HolidayImportWebIntegrationTest.MailProbeConfiguration.class,
    HolidayImportWebIntegrationTest.FakeHolidayApiConfiguration.class
})
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class HolidayImportWebIntegrationTest {
    private static final String PASSWORD = "correct horse battery staple";
    private static final String ADMIN_EMAIL = "holiday-admin@example.test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountService accounts;

    @Autowired
    private SmtpConfigurationService smtp;

    @Autowired
    private RecordingSmtpProbe mail;

    @Test
    void adminPreviewPreselectsPublicRowsAndImportedSelectionPersists() throws Exception {
        bootstrapFirstAdminThroughTheForm();
        MockHttpSession adminSession = login(ADMIN_EMAIL, PASSWORD, "ADMIN");

        String previewHtml = mockMvc.perform(get("/attendance/calendar/holidays").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("National Day")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Lunar New Year Eve")))
                .andReturn()
                .getResponse()
                .getContentAsString();
        assertThat(StringUtils.countOccurrencesOf(previewHtml, "checked=\"checked\"")).isEqualTo(2);

        mockMvc.perform(post("/attendance/calendar/holidays").session(adminSession)
                        .with(csrf())
                        .param("year", "2026")
                        .param("selections[0].uuid", "11111111-1111-1111-1111-111111111111")
                        .param("selections[0].selected", "true")
                        .param("selections[0].dayOff", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/attendance/calendar/holidays?year=2026"));

        mockMvc.perform(get("/attendance/calendar/holidays").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("already imported")));
    }

    @Test
    void internIsForbiddenFromHolidayPreview() throws Exception {
        bootstrapFirstAdminThroughTheForm();
        MockHttpSession adminSession = login(ADMIN_EMAIL, PASSWORD, "ADMIN");
        long adminId = accounts.requireActiveAdminId(ADMIN_EMAIL);
        configureSmtp(adminId);
        createAndActivate(adminId, new CreateAccountCommand(
                "holiday-intern@example.test",
                "Holiday Intern",
                GlobalRole.INTERN,
                "INT-HOLIDAY",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 12, 31)));

        mockMvc.perform(get("/attendance/calendar/holidays")
                        .session(login("holiday-intern@example.test", PASSWORD, "INTERN")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/attendance/calendar/holidays")
                        .session(login("holiday-intern@example.test", PASSWORD, "INTERN"))
                        .with(csrf())
                        .param("year", "2026")
                        .param("selections[0].uuid", "11111111-1111-1111-1111-111111111111")
                        .param("selections[0].selected", "true")
                        .param("selections[0].dayOff", "true"))
                .andExpect(status().isForbidden());
    }

    private void bootstrapFirstAdminThroughTheForm() throws Exception {
        mockMvc.perform(post("/bootstrap")
                        .with(csrf())
                        .param("email", ADMIN_EMAIL)
                        .param("displayName", "Admin")
                        .param("password", PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/smtp?onboarding"));
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

    @TestConfiguration(proxyBeanMethods = false)
    static class FakeHolidayApiConfiguration {
        @Bean
        @Primary
        HolidayApiClient holidayApiClient() {
            return year -> List.of(
                    new HolidayCandidate(
                            "11111111-1111-1111-1111-111111111111",
                            "National Day",
                            LocalDate.of(2026, 9, 2),
                            LocalDate.of(2026, 9, 2),
                            true),
                    new HolidayCandidate(
                            "22222222-2222-2222-2222-222222222222",
                            "Lunar New Year Eve",
                            LocalDate.of(2026, 2, 16),
                            LocalDate.of(2026, 2, 17),
                            false),
                    new HolidayCandidate(
                            "33333333-3333-3333-3333-333333333333",
                            "Christmas Day",
                            LocalDate.of(2026, 12, 25),
                            LocalDate.of(2026, 12, 25),
                            true));
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