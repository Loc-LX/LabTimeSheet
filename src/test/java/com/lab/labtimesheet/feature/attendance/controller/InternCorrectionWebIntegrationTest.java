package com.lab.labtimesheet.feature.attendance.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lab.labtimesheet.feature.account.model.GlobalRole;
import com.lab.labtimesheet.feature.account.model.dto.CreateAccountCommand;
import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.attendance.service.AttendanceApplicationService;
import com.lab.labtimesheet.feature.integration.model.SecurityMode;
import com.lab.labtimesheet.feature.integration.model.dto.SmtpConnection;
import com.lab.labtimesheet.feature.integration.model.dto.SmtpDraft;
import com.lab.labtimesheet.feature.integration.service.SmtpConfigurationService;
import com.lab.labtimesheet.feature.integration.service.SmtpProbe;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

@Import(InternCorrectionWebIntegrationTest.IntegrationConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class InternCorrectionWebIntegrationTest {
    private static final String PASSWORD = "correct horse battery staple";
    private static final String ADMIN_EMAIL = "correction-admin@example.test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountService accounts;

    @Autowired
    private SmtpConfigurationService smtp;

    @Autowired
    private RecordingSmtpProbe mail;

    @Autowired
    private AttendanceApplicationService attendance;

    @Autowired
    private MutableClock clock;

    @Test
    void internCanSubmitCorrectionAndSeeItInPriorCorrections() throws Exception {
        MockHttpSession adminSession = seedAdmin();
        long adminId = accounts.requireActiveAdminId(ADMIN_EMAIL);
        configureSmtp(adminId);
        long internId = createAndActivateIntern(adminId);
        MockHttpSession internSession = login("correction-intern@example.test", PASSWORD, "INTERN");

        clock.set(Instant.parse("2026-08-14T02:00:00Z"));
        attendance.checkIn(internId);
        clock.set(Instant.parse("2026-08-14T09:00:01Z"));

        mockMvc.perform(get("/intern/corrections").session(internSession))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("New correction")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Prior corrections")));

        mockMvc.perform(post("/intern/corrections").session(internSession)
                        .with(csrf())
                        .param("workDate", "2026-08-14")
                        .param("proposedCheckoutTime", "15:45")
                        .param("reason", "Forgot to check out"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/intern/corrections"));

        mockMvc.perform(get("/intern/corrections").session(internSession))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "Correction submitted for 14/08/2026 at 15:45")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Forgot to check out")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("PENDING")));
    }

    @Test
    void duplicateCorrectionFlowsBackAsErrorFlashMessage() throws Exception {
        MockHttpSession adminSession = seedAdmin();
        long adminId = accounts.requireActiveAdminId(ADMIN_EMAIL);
        configureSmtp(adminId);
        long internId = createAndActivateIntern(adminId);
        MockHttpSession internSession = login("correction-intern@example.test", PASSWORD, "INTERN");

        clock.set(Instant.parse("2026-08-14T02:00:00Z"));
        attendance.checkIn(internId);
        clock.set(Instant.parse("2026-08-14T09:00:01Z"));

        mockMvc.perform(post("/intern/corrections").session(internSession)
                        .with(csrf())
                        .param("workDate", "2026-08-14")
                        .param("proposedCheckoutTime", "15:45")
                        .param("reason", "Forgot to check out"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(post("/intern/corrections").session(internSession)
                        .with(csrf())
                        .param("workDate", "2026-08-14")
                        .param("proposedCheckoutTime", "16:00")
                        .param("reason", "Duplicate"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/intern/corrections").session(internSession))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("ALREADY_SUBMITTED")));
    }

    @Test
    void mentorAndAdminCannotOpenCorrectionPage() throws Exception {
        MockHttpSession adminSession = seedAdmin();
        long adminId = accounts.requireActiveAdminId(ADMIN_EMAIL);

        mockMvc.perform(get("/intern/corrections").session(adminSession)).andExpect(status().isForbidden());
        mockMvc.perform(post("/intern/corrections").session(adminSession)
                        .with(csrf())
                        .param("workDate", "2026-08-14")
                        .param("proposedCheckoutTime", "15:45")
                        .param("reason", "Admin correction"))
                .andExpect(status().isForbidden());

        configureSmtp(adminId);
        createAndActivate(adminId, new CreateAccountCommand(
                "correction-mentor@example.test", "Correction Mentor", GlobalRole.MENTOR, null, null, null));
        MockHttpSession mentorSession = login("correction-mentor@example.test", PASSWORD, "MENTOR");
        mockMvc.perform(get("/intern/corrections").session(mentorSession)).andExpect(status().isForbidden());
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

    private long createAndActivateIntern(long adminId) {
        var creation = accounts.create(new CreateAccountCommand(
                "correction-intern@example.test",
                "Correction Intern",
                GlobalRole.INTERN,
                "INT-CORR",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 12, 31)), adminId);
        assertThat(creation.deliverySucceeded()).isTrue();
        assertThat(accounts.activate(mail.activationTokenFor("correction-intern@example.test"), PASSWORD)).isTrue();
        accounts.activateInternship(creation.userId(), adminId);
        return creation.userId();
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
    static class IntegrationConfiguration {

        @Bean
        @ServiceConnection
        PostgreSQLContainer postgresContainer() {
            return new PostgreSQLContainer(DockerImageName.parse("postgres:18.4"));
        }

        @Bean
        @Primary
        MutableClock mutableClock() {
            return new MutableClock(Instant.parse("2026-08-14T00:00:00Z"));
        }

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

    static final class MutableClock extends Clock {

        private Instant instant;

        MutableClock(Instant instant) {
            this.instant = instant;
        }

        void set(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }

    record Message(String recipient, String body) {
    }
}