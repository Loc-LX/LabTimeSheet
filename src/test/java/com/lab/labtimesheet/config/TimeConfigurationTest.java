package com.lab.labtimesheet.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.context.support.TestPropertySourceUtils;

class TimeConfigurationTest {
    @Test
    void utcInstantAtVietnamMidnightUsesTheNewLocalBusinessDate() {
        Clock applicationClock = new TimeConfiguration().applicationClock();
        Instant vietnamMidnight = Instant.parse("2026-08-14T17:00:00Z");

        LocalDate businessDate = LocalDate.now(Clock.fixed(vietnamMidnight, applicationClock.getZone()));

        assertThat(businessDate).isEqualTo(LocalDate.of(2026, 8, 15));
    }

    @Test
    void e2eProfileUsesTheConfiguredFixedInstant() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("e2e");
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                    context, "lab.e2e.fixed-instant=2026-08-22T02:00:00Z");
            context.register(TimeConfiguration.class);
            context.refresh();

            Clock clock = context.getBean(Clock.class);
            assertThat(clock.instant()).isEqualTo(Instant.parse("2026-08-22T02:00:00Z"));
        }
    }

    @Test
    void e2eProfileIsRejectedAlongsideProduction() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().setActiveProfiles("prod", "e2e");
            TestPropertySourceUtils.addInlinedPropertiesToEnvironment(
                    context, "lab.e2e.fixed-instant=2026-08-22T02:00:00Z");
            context.register(TimeConfiguration.class);

            assertThat(org.assertj.core.api.Assertions.catchThrowable(context::refresh))
                    .hasRootCauseMessage("e2e fixed clock cannot be enabled with prod");
        }
    }
}
