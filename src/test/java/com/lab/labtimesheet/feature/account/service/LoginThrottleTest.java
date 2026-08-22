package com.lab.labtimesheet.feature.account.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

/** Unit proof for normalized email/source-IP login throttling boundaries. */
class LoginThrottleTest {
    private static final Instant BASE = Instant.parse("2026-08-22T00:00:00Z");

    @Test
    void fiveFailuresWithinFifteenMinutesThrottleTheSixthAttempt() {
        MutableClock clock = new MutableClock(BASE);
        LoginThrottle throttle = new LoginThrottle(clock);

        for (int attempt = 0; attempt < 5; attempt++) {
            throttle.recordFailure(" ADMIN@EXAMPLE.COM ", "203.0.113.10");
        }

        assertThat(throttle.isBlocked("admin@example.com", "203.0.113.10")).isTrue();
        assertThat(throttle.isBlocked("admin@example.com", "203.0.113.11")).isFalse();
        clock.advanceSeconds(899);
        assertThat(throttle.isBlocked("ADMIN@EXAMPLE.COM", "203.0.113.10")).isTrue();
        clock.advanceSeconds(1);
        assertThat(throttle.isBlocked("admin@example.com", "203.0.113.10")).isFalse();
    }

    @Test
    void successClearsApplicableEmailAndSourceIpState() {
        MutableClock clock = new MutableClock(BASE);
        LoginThrottle throttle = new LoginThrottle(clock);
        for (int attempt = 0; attempt < 5; attempt++) {
            throttle.recordFailure("admin@example.com", "203.0.113.10");
        }

        throttle.clear(" ADMIN@EXAMPLE.COM ", "203.0.113.10");

        assertThat(throttle.isBlocked("admin@example.com", "203.0.113.10")).isFalse();
        for (int attempt = 0; attempt < 4; attempt++) {
            throttle.recordFailure("admin@example.com", "203.0.113.10");
        }
        assertThat(throttle.isBlocked("admin@example.com", "203.0.113.10")).isFalse();
    }

    @Test
    void failuresOlderThanTheRollingWindowDoNotCount() {
        MutableClock clock = new MutableClock(BASE);
        LoginThrottle throttle = new LoginThrottle(clock);
        for (int attempt = 0; attempt < 4; attempt++) {
            throttle.recordFailure("admin@example.com", "203.0.113.10");
        }
        clock.advanceSeconds(900);
        throttle.recordFailure("admin@example.com", "203.0.113.10");

        assertThat(throttle.isBlocked("admin@example.com", "203.0.113.10")).isFalse();
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }

        private void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }
    }
}
