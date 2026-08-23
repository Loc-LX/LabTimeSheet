package com.lab.labtimesheet.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables scheduled deadline workers for the attendance feature outside the test profile. With the test profile
 * active the {@code @Scheduled} triggers stay inert so integration tests remain deterministic and invoke the
 * worker methods directly against committed rows.
 */
@Configuration(proxyBeanMethods = false)
@Profile("!test")
@EnableScheduling
class SchedulingConfiguration {
}