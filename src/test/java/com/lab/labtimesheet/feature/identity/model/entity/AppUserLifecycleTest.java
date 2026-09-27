package com.lab.labtimesheet.feature.identity.model.entity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import com.lab.labtimesheet.feature.identity.model.AccountStatus;
import com.lab.labtimesheet.platform.model.GlobalRole;
import org.junit.jupiter.api.Test;


class AppUserLifecycleTest {
    private static final Instant NOW = Instant.parse("2026-09-26T12:00:00Z");

    /** Protects ACC-016: deactivation of a LOCKED account preserves the timestamp used by reinstatement. */
    @Test
    void deactivatingLockedAccountKeepsItsLockTimestamp() {
        AppUser user = AppUser.pending("locked@example.test", "Locked", GlobalRole.INTERN, null, NOW);
        user.activate("{noop}encoded", NOW);
        user.lock(NOW.plusSeconds(1));
        Instant lockedAt = user.getLockedAt();

        user.deactivate(NOW.plusSeconds(2));

        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.DEACTIVATED);
        assertThat(user.getLockedAt()).isEqualTo(lockedAt);
    }

    /** Protects ACC-029 and DB-022: reinstatement derives state from retained activation and lock timestamps. */
    @Test
    void reinstatementRestoresOnlyTheStateStoredHistoryAllows() {
        AppUser active = AppUser.pending("active@example.test", "Active", GlobalRole.INTERN, null, NOW);
        active.activate("{noop}encoded", NOW);
        active.deactivate(NOW.plusSeconds(1));

        AppUser locked = AppUser.pending("locked@example.test", "Locked", GlobalRole.INTERN, null, NOW);
        locked.activate("{noop}encoded", NOW);
        locked.lock(NOW.plusSeconds(1));
        Instant lockTime = locked.getLockedAt();
        locked.deactivate(NOW.plusSeconds(2));

        AppUser pending = AppUser.pending("pending@example.test", "Pending", GlobalRole.INTERN, null, NOW);
        pending.deactivate(NOW.plusSeconds(1));

        active.reinstate(NOW.plusSeconds(3));
        locked.reinstate(NOW.plusSeconds(3));
        pending.reinstate(NOW.plusSeconds(2));

        assertThat(active.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(locked.getAccountStatus()).isEqualTo(AccountStatus.LOCKED);
        assertThat(locked.getLockedAt()).isEqualTo(lockTime);
        assertThat(pending.getAccountStatus()).isEqualTo(AccountStatus.PENDING_ACTIVATION);
    }
}
