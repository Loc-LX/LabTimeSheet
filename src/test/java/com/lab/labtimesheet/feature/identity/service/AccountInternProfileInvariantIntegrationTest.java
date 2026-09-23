package com.lab.labtimesheet.feature.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.identity.model.dto.CreateAccountCommand;
import com.lab.labtimesheet.feature.identity.repository.AppUserRepository;
import com.lab.labtimesheet.feature.identity.repository.InternProfileRepository;
import com.lab.labtimesheet.platform.model.GlobalRole;
import com.lab.labtimesheet.platform.model.SecurityMode;
import com.lab.labtimesheet.platform.model.dto.SmtpConnection;
import com.lab.labtimesheet.platform.model.dto.SmtpDraft;
import com.lab.labtimesheet.platform.service.SmtpConfigurationService;
import com.lab.labtimesheet.platform.service.SmtpProbe;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@Import({TestcontainersConfiguration.class, AccountInternProfileInvariantIntegrationTest.MailConfiguration.class})
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AccountInternProfileInvariantIntegrationTest {
    private static final String ADMIN_EMAIL = "admin@example.com";
    private static final String ADMIN_PASSWORD = "correct horse battery staple";
    private static final LocalDate INTERNSHIP_START = LocalDate.of(2026, 8, 1);
    private static final LocalDate INTERNSHIP_END = LocalDate.of(2026, 12, 31);

    @Autowired
    private BootstrapService bootstrap;

    @Autowired
    private AccountService accounts;

    @Autowired
    private SmtpConfigurationService smtp;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private InternProfileRepository internProfiles;

    @Autowired
    private JdbcTemplate jdbc;

    private long adminId;

    @BeforeEach
    void enableSmtp() {
        bootstrap.bootstrap(ADMIN_EMAIL, "Primary Admin", ADMIN_PASSWORD);
        adminId = accounts.requireActiveAdminId(ADMIN_EMAIL);
        long draftId = smtp.saveDraft(adminId, new SmtpDraft(
                "mailpit", 1025, SecurityMode.NONE, null, null, ADMIN_EMAIL, "Lab Timesheet"));
        smtp.testDraft(draftId, adminId, ADMIN_EMAIL);
        smtp.activate(draftId, adminId);
    }

    /**
     * Protects {@code ACC-019}. Observable break: account creation can commit an {@code INTERN}
     * without its shared-key profile, attach a profile to another role, or retain the account row
     * after profile persistence fails. The hand-derived result is one profile for the successful
     * Intern, none for the Admin and Mentor, and unchanged account/profile counts after the
     * duplicate Student Code makes the second Intern creation roll back.
     */
    @Test
    void accountCreationPreservesInternProfileInvariantAcrossMidTransactionFailure() {
        accounts.create(new CreateAccountCommand(
                "mentor@example.com", "Mentor", GlobalRole.MENTOR, null, null, null), adminId);
        accounts.create(new CreateAccountCommand(
                "intern@example.com", "Intern", GlobalRole.INTERN, "STU-001",
                INTERNSHIP_START, INTERNSHIP_END), adminId);

        assertInternProfileInvariant();
        long accountCount = users.count();
        long profileCount = internProfiles.count();

        assertThatThrownBy(() -> accounts.create(new CreateAccountCommand(
                "rollback-intern@example.com", "Rollback Intern", GlobalRole.INTERN, " stu-001 ",
                INTERNSHIP_START, INTERNSHIP_END), adminId))
                .isInstanceOf(DataIntegrityViolationException.class);

        assertThat(users.findByNormalizedEmail("rollback-intern@example.com")).isEmpty();
        assertThat(users.count()).isEqualTo(accountCount);
        assertThat(internProfiles.count()).isEqualTo(profileCount);
        assertInternProfileInvariant();
    }

    private void assertInternProfileInvariant() {
        Integer violations = jdbc.queryForObject("""
                select count(*)
                from app_users u
                left join intern_profiles p on p.user_id = u.id
                where (u.global_role = 'INTERN' and p.user_id is null)
                   or (u.global_role <> 'INTERN' and p.user_id is not null)
                """, Integer.class);
        assertThat(violations).isZero();
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class MailConfiguration {
        @Bean
        @Primary
        SmtpProbe acceptingSmtpProbe() {
            return new SmtpProbe() {
                @Override
                public void send(SmtpConnection connection, String recipient, String subject, String body) {
                    // Intentionally succeeds: this test isolates account/profile transaction boundaries.
                }
            };
        }
    }
}
