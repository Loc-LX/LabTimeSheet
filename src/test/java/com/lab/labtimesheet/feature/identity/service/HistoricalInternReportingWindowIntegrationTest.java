package com.lab.labtimesheet.feature.identity.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.identity.model.dto.InternReportingWindow;
import com.lab.labtimesheet.feature.identity.model.entity.AppUser;
import com.lab.labtimesheet.feature.identity.model.entity.InternProfile;
import com.lab.labtimesheet.feature.identity.repository.AppUserRepository;
import com.lab.labtimesheet.feature.identity.repository.InternProfileRepository;
import com.lab.labtimesheet.platform.model.GlobalRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class HistoricalInternReportingWindowIntegrationTest {
    private static final Instant ACTIVATED_AT = Instant.parse("2026-08-14T00:00:00Z");
    private static final Instant TERMINATED_AT = Instant.parse("2026-08-20T16:00:00Z");
    private static final LocalDate CONFIGURED_START = LocalDate.of(2026, 8, 10);
    private static final LocalDate CONFIGURED_END = LocalDate.of(2026, 8, 31);

    @Autowired
    private AccountService accounts;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private InternProfileRepository profiles;

    @Test
    void completedInternRetainsInclusiveHistoricalWindowThroughTerminalBusinessDate() {
        long userId = activeIntern("completed@example.com");
        InternProfile profile = profiles.findById(userId).orElseThrow();
        profile.complete(TERMINATED_AT);
        profiles.saveAndFlush(profile);

        Optional<InternReportingWindow> window = accounts.historicalInternReportingWindow(userId);

        assertThat(window).isPresent();
        assertThat(window.orElseThrow().activationDate()).isEqualTo(LocalDate.of(2026, 8, 14));
        assertThat(window.orElseThrow().terminalDate()).isEqualTo(LocalDate.of(2026, 8, 20));
        assertThat(window.orElseThrow().eligibleOn(LocalDate.of(2026, 8, 13))).isFalse();
        assertThat(window.orElseThrow().eligibleOn(LocalDate.of(2026, 8, 19))).isTrue();
        assertThat(window.orElseThrow().eligibleOn(LocalDate.of(2026, 8, 20))).isTrue();
        assertThat(window.orElseThrow().eligibleOn(LocalDate.of(2026, 8, 21))).isFalse();
    }

    @Test
    void withdrawnInternRetainsItsHistoricalWindowAfterAccountDeactivation() {
        long userId = activeIntern("withdrawn@example.com");
        InternProfile profile = profiles.findById(userId).orElseThrow();
        profile.withdraw(TERMINATED_AT);
        profiles.saveAndFlush(profile);
        AppUser user = users.findById(userId).orElseThrow();
        user.deactivate(TERMINATED_AT);
        users.saveAndFlush(user);

        Optional<InternReportingWindow> window = accounts.historicalInternReportingWindow(userId);

        assertThat(window).isPresent();
        assertThat(window.orElseThrow().terminalDate()).isEqualTo(LocalDate.of(2026, 8, 20));
        assertThat(window.orElseThrow().eligibleOn(LocalDate.of(2026, 8, 20))).isTrue();
        assertThat(window.orElseThrow().eligibleOn(LocalDate.of(2026, 8, 21))).isFalse();
    }

    @Test
    void nonEligibleLifecycleStatesReturnEmptyWindow() {
        long pendingId = users.saveAndFlush(AppUser.pending(
                "pending@example.com", "Pending", GlobalRole.INTERN, null, ACTIVATED_AT)).getId();
        long notStartedId = notStartedIntern("not-started@example.com");
        long deactivatedId = activeIntern("deactivated@example.com");
        AppUser deactivated = users.findById(deactivatedId).orElseThrow();
        deactivated.deactivate(TERMINATED_AT);
        users.saveAndFlush(deactivated);

        assertThat(accounts.historicalInternReportingWindow(pendingId)).isEmpty();
        assertThat(accounts.historicalInternReportingWindow(notStartedId)).isEmpty();
        assertThat(accounts.historicalInternReportingWindow(deactivatedId)).isEmpty();
        assertThat(accounts.historicalInternReportingWindow(99_999L)).isEmpty();
    }

    private long activeIntern(String email) {
        AppUser user = users.saveAndFlush(AppUser.pending(email, "Intern", GlobalRole.INTERN, null, ACTIVATED_AT));
        InternProfile profile = notStartedProfile(user, email);
        profiles.saveAndFlush(profile);
        user.activate("encoded-password", ACTIVATED_AT);
        users.saveAndFlush(user);
        profile.activate(ACTIVATED_AT);
        profiles.saveAndFlush(profile);
        return user.getId();
    }

    private long notStartedIntern(String email) {
        AppUser user = users.saveAndFlush(AppUser.pending(email, "Intern", GlobalRole.INTERN, null, ACTIVATED_AT));
        profiles.saveAndFlush(notStartedProfile(user, email));
        user.activate("encoded-password", ACTIVATED_AT);
        users.saveAndFlush(user);
        return user.getId();
    }

    private static InternProfile notStartedProfile(AppUser user, String email) {
        return InternProfile.notStarted(
                user.getId(), "STU-" + email.substring(0, email.indexOf('@')), CONFIGURED_START, CONFIGURED_END,
                ACTIVATED_AT);
    }
}
