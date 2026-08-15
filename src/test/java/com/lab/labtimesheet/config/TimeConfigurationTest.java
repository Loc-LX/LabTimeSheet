package com.lab.labtimesheet.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class TimeConfigurationTest {
    @Test
    void utcInstantAtVietnamMidnightUsesTheNewLocalBusinessDate() {
        Clock applicationClock = new TimeConfiguration().applicationClock();
        Instant vietnamMidnight = Instant.parse("2026-08-14T17:00:00Z");

        LocalDate businessDate = LocalDate.now(Clock.fixed(vietnamMidnight, applicationClock.getZone()));

        assertThat(businessDate).isEqualTo(LocalDate.of(2026, 8, 15));
    }
}
