package com.lab.labtimesheet.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Provides the injectable Vietnam-zone clock used for server-authoritative business dates and time. */
@Configuration(proxyBeanMethods = false)
class TimeConfiguration {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    /** Provides the live server clock for every profile other than the deterministic E2E profile. */
    @Bean
    @Profile("!e2e")
    Clock applicationClock() {
        return Clock.system(BUSINESS_ZONE);
    }

    /**
     * Provides an advancing clock anchored at a configured instant for local E2E runs. The offset preserves
     * deterministic startup dates while allowing elapsed-time validation, such as checkout after check-in.
     *
     * @param startInstant ISO-8601 instant configured only when the {@code e2e} profile is explicitly active
     * @return advancing clock whose first instant is near the configured start in the application's business zone
     */
    @Bean
    @Profile("e2e & !prod")
    Clock e2eClock(@Value("${lab.e2e.start-instant}") String startInstant) {
        Instant configuredStart = Instant.parse(startInstant);
        Instant systemStart = Instant.now();
        return Clock.offset(Clock.system(BUSINESS_ZONE), Duration.between(systemStart, configuredStart));
    }

    /**
     * Rejects accidental activation of the deterministic clock in production.
     *
     * @throws IllegalStateException when both {@code prod} and {@code e2e} are active
     */
    @Bean
    @Profile("prod & e2e")
    Clock rejectE2eClockInProduction() {
        throw new IllegalStateException("e2e fixed clock cannot be enabled with prod");
    }
}
