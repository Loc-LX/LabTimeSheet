package com.lab.labtimesheet.feature.identity.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.identity.model.AccountStatus;
import com.lab.labtimesheet.feature.identity.model.dto.CreateAccountCommand;
import com.lab.labtimesheet.feature.identity.repository.AppUserRepository;
import com.lab.labtimesheet.feature.identity.service.AccountService;
import com.lab.labtimesheet.feature.identity.service.BootstrapService;
import com.lab.labtimesheet.feature.internship.model.InternshipStatus;
import com.lab.labtimesheet.feature.internship.service.InternshipService;
import com.lab.labtimesheet.feature.project.model.dto.ProjectCreateCommand;
import com.lab.labtimesheet.feature.project.service.ProjectQueryService;
import com.lab.labtimesheet.feature.project.service.ProjectService;
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
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/** PostgreSQL and MockMvc acceptance coverage for the three-state account reinstatement scenario. */
@Import({TestcontainersConfiguration.class, AccountReinstatementWebIntegrationTest.MailConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class AccountReinstatementWebIntegrationTest {
    private static final String ADMIN_EMAIL = "admin@example.test";
    private static final String A_EMAIL = "reinstated-intern@example.test";
    private static final String B_EMAIL = "reinstated-locked@example.test";
    private static final String C_EMAIL = "reinstated-pending@example.test";
    private static final String A_PASSWORD = "intern's retained password";
    private static final String B_PASSWORD = "mentor's retained password";

    @Autowired private MockMvc mockMvc;
    @Autowired private BootstrapService bootstrap;
    @Autowired private AccountService accounts;
    @Autowired private InternshipService internships;
    @Autowired private SmtpConfigurationService smtp;
    @Autowired private AppUserRepository users;
    @Autowired private ProjectService projects;
    @Autowired private ProjectQueryService projectQueries;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private RecordingProbe mail;

    @BeforeEach
    void initializeAdminAndMail() {
        bootstrap.bootstrap(ADMIN_EMAIL, "Admin", "correct horse battery staple");
        long adminId = accounts.requireActiveAdminId(ADMIN_EMAIL);
        long smtpId = smtp.saveDraft(adminId,
                new SmtpDraft("mailpit", 1025, SecurityMode.NONE, null, null, ADMIN_EMAIL, "Lab Timesheet"));
        smtp.testDraft(smtpId, adminId, ADMIN_EMAIL);
        smtp.activate(smtpId, adminId);
    }

    /**
     * Protects ACC-013, ACC-014, ACC-016, ACC-024, ACC-029, ACC-030, AC-ACC-021 and AC-ACC-022. Observable break: a deactivated
     * user's correct password authenticates, reinstatement changes retained credentials or lock state,
     * pending accounts authenticate before a fresh activation, or reinstatement restores a withdrawn
     * internship or closed Project membership. Hand-derived result: A returns ACTIVE with the same hash,
     * B returns LOCKED with the same lock timestamp until unlock, C returns PENDING_ACTIVATION until resend
     * and activation, and A's withdrawal and closed membership remain in history.
     */
    @Test
    void reinstatesActivatedLockedAndPendingAccountsWithoutRestoringInternshipOrMembership() throws Exception {
        long adminId = accounts.requireActiveAdminId(ADMIN_EMAIL);
        var a = createAndActivateIntern(A_EMAIL, "STU-REINSTATE-A", A_PASSWORD, adminId);
        var leader = createAndActivateIntern("project-leader@example.test", "STU-REINSTATE-LEADER",
                "leader's retained password", adminId);
        var projectOwner = createAndActivateMentor("project-owner@example.test", "Project Owner",
                "project owner's password", adminId);
        internships.activateInternship(a.userId(), adminId);
        internships.activateInternship(leader.userId(), adminId);
        long projectId = projects.create(projectOwner.userId(), new ProjectCreateCommand(
                "Reinstatement history", "Retains the closed membership", LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 12, 31), leader.userId()));
        projects.addMember(projectOwner.userId(), projectId, a.userId());
        var beforeWithdrawal = projectQueries.members(projectOwner.userId(), projectId).stream()
                .filter(member -> member.internUserId() == a.userId())
                .findFirst().orElseThrow();
        assertThat(beforeWithdrawal.leftAt()).isNull();

        // Close the Project interval through its owning feature service before the withdrawal guard runs.
        projects.directRemoveMember(projectOwner.userId(), projectId, beforeWithdrawal.membershipId(), null);
        internships.withdrawInternship(a.userId(), adminId);
        assertThat(internships.administrationView(a.userId(), adminId).internshipStatus())
                .isEqualTo(InternshipStatus.WITHDRAWN);
        String aPasswordHash = users.findById(a.userId()).orElseThrow().getPasswordHash();

        var b = createAndActivateMentor(B_EMAIL, "Locked Mentor", B_PASSWORD, adminId);
        accounts.lockAccount(b.userId(), adminId);
        Instant lockedAt = users.findById(b.userId()).orElseThrow().getLockedAt();
        mockMvc.perform(post("/admin/accounts/{id}/deactivate", b.userId())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .user(ADMIN_EMAIL).roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        var c = internships.create(new CreateAccountCommand(C_EMAIL, "Pending Account C", GlobalRole.MENTOR,
                null, null, null), adminId);
        mockMvc.perform(post("/admin/accounts/{id}/deactivate", c.userId())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .user(ADMIN_EMAIL).roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertLoginRefusedWithoutSession(A_EMAIL, A_PASSWORD);
        assertLoginRefusedWithoutSession(B_EMAIL, B_PASSWORD);
        assertLoginRefusedWithoutSession(C_EMAIL, "any password for pending C");

        reinstate(c.userId());
        reinstate(b.userId());
        reinstate(a.userId());

        assertThat(users.findById(a.userId()).orElseThrow().getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(users.findById(a.userId()).orElseThrow().getPasswordHash()).isEqualTo(aPasswordHash);
        assertThat(internships.administrationView(a.userId(), adminId).internshipStatus())
                .isEqualTo(InternshipStatus.WITHDRAWN);
        var membershipAfterReinstate = projectQueries.members(projectOwner.userId(), projectId).stream()
                .filter(member -> member.internUserId() == a.userId())
                .findFirst().orElseThrow();
        assertThat(membershipAfterReinstate.leftAt()).isNotNull();
        assertLoginSucceeds(A_EMAIL, A_PASSWORD);

        assertThat(users.findById(b.userId()).orElseThrow().getAccountStatus()).isEqualTo(AccountStatus.LOCKED);
        assertThat(users.findById(b.userId()).orElseThrow().getLockedAt()).isEqualTo(lockedAt);
        assertLoginRefusedWithoutSession(B_EMAIL, B_PASSWORD);
        accounts.unlockAccount(b.userId(), adminId);
        assertLoginSucceeds(B_EMAIL, B_PASSWORD);

        assertThat(users.findById(c.userId()).orElseThrow().getAccountStatus())
                .isEqualTo(AccountStatus.PENDING_ACTIVATION);
        assertLoginRefusedWithoutSession(C_EMAIL, "any password for pending C");
        accounts.resendActivation(c.userId(), adminId);
        assertThat(accounts.activate(mail.activationTokenFor(C_EMAIL), "fresh C activation password")).isTrue();
        assertLoginSucceeds(C_EMAIL, "fresh C activation password");
    }

    private void reinstate(long userId) throws Exception {
        mockMvc.perform(post("/admin/accounts/{id}/reinstate", userId)
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors
                                .user(ADMIN_EMAIL).roles("ADMIN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/accounts/" + userId));
    }

    private void assertLoginRefusedWithoutSession(String email, String password) throws Exception {
        var result = mockMvc.perform(post("/login").with(csrf())
                        .param("username", email).param("password", password))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated())
                .andReturn();
        HttpSession session = result.getRequest().getSession(false);
        assertThat(session == null || session.getAttribute(
                HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY) == null)
                .as("refused login must not persist an authenticated security context for %s", email)
                .isTrue();
    }

    private void assertLoginSucceeds(String email, String password) throws Exception {
        var result = mockMvc.perform(post("/login").with(csrf())
                        .param("username", email).param("password", password))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated().withUsername(email))
                .andReturn();
        assertThat(result.getRequest().getSession(false)).isInstanceOf(MockHttpSession.class);
    }

    private com.lab.labtimesheet.feature.identity.model.dto.AccountCreation createAndActivateIntern(
            String email, String studentCode, String password, long adminId) {
        var creation = internships.create(new CreateAccountCommand(email, studentCode, GlobalRole.INTERN, studentCode,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 12, 31)), adminId);
        assertThat(accounts.activate(mail.activationTokenFor(email), password)).isTrue();
        return creation;
    }

    private com.lab.labtimesheet.feature.identity.model.dto.AccountCreation createAndActivateMentor(
            String email, String name, String password, long adminId) {
        var creation = internships.create(new CreateAccountCommand(email, name, GlobalRole.MENTOR,
                null, null, null), adminId);
        assertThat(accounts.activate(mail.activationTokenFor(email), password)).isTrue();
        return creation;
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MailConfiguration {
        @Bean @Primary RecordingProbe recordingProbe() { return new RecordingProbe(); }
    }

    static final class RecordingProbe implements SmtpProbe {
        private final Map<String, String> activationTokens = new HashMap<>();

        @Override
        public void send(SmtpConnection connection, String recipient, String subject, String body) {
            int marker = body.indexOf("token=");
            if (marker >= 0 && subject.startsWith("Activate")) {
                activationTokens.put(recipient, body.substring(marker + "token=".length()).trim());
            }
        }

        String activationTokenFor(String email) {
            return activationTokens.get(email);
        }
    }
}
