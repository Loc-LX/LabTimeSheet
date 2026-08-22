package com.lab.labtimesheet.feature.account.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Bounded single-instance login-failure throttle keyed by normalized email and source IP.
 *
 * <p>SEC-007 deliberately permits in-memory state for the single application instance. Restart clears this state;
 * persisted manual account locks remain independent. The map is capped so arbitrary login identifiers cannot grow
 * memory without bound.</p>
 */
@Component
@RequiredArgsConstructor
public class LoginThrottle {
    private static final int MAX_ENTRIES = 10_000;
    private static final int MAX_FAILURES = 5;
    private static final Duration FAILURE_WINDOW = Duration.ofMinutes(15);
    private static final Duration BLOCK_DURATION = Duration.ofMinutes(15);

    private final Clock clock;
    private final Map<Key, State> states = new ConcurrentHashMap<>();
    private final Object capacityLock = new Object();

    /**
     * Returns whether the normalized email/source-IP pair is currently throttled.
     *
     * @param email submitted login email
     * @param sourceIp request source address
     * @return {@code true} only during the bounded throttle interval
     */
    public boolean isBlocked(String email, String sourceIp) {
        Key key = key(email, sourceIp);
        synchronized (capacityLock) {
            State state = states.get(key);
            if (state == null) {
                return false;
            }
            Instant now = clock.instant();
            state.expireFailures(now);
            if (state.blockedUntil != null && now.isBefore(state.blockedUntil)) {
                return true;
            }
            if (state.failures.isEmpty()) {
                states.remove(key, state);
            }
            return false;
        }
    }

    /**
     * Records one failed authentication attempt and starts the fifteen-minute block at the fifth failure in the
     * rolling fifteen-minute window.
     *
     * @param email submitted login email
     * @param sourceIp request source address
     */
    public void recordFailure(String email, String sourceIp) {
        Key key = key(email, sourceIp);
        synchronized (capacityLock) {
            Instant now = clock.instant();
            State state = states.get(key);
            if (state == null) {
                purgeUnblockedEntries(now);
                if (states.size() >= MAX_ENTRIES) {
                    return;
                }
                state = new State();
                states.put(key, state);
            }
            state.expireFailures(now);
            if (state.blockedUntil != null && now.isBefore(state.blockedUntil)) {
                return;
            }
            state.failures.addLast(now);
            if (state.failures.size() >= MAX_FAILURES) {
                state.blockedUntil = now.plus(BLOCK_DURATION);
            }
        }
    }

    /**
     * Clears the failure state after a successful authentication.
     *
     * @param email authenticated login email
     * @param sourceIp request source address
     */
    public void clear(String email, String sourceIp) {
        synchronized (capacityLock) {
            states.remove(key(email, sourceIp));
        }
    }

    private void purgeUnblockedEntries(Instant now) {
        states.forEach((key, state) -> {
            state.expireFailures(now);
            if (state.blockedUntil == null) {
                states.remove(key, state);
            }
        });
    }

    private static Key key(String email, String sourceIp) {
        String normalizedEmail = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        String normalizedIp = sourceIp == null || sourceIp.isBlank() ? "unknown" : sourceIp.trim();
        return new Key(normalizedEmail, normalizedIp);
    }

    private record Key(String email, String sourceIp) {
    }

    private static final class State {
        private final Deque<Instant> failures = new ArrayDeque<>();
        private Instant blockedUntil;

        private void expireFailures(Instant now) {
            Instant cutoff = now.minus(FAILURE_WINDOW);
            while (!failures.isEmpty() && !failures.peekFirst().isAfter(cutoff)) {
                failures.removeFirst();
            }
        }
    }
}
