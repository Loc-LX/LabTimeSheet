package com.lab.labtimesheet;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.lab.labtimesheet.config.SecurityProperties;

/**
 * Application entry point and root component-scan boundary for Lab Timesheet.
 *
 * <p>The entry point also canonicalizes the legacy Windows Vietnam timezone alias before database
 * drivers inspect the JVM default timezone.
 */
@SpringBootApplication
@EnableConfigurationProperties(SecurityProperties.class)
@EnableScheduling
public class LabtimesheetApplication {
    private static final String LEGACY_VIETNAM_TIME_ZONE = "Asia/Saigon";
    private static final String BUSINESS_TIME_ZONE = "Asia/Ho_Chi_Minh";

    /**
     * Canonicalizes the process timezone and starts the standalone Spring Boot process.
     *
     * @param args command-line arguments forwarded to Spring Boot
     */
    public static void main(String[] args) {
        normalizeDefaultTimeZone();
        SpringApplication.run(LabtimesheetApplication.class, args);
    }

    /**
     * Replaces the legacy Windows Vietnam alias before pgJDBC sends it to PostgreSQL as a startup
     * parameter. Other supported system timezones remain unchanged.
     */
    static void normalizeDefaultTimeZone() {
        if (LEGACY_VIETNAM_TIME_ZONE.equals(TimeZone.getDefault().getID())) {
            TimeZone.setDefault(TimeZone.getTimeZone(BUSINESS_TIME_ZONE));
        }
    }

}
