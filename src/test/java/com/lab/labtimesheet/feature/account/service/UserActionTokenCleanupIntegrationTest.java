package com.lab.labtimesheet.feature.account.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.account.model.TokenPurpose;
import com.lab.labtimesheet.feature.account.model.GlobalRole;
import com.lab.labtimesheet.feature.account.model.entity.AppUser;
import com.lab.labtimesheet.feature.account.model.entity.UserActionToken;
import com.lab.labtimesheet.feature.account.repository.AppUserRepository;
import com.lab.labtimesheet.feature.account.repository.UserActionTokenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

/** PostgreSQL proof for removing expired and terminal hashed account-action tokens without touching live tokens. */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class UserActionTokenCleanupIntegrationTest {
    private static final Instant BASE = Instant.parse("2026-08-14T00:00:00Z");

    @Autowired
    private BootstrapService bootstrap;

    @Autowired
    private AccountService accounts;

    @Autowired
    private UserActionTokenRepository tokens;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private UserActionTokenCleanupService cleanup;

    @Test
    void cleanupDeletesExpiredAndTerminalRowsButRetainsLiveToken() {
        bootstrap.bootstrap("cleanup@example.com", "Cleanup Admin", "correct horse battery staple");
        long userId = accounts.requireActiveAdminId("cleanup@example.com");
        AppUser secondUser = users.saveAndFlush(AppUser.pending(
                "cleanup-second@example.com", "Second", GlobalRole.MENTOR,
                users.findById(userId).orElseThrow(), BASE.minusSeconds(60)));
        UserActionToken expired = tokens.save(UserActionToken.passwordReset(
                secondUser.getId(), hash(1), BASE.minusSeconds(30), BASE.minusSeconds(60)));
        UserActionToken used = tokens.save(UserActionToken.activation(
                userId, hash(2), BASE.plusSeconds(600), userId, BASE.minusSeconds(60)));
        used.markUsed(BASE);
        tokens.saveAndFlush(used);
        UserActionToken live = tokens.save(UserActionToken.passwordReset(
                userId, hash(3), BASE.plusSeconds(600), BASE.minusSeconds(60)));
        tokens.flush();

        assertThat(cleanup.cleanupExpiredAndTerminal()).isEqualTo(2);
        assertThat(tokens.findById(expired.getId())).isEmpty();
        assertThat(tokens.findById(used.getId())).isEmpty();
        assertThat(tokens.findById(live.getId())).isPresent();
    }

    @Test
    void scheduledCleanupRunsTheDeletionInsideItsSchedulerTransaction() {
        bootstrap.bootstrap("scheduled-cleanup@example.com", "Scheduled Cleanup Admin",
                "correct horse battery staple");
        long userId = accounts.requireActiveAdminId("scheduled-cleanup@example.com");
        UserActionToken expired = tokens.saveAndFlush(UserActionToken.passwordReset(
                userId, hash(4), BASE.minusSeconds(30), BASE.minusSeconds(60)));

        cleanup.scheduledCleanup();

        assertThat(tokens.findById(expired.getId())).isEmpty();
    }

    private static byte[] hash(int marker) {
        byte[] hash = new byte[32];
        hash[0] = (byte) marker;
        return hash;
    }
}
