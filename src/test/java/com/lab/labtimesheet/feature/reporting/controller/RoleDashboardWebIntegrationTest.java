package com.lab.labtimesheet.feature.reporting.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.account.model.GlobalRole;
import com.lab.labtimesheet.feature.account.model.dto.CreateAccountCommand;
import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.account.service.BootstrapService;
import com.lab.labtimesheet.feature.integration.model.SecurityMode;
import com.lab.labtimesheet.feature.integration.model.dto.SmtpConnection;
import com.lab.labtimesheet.feature.integration.model.dto.SmtpDraft;
import com.lab.labtimesheet.feature.integration.service.SmtpConfigurationService;
import com.lab.labtimesheet.feature.integration.service.SmtpProbe;
import com.lab.labtimesheet.feature.project.model.dto.ProjectCreateCommand;
import com.lab.labtimesheet.feature.project.service.ProjectQueryService;
import com.lab.labtimesheet.feature.project.service.ProjectService;
import com.lab.labtimesheet.feature.task.model.TaskStatus;
import com.lab.labtimesheet.feature.task.model.dto.CreateTaskCommand;
import com.lab.labtimesheet.feature.task.service.TaskService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Import({TestcontainersConfiguration.class, RoleDashboardWebIntegrationTest.MailProbeConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RoleDashboardWebIntegrationTest {

    private static final Pattern NAVIGATION_LINK = Pattern.compile(
            "<a class=\"(?:brand|nav-link|account|icon-button)\" href=\"([^\"]+)\"");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private BootstrapService bootstrap;

    @Autowired
    private AccountService accounts;

    @Autowired
    private SmtpConfigurationService smtp;

    @Autowired
    private RecordingSmtpProbe mail;

    @Autowired
    private ProjectService projects;

    @Autowired
    private ProjectQueryService projectQueries;

    @Autowired
    private TaskService tasks;

    @Test
    void mentorAndInternDashboardsRenderRealScopedProjectTaskAndAttendanceData() throws Exception {
        long adminId = initializeAdminAndSmtp();
        long mentorId = createActiveAccount(
                adminId,
                new CreateAccountCommand(
                        "mentor@example.test", "Minh Mentor", GlobalRole.MENTOR, null, null, null));
        long internId = createActiveAccount(
                adminId,
                new CreateAccountCommand(
                        "intern@example.test",
                        "Mai Intern",
                        GlobalRole.INTERN,
                        "INT-001",
                        LocalDate.of(2026, 8, 1),
                        LocalDate.of(2026, 12, 31)));
        accounts.activateInternship(internId, adminId);

        long projectId = projects.create(
                mentorId,
                new ProjectCreateCommand(
                        "Intern Portal",
                        "Portal refresh",
                        LocalDate.of(2026, 8, 14),
                        LocalDate.of(2026, 9, 30),
                        internId));
        projects.activate(mentorId, projectId);
        long membershipId = projectQueries.taskContext(internId, projectId).currentLeaderMembershipId();
        var task = tasks.create(
                "intern@example.test",
                new CreateTaskCommand(
                        projectId,
                        membershipId,
                        "Resolve accessibility review",
                        null,
                        LocalDate.of(2026, 8, 20)));
        tasks.changeStatus("intern@example.test", projectId, task.id(), TaskStatus.BLOCKED);

        mvc.perform(get("/dashboard").with(user("mentor@example.test").roles("MENTOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Good morning, Minh Mentor")))
                .andExpect(content().string(containsString(
                        "Active owned Projects</div><div class=\"metric-value\">1")))
                .andExpect(content().string(containsString(
                        "Active members</div><div class=\"metric-value\">1")))
                .andExpect(content().string(containsString(
                        "Blocked Tasks</div><div class=\"metric-value\">1")));

        mvc.perform(get("/dashboard").with(user("intern@example.test").roles("INTERN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Attendance, assigned work, and Project activity for Mai Intern.")))
                .andExpect(content().string(containsString("Not checked in")))
                .andExpect(content().string(containsString(
                        "Active Projects</div><div class=\"metric-value\">1")))
                .andExpect(content().string(containsString(
                        "Assigned Tasks</div><div class=\"metric-value\">1")))
                .andExpect(content().string(containsString("Resolve accessibility review")))
                .andExpect(content().string(containsString("BLOCKED")))
                .andExpect(content().string(containsString("20/08/2026")))
                .andExpect(content().string(containsString("data-tooltip=\"Daily Project Work Report\"")));

        followEveryVisibleNavigationLink("admin@example.test", "ADMIN", true);
        followEveryVisibleNavigationLink("mentor@example.test", "MENTOR", false);
        followEveryVisibleNavigationLink("intern@example.test", "INTERN", false);
    }

    private void followEveryVisibleNavigationLink(String email, String role, boolean expectsSmtpSettings) throws Exception {
        String dashboard = mvc.perform(get("/dashboard").with(user(email).roles(role)))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
        Matcher matcher = NAVIGATION_LINK.matcher(dashboard);
        Set<String> paths = new LinkedHashSet<>();
        while (matcher.find()) {
            paths.add(matcher.group(1));
        }
        assertThat(paths).isNotEmpty();
        if (expectsSmtpSettings) {
            assertThat(paths).contains("/admin/smtp");
        } else {
            assertThat(paths).doesNotContain("/admin/smtp");
        }
        for (String path : paths) {
            var request = mvc.perform(get(path).with(user(email).roles(role)));
            if ("/attendance/requests".equals(path)) {
                request.andExpect(status().is3xxRedirection())
                        .andExpect(redirectedUrl("/attendance/leave"));
            } else if ("/reports/daily".equals(path) && "INTERN".equals(role)) {
                // UI-019 shows this entry to an Intern only while a current leadership term makes
                // at least one open Project eligible, and DailyProjectWorkReportController resolves
                // that single Project before rendering, so the Intern entry is a redirect.
                //
                // A Mentor holds the same entry unconditionally and may own several Projects, so
                // the controller renders the selector and answers 200. This branch tested every
                // role until 13 September 2026 and the Mentor case was hidden behind a context
                // failure in the same class.
                request.andExpect(status().is3xxRedirection());
            } else {
                request.andExpect(status().isOk());
            }
        }
    }

    private long initializeAdminAndSmtp() {
        bootstrap.bootstrap("admin@example.test", "Admin", "correct horse battery staple");
        long adminId = accounts.requireActiveAdminId("admin@example.test");
        long draftId = smtp.saveDraft(adminId, new SmtpDraft(
                "mailpit", 1025, SecurityMode.NONE, null, null, "admin@example.test", "Lab Timesheet"));
        smtp.testDraft(draftId, adminId);
        smtp.activate(draftId, adminId);
        mail.clear();
        return adminId;
    }

    private long createActiveAccount(long adminId, CreateAccountCommand command) {
        var creation = accounts.create(command, adminId);
        assertThat(creation.deliverySucceeded()).isTrue();
        assertThat(accounts.activate(mail.activationTokenFor(command.email()), "correct horse battery staple"))
                .isTrue();
        return creation.userId();
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
