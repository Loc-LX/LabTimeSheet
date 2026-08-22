package com.lab.labtimesheet.config;

import java.time.Clock;
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
     * Provides an immutable clock for local E2E runs so date-sensitive journeys are repeatable.
     *
     * @param fixedInstant ISO-8601 instant configured only when the {@code e2e} profile is explicitly active
     * @return fixed clock in the application's Vietnam business zone
     */
    @Bean
    @Profile("e2e & !prod")
    Clock e2eClock(@Value("${lab.e2e.fixed-instant}") String fixedInstant) {
        return Clock.fixed(Instant.parse(fixedInstant), BUSINESS_ZONE);
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
