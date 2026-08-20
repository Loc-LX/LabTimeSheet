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
import com.lab.labtimesheet.feature.attendance.model.entity.AttendancePolicyEntity;
import com.lab.labtimesheet.feature.attendance.repository.AttendancePolicyRepository;
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

@Import(AttendancePolicyWebIntegrationTest.IntegrationConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AttendancePolicyWebIntegrationTest {
    private static final String PASSWORD = "correct horse battery staple";
    private static final String ADMIN_EMAIL = "policy-admin@example.test";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AccountService accounts;

    @Autowired
    private SmtpConfigurationService smtp;

    @Autowired
    private RecordingSmtpProbe mail;

    @Autowired
    private AttendancePolicyRepository policies;

    @Test
    void adminSchedulesAndReplacesFuturePolicyVersionThroughTheForm() throws Exception {
        MockHttpSession adminSession = seedAdmin();
        long adminId = accounts.requireActiveAdminId(ADMIN_EMAIL);
        configureSmtp(adminId);

        mockMvc.perform(get("/attendance/policy").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Attendance policy")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("EFFECTIVE")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("08:30")));

        mockMvc.perform(post("/attendance/policy").session(adminSession)
                        .with(csrf())
                        .param("effectiveFrom", "2026-09-01")
                        .param("zoneId", "Asia/Ho_Chi_Minh")
                        .param("scheduledStart", "09:00")
                        .param("scheduledEnd", "18:00")
                        .param("checkInGraceMinutes", "20")
                        .param("checkoutGraceMinutes", "0")
                        .param("monthlyLeaveQuota", "5")
                        .param("violationPenalty", "0.5")
                        .param("workdays", "MONDAY", "WEDNESDAY", "FRIDAY"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/attendance/policy"));

        List<AttendancePolicyEntity> timeline = policies.findAllByOrderByEffectiveFromAsc();
        assertThat(timeline).hasSize(2);
        AttendancePolicyEntity scheduled = timeline.get(1);
        assertThat(scheduled.effectiveFrom()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(scheduled.toDomain().checkInGraceMinutes()).isEqualTo(20);
        assertThat(scheduled.toDomain().checkoutGraceMinutes()).isEqualTo(0);
        assertThat(scheduled.toDomain().monthlyLeaveQuota()).isEqualTo(5);
        assertThat(scheduled.toDomain().violationPenalty()).isEqualByComparingTo("0.5");
        assertThat(scheduled.toDomain().workdays())
                .containsExactlyInAnyOrder(java.time.DayOfWeek.MONDAY, java.time.DayOfWeek.WEDNESDAY, java.time.DayOfWeek.FRIDAY);

        mockMvc.perform(get("/attendance/policy").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("FUTURE")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Attendance policy version scheduled")));

        mockMvc.perform(post("/attendance/policy/{id}", scheduled.toDomain().id()).session(adminSession)
                        .with(csrf())
                        .param("version", "0")
                        .param("effectiveFrom", "2026-09-01")
                        .param("zoneId", "Asia/Ho_Chi_Minh")
                        .param("scheduledStart", "08:30")
                        .param("scheduledEnd", "15:30")
                        .param("checkInGraceMinutes", "30")
                        .param("checkoutGraceMinutes", "30")
                        .param("monthlyLeaveQuota", "3")
                        .param("violationPenalty", "0.25")
                        .param("workdays", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/attendance/policy"));

        AttendancePolicyEntity replaced = policies.findAllByOrderByEffectiveFromAsc().get(1);
        assertThat(replaced.toDomain().checkInGraceMinutes()).isEqualTo(30);
        assertThat(replaced.toDomain().checkoutGraceMinutes()).isEqualTo(30);
        assertThat(replaced.version()).isEqualTo(1);
    }

    @Test
    void nonAdminAccountsAreDeniedPolicyManagement() throws Exception {
        MockHttpSession adminSession = seedAdmin();
        long adminId = accounts.requireActiveAdminId(ADMIN_EMAIL);
        configureSmtp(adminId);
        createAndActivate(adminId, new CreateAccountCommand(
                "policy-mentor@example.test", "Policy Mentor", GlobalRole.MENTOR, null, null, null));
        createAndActivate(adminId, new CreateAccountCommand(
                "policy-intern@example.test",
                "Policy Intern",
                GlobalRole.INTERN,
                "INT-POLICY",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 12, 31)));

        mockMvc.perform(get("/attendance/policy")
                        .session(login("policy-mentor@example.test", PASSWORD, "MENTOR")))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/attendance/policy")
                        .session(login("policy-intern@example.test", PASSWORD, "INTERN")))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/attendance/policy")
                        .session(login("policy-intern@example.test", PASSWORD, "INTERN"))
                        .with(csrf())
                        .param("effectiveFrom", "2026-09-01")
                        .param("zoneId", "Asia/Ho_Chi_Minh")
                        .param("scheduledStart", "09:00")
                        .param("scheduledEnd", "18:00")
                        .param("checkInGraceMinutes", "20")
                        .param("checkoutGraceMinutes", "0")
                        .param("monthlyLeaveQuota", "5")
                        .param("violationPenalty", "0.5")
                        .param("workdays", "MONDAY"))
                .andExpect(status().isForbidden());
        assertThat(policies.findAllByOrderByEffectiveFromAsc()).hasSize(1);
    }

    @Test
    void invalidScheduleInputsReRenderWithFieldErrorsWithoutPersisting() throws Exception {
        MockHttpSession adminSession = seedAdmin();
        long adminId = accounts.requireActiveAdminId(ADMIN_EMAIL);
        configureSmtp(adminId);

        mockMvc.perform(post("/attendance/policy").session(adminSession)
                        .with(csrf())
                        .param("effectiveFrom", "2026-09-01")
                        .param("zoneId", "Asia/Ho_Chi_Minh")
                        .param("scheduledStart", "08:30")
                        .param("scheduledEnd", "15:30")
                        .param("checkInGraceMinutes", "721")
                        .param("checkoutGraceMinutes", "30")
                        .param("monthlyLeaveQuota", "5")
                        .param("violationPenalty", "0.5")
                        .param("workdays", "MONDAY"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Check-in grace must be between 0 and 720")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("721")));

        mockMvc.perform(post("/attendance/policy").session(adminSession)
                        .with(csrf())
                        .param("effectiveFrom", "2026-09-01")
                        .param("zoneId", "Mars/Olympus")
                        .param("scheduledStart", "08:30")
                        .param("scheduledEnd", "15:30")
                        .param("checkInGraceMinutes", "30")
                        .param("checkoutGraceMinutes", "30")
                        .param("monthlyLeaveQuota", "5")
                        .param("violationPenalty", "0.5")
                        .param("workdays", "MONDAY"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Timezone must be a valid")));

        mockMvc.perform(post("/attendance/policy").session(adminSession)
                        .with(csrf())
                        .param("effectiveFrom", "2026-09-01")
                        .param("zoneId", "Asia/Ho_Chi_Minh")
                        .param("scheduledStart", "08:30")
                        .param("scheduledEnd", "15:30")
                        .param("checkInGraceMinutes", "30")
                        .param("checkoutGraceMinutes", "30")
                        .param("monthlyLeaveQuota", "32")
                        .param("violationPenalty", "0.5")
                        .param("workdays", "MONDAY"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Monthly leave quota must be between 0 and 31")));

        mockMvc.perform(post("/attendance/policy").session(adminSession)
                        .with(csrf())
                        .param("effectiveFrom", "2026-09-01")
                        .param("zoneId", "Asia/Ho_Chi_Minh")
                        .param("scheduledStart", "08:30")
                        .param("scheduledEnd", "15:30")
                        .param("checkInGraceMinutes", "30")
                        .param("checkoutGraceMinutes", "30")
                        .param("monthlyLeaveQuota", "5")
                        .param("violationPenalty", "1.5")
                        .param("workdays", "MONDAY"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Violation penalty must be between 0 and 1")));

        mockMvc.perform(post("/attendance/policy").session(adminSession)
                        .with(csrf())
                        .param("effectiveFrom", "2026-09-01")
                        .param("zoneId", "Asia/Ho_Chi_Minh")
                        .param("scheduledStart", "08:30")
                        .param("scheduledEnd", "15:30")
                        .param("checkInGraceMinutes", "30")
                        .param("checkoutGraceMinutes", "30")
                        .param("monthlyLeaveQuota", "5")
                        .param("violationPenalty", "0.5"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("At least one workday is required")));

        mockMvc.perform(post("/attendance/policy").session(adminSession)
                        .with(csrf())
                        .param("effectiveFrom", "2026-09-01")
                        .param("zoneId", "Asia/Ho_Chi_Minh")
                        .param("scheduledStart", "18:00")
                        .param("scheduledEnd", "09:00")
                        .param("checkInGraceMinutes", "30")
                        .param("checkoutGraceMinutes", "30")
                        .param("monthlyLeaveQuota", "5")
                        .param("violationPenalty", "0.5")
                        .param("workdays", "MONDAY"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Scheduled end must be after scheduled start")));

        assertThat(policies.findAllByOrderByEffectiveFromAsc()).hasSize(1);
    }

    @Test
    void nonFirstOfFutureMonthEffectiveDateRejectsWithRetainedInput() throws Exception {
        MockHttpSession adminSession = seedAdmin();
        long adminId = accounts.requireActiveAdminId(ADMIN_EMAIL);
        configureSmtp(adminId);

        mockMvc.perform(post("/attendance/policy").session(adminSession)
                        .with(csrf())
                        .param("effectiveFrom", "2026-09-15")
                        .param("zoneId", "Asia/Ho_Chi_Minh")
                        .param("scheduledStart", "08:30")
                        .param("scheduledEnd", "15:30")
                        .param("checkInGraceMinutes", "30")
                        .param("checkoutGraceMinutes", "30")
                        .param("monthlyLeaveQuota", "5")
                        .param("violationPenalty", "0.5")
                        .param("workdays", "MONDAY"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("first day of a calendar month")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("2026-09-15")));

        mockMvc.perform(post("/attendance/policy").session(adminSession)
                        .with(csrf())
                        .param("effectiveFrom", "2026-08-01")
                        .param("zoneId", "Asia/Ho_Chi_Minh")
                        .param("scheduledStart", "08:30")
                        .param("scheduledEnd", "15:30")
                        .param("checkInGraceMinutes", "30")
                        .param("checkoutGraceMinutes", "30")
                        .param("monthlyLeaveQuota", "5")
                        .param("violationPenalty", "0.5")
                        .param("workdays", "MONDAY"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("after the current business date")));

        assertThat(policies.findAllByOrderByEffectiveFromAsc()).hasSize(1);
    }

    @Test
    void replaceWithInvalidValuesRedirectsWithFlashAndLeavesRowUnchanged() throws Exception {
        MockHttpSession adminSession = seedAdmin();
        long adminId = accounts.requireActiveAdminId(ADMIN_EMAIL);
        configureSmtp(adminId);

        mockMvc.perform(post("/attendance/policy").session(adminSession)
                        .with(csrf())
                        .param("effectiveFrom", "2026-09-01")
                        .param("zoneId", "Asia/Ho_Chi_Minh")
                        .param("scheduledStart", "09:00")
                        .param("scheduledEnd", "18:00")
                        .param("checkInGraceMinutes", "20")
                        .param("checkoutGraceMinutes", "0")
                        .param("monthlyLeaveQuota", "5")
                        .param("violationPenalty", "0.5")
                        .param("workdays", "MONDAY"))
                .andExpect(status().is3xxRedirection());

        mockMvc.perform(get("/attendance/policy").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Attendance policy version scheduled")));

        AttendancePolicyEntity scheduled = policies.findAllByOrderByEffectiveFromAsc().get(1);

        mockMvc.perform(post("/attendance/policy/{id}", scheduled.toDomain().id()).session(adminSession)
                        .with(csrf())
                        .param("version", "0")
                        .param("effectiveFrom", "2026-09-01")
                        .param("zoneId", "Mars/Olympus")
                        .param("scheduledStart", "09:00")
                        .param("scheduledEnd", "18:00")
                        .param("checkInGraceMinutes", "721")
                        .param("checkoutGraceMinutes", "0")
                        .param("monthlyLeaveQuota", "5")
                        .param("violationPenalty", "0.5")
                        .param("workdays", "MONDAY"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/attendance/policy"));

        mockMvc.perform(get("/attendance/policy").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Timezone must be a valid")));

        AttendancePolicyEntity unchanged = policies.findAllByOrderByEffectiveFromAsc().get(1);
        assertThat(unchanged.toDomain().checkInGraceMinutes()).isEqualTo(20);
        assertThat(unchanged.toDomain().zoneId().getId()).isEqualTo("Asia/Ho_Chi_Minh");
        assertThat(unchanged.version()).isZero();
    }

    @Test
    void timezoneIsRenderedAsSelectableList() throws Exception {
        MockHttpSession adminSession = seedAdmin();
        long adminId = accounts.requireActiveAdminId(ADMIN_EMAIL);
        configureSmtp(adminId);

        mockMvc.perform(get("/attendance/policy").session(adminSession))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("<select")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("name=\"zoneId\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Asia/Ho_Chi_Minh")));
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