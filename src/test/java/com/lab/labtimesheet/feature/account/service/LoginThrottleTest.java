package com.lab.labtimesheet.feature.account.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

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

    @Test
    void capacityPressureNeverEvictsAnActiveBlock() {
        MutableClock clock = new MutableClock(BASE);
        LoginThrottle throttle = new LoginThrottle(clock);
        for (int attempt = 0; attempt < 5; attempt++) {
            throttle.recordFailure("target@example.com", "203.0.113.10");
        }

        for (int identifier = 0; identifier < 15_120; identifier++) {
            throttle.recordFailure("unknown-" + identifier + "@example.com", "203.0.113.10");
        }

        assertThat(throttle.isBlocked("target@example.com", "203.0.113.10")).isTrue();
    }

    @Test
    void interleavedArbitraryUsernamesDoNotEraseTargetFailureHistory() {
        MutableClock clock = new MutableClock(BASE);
        LoginThrottle throttle = new LoginThrottle(clock);

        for (int attempt = 0; attempt < 5; attempt++) {
            throttle.recordFailure("target@example.com", "203.0.113.10");
            throttle.recordFailure("unknown-" + attempt + "@example.com", "203.0.113.10");
        }

        assertThat(throttle.isBlocked("target@example.com", "203.0.113.10")).isTrue();
    }

    @Test
    void concurrentCapacityPressureNeverEvictsAnActiveBlock() throws Exception {
        MutableClock clock = new MutableClock(BASE);
        LoginThrottle throttle = new LoginThrottle(clock);
        for (int attempt = 0; attempt < 5; attempt++) {
            throttle.recordFailure("target@example.com", "203.0.113.10");
        }

        int workers = 8;
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(workers);
        try {
            List<java.util.concurrent.Future<?>> futures = new ArrayList<>();
            for (int worker = 0; worker < workers; worker++) {
                int workerId = worker;
                futures.add(executor.submit(() -> {
                    start.await();
                    for (int identifier = 0; identifier < 2_000; identifier++) {
                        throttle.recordFailure(
                                "concurrent-" + workerId + "-" + identifier + "@example.com",
                                "198.51.100." + (workerId + 1));
                    }
                    return null;
                }));
            }
            start.countDown();
            for (java.util.concurrent.Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        assertThat(throttle.isBlocked("target@example.com", "203.0.113.10")).isTrue();
    }

    @Test
    void saturatedRecentNonBlockedEntriesStillTrackAndBlockANewVictim() {
        MutableClock clock = new MutableClock(BASE);
        LoginThrottle throttle = new LoginThrottle(clock);
        for (int attempt = 0; attempt < 5; attempt++) {
            throttle.recordFailure("protected@example.com", "203.0.113.10");
        }
        for (int identifier = 0; identifier < 9_999; identifier++) {
            throttle.recordFailure("filler-" + identifier + "@example.com", "203.0.113.10");
        }

        for (int attempt = 0; attempt < 5; attempt++) {
            throttle.recordFailure("victim@example.com", "203.0.113.10");
        }

        assertThat(throttle.isBlocked("victim@example.com", "203.0.113.10")).isTrue();
        assertThat(throttle.isBlocked("protected@example.com", "203.0.113.10")).isTrue();
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
