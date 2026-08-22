package com.lab.labtimesheet.feature.account.service;

import java.time.Clock;

import com.lab.labtimesheet.feature.account.repository.UserActionTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Removes expired and terminal hashed activation/reset tokens without exposing token material. */
@Service
@RequiredArgsConstructor
public class UserActionTokenCleanupService {
    private final UserActionTokenRepository tokens;
    private final Clock clock;

    /**
     * Deletes expired, consumed, and invalidated token rows in one bounded database operation.
     *
     * @return number of rows removed
     */
    @Transactional
    public int cleanupExpiredAndTerminal() {
        return tokens.deleteExpiredAndTerminal(clock.instant());
    }

    /** Runs cleanup hourly; token rows are not an operational history surface. */
    @Scheduled(fixedDelay = 3_600_000L, initialDelay = 3_600_000L)
    public void scheduledCleanup() {
        cleanupExpiredAndTerminal();
    }
}
