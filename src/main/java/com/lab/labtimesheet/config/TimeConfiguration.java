package com.lab.labtimesheet.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Provides the injectable Vietnam-zone clock used for server-authoritative business dates and time. */
@Configuration(proxyBeanMethods = false)
class TimeConfiguration {
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Bean
    Clock applicationClock() {
        return Clock.system(BUSINESS_ZONE);
    }
}
