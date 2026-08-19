package com.lab.labtimesheet.feature.account.model.entity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lab.labtimesheet.feature.account.model.InternshipStatus;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

class InternProfileTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-20T00:00:00Z");

    @Test
    void activeInternshipCanCompleteAndRecordsTheTerminalTimestamp() {
        var profile = profile();
        profile.activate(CREATED_AT.plusSeconds(60));

        profile.complete(CREATED_AT.plusSeconds(120));

        assertThat(profile.getInternshipStatus()).isEqualTo(InternshipStatus.COMPLETED);
        assertThat(ReflectionTestUtils.getField(profile, "completedAt"))
                .isEqualTo(CREATED_AT.plusSeconds(120));
        assertThat(ReflectionTestUtils.getField(profile, "updatedAt"))
                .isEqualTo(CREATED_AT.plusSeconds(120));
    }

    @Test
    void notStartedOrActiveInternshipCanWithdrawAndRecordsTheTerminalTimestamp() {
        var notStarted = profile();
        notStarted.withdraw(CREATED_AT.plusSeconds(30));

        var active = profile();
        active.activate(CREATED_AT.plusSeconds(60));
        active.withdraw(CREATED_AT.plusSeconds(90));

        assertThat(notStarted.getInternshipStatus()).isEqualTo(InternshipStatus.WITHDRAWN);
        assertThat(active.getInternshipStatus()).isEqualTo(InternshipStatus.WITHDRAWN);
        assertThat(ReflectionTestUtils.getField(notStarted, "withdrawnAt"))
                .isEqualTo(CREATED_AT.plusSeconds(30));
        assertThat(ReflectionTestUtils.getField(active, "withdrawnAt"))
                .isEqualTo(CREATED_AT.plusSeconds(90));
    }

    @Test
    void terminalStatesAndPrematureCompletionCannotTransition() {
        var notStarted = profile();
        assertThatThrownBy(() -> notStarted.complete(CREATED_AT.plusSeconds(1)))
                .isInstanceOf(IllegalStateException.class);

        var completed = profile();
        completed.activate(CREATED_AT.plusSeconds(1));
        completed.complete(CREATED_AT.plusSeconds(2));
        assertThatThrownBy(() -> completed.withdraw(CREATED_AT.plusSeconds(3)))
                .isInstanceOf(IllegalStateException.class);

        var withdrawn = profile();
        withdrawn.withdraw(CREATED_AT.plusSeconds(1));
        assertThatThrownBy(() -> withdrawn.activate(CREATED_AT.plusSeconds(2)))
                .isInstanceOf(IllegalStateException.class);
    }

    private static InternProfile profile() {
        return InternProfile.notStarted(
                42L,
                "STU-42",
                LocalDate.of(2026, 8, 20),
                LocalDate.of(2026, 12, 31),
                CREATED_AT);
    }
}
