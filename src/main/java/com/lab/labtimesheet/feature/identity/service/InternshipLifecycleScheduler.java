package com.lab.labtimesheet.feature.identity.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

/** Runs the idempotent Account-owned internship start guard independently of request traffic. */
@Component
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class InternshipLifecycleScheduler {
    private final AccountService accounts;

    /** Invokes the same server-date guard used by request-time activation once per minute after startup. */
    @Scheduled(fixedDelay = 60_000L, initialDelay = 60_000L)
    void activateDueInternships() {
        accounts.activateDueInternships();
    }
}
