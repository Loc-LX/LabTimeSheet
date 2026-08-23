package com.lab.labtimesheet.feature.attendance.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registers the default {@link AttendanceNotificationClient} fallback. {@code @ConditionalOnMissingBean}
 * is evaluated on the factory method so the bean reliably disappears once the platform
 * feature supplies the tested notification service.
 */
@Configuration
public class AttendanceNotificationClientConfiguration {

    @Bean
    @ConditionalOnMissingBean(AttendanceNotificationClient.class)
    AttendanceNotificationClient unconfiguredAttendanceNotificationClient() {
        return new UnconfiguredAttendanceNotificationClient();
    }
}