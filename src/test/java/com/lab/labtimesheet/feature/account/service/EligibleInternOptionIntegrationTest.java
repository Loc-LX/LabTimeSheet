package com.lab.labtimesheet.feature.account.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.account.model.GlobalRole;
import com.lab.labtimesheet.feature.account.model.dto.EligibleInternOption;
import com.lab.labtimesheet.feature.account.model.entity.AppUser;
import com.lab.labtimesheet.feature.account.model.entity.InternProfile;
import com.lab.labtimesheet.feature.account.repository.AppUserRepository;
import com.lab.labtimesheet.feature.account.repository.InternProfileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class EligibleInternOptionIntegrationTest {
    private static final Instant NOW = Instant.parse("2026-08-14T00:00:00Z");
    private static final LocalDate BUSINESS_DATE = LocalDate.of(2026, 8, 14);

    @Autowired
    private AccountService accounts;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private InternProfileRepository internProfiles;

    @Autowired
    private JdbcTemplate jdbc;

    private AppUser admin;
    private int userSequence;

    @BeforeEach
    void setUp() {
        admin = users.saveAndFlush(AppUser.bootstrapAdmin(
                "picker-admin@example.com", "Picker Admin", "encoded-password", NOW));
    }

    @Test
    void listsOnlyActiveInternsWithActiveInclusiveInternshipsInPickerOrder() {
        long lowerBoundary = activeIntern("Alpha", "STU-100", BUSINESS_DATE, BUSINESS_DATE.plusDays(10));
        long upperBoundary = activeIntern("Alpha", "STU-200", BUSINESS_DATE.minusDays(10), BUSINESS_DATE);
        long laterName = activeIntern("Zeta", "STU-300", BUSINESS_DATE.minusDays(1), BUSINESS_DATE.plusDays(1));

        pendingInternWithActiveProfile("Ignored Pending", "STU-400");
        long locked = activeIntern("Ignored Locked", "STU-500", BUSINESS_DATE.minusDays(1), BUSINESS_DATE.plusDays(1));
        lock(locked);
        long deactivated = activeIntern(
                "Ignored Deactivated", "STU-600", BUSINESS_DATE.minusDays(1), BUSINESS_DATE.plusDays(1));
        deactivate(deactivated);
        activeMentorWithActiveProfile("Ignored Mentor", "STU-700");
        activeInternWithNotStartedProfile("Ignored Not Started", "STU-800");
        activeIntern("Ignored Ended", "STU-900", BUSINESS_DATE.minusDays(10), BUSINESS_DATE.minusDays(1));
        long completed = activeIntern(
                "Ignored Completed", "STU-1000", BUSINESS_DATE.minusDays(1), BUSINESS_DATE.plusDays(1));
        completeInternship(completed);

        List<EligibleInternOption> options = accounts.eligibleInternOptions(BUSINESS_DATE);

        assertThat(options).containsExactly(
                new EligibleInternOption(
                        lowerBoundary, "Alpha", "STU-100", BUSINESS_DATE, BUSINESS_DATE.plusDays(10)),
                new EligibleInternOption(
                        upperBoundary, "Alpha", "STU-200", BUSINESS_DATE.minusDays(10), BUSINESS_DATE),
                new EligibleInternOption(
                        laterName, "Zeta", "STU-300", BUSINESS_DATE.minusDays(1), BUSINESS_DATE.plusDays(1)));
        assertThat(options).extracting(EligibleInternOption::userId).doesNotHaveDuplicates();
    }

    private long activeIntern(String displayName, String studentCode, LocalDate startDate, LocalDate endDate) {
        long userId = activeUser(GlobalRole.INTERN, displayName);
        activeProfile(userId, studentCode, startDate, endDate);
        return userId;
    }

    private void pendingInternWithActiveProfile(String displayName, String studentCode) {
        long userId = pendingUser(GlobalRole.INTERN, displayName);
        activeProfile(userId, studentCode, BUSINESS_DATE.minusDays(1), BUSINESS_DATE.plusDays(1));
    }

    private void activeMentorWithActiveProfile(String displayName, String studentCode) {
        long userId = activeUser(GlobalRole.MENTOR, displayName);
        activeProfile(userId, studentCode, BUSINESS_DATE.minusDays(1), BUSINESS_DATE.plusDays(1));
    }

    private void activeInternWithNotStartedProfile(String displayName, String studentCode) {
        long userId = activeUser(GlobalRole.INTERN, displayName);
        internProfiles.saveAndFlush(InternProfile.notStarted(
                userId, studentCode, BUSINESS_DATE.minusDays(1), BUSINESS_DATE.plusDays(1), NOW));
    }

    private long activeUser(GlobalRole role, String displayName) {
        AppUser user = AppUser.pending(nextEmail(), displayName, role, admin, NOW);
        user.activate("encoded-password", NOW);
        return users.saveAndFlush(user).getId();
    }

    private long pendingUser(GlobalRole role, String displayName) {
        return users.saveAndFlush(AppUser.pending(nextEmail(), displayName, role, admin, NOW)).getId();
    }

    private void activeProfile(long userId, String studentCode, LocalDate startDate, LocalDate endDate) {
        InternProfile profile = InternProfile.notStarted(userId, studentCode, startDate, endDate, NOW);
        profile.activate(NOW);
        internProfiles.saveAndFlush(profile);
    }

    private void lock(long userId) {
        assertThat(jdbc.update(
                """
                update app_users
                set account_status = 'LOCKED', locked_at = ?, updated_at = ?
                where id = ?
                """,
                Timestamp.from(NOW), Timestamp.from(NOW), userId)).isOne();
    }

    private void deactivate(long userId) {
        assertThat(jdbc.update(
                """
                update app_users
                set account_status = 'DEACTIVATED', deactivated_at = ?, updated_at = ?
                where id = ?
                """,
                Timestamp.from(NOW), Timestamp.from(NOW), userId)).isOne();
    }

    private void completeInternship(long userId) {
        assertThat(jdbc.update(
                """
                update intern_profiles
                set internship_status = 'COMPLETED', completed_at = ?, updated_at = ?
                where user_id = ?
                """,
                Timestamp.from(NOW), Timestamp.from(NOW), userId)).isOne();
    }

    private String nextEmail() {
        return "picker-" + ++userSequence + "@example.com";
    }
}
