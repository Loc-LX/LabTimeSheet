package com.lab.labtimesheet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.lab.labtimesheet.config.SecurityProperties;

/** Application entry point and root component-scan boundary for Lab Timesheet. */
@SpringBootApplication
@EnableConfigurationProperties(SecurityProperties.class)
public class LabtimesheetApplication {

    /**
     * Starts the standalone Spring Boot process.
     *
     * @param args command-line arguments forwarded to Spring Boot
     */
    public static void main(String[] args) {
        SpringApplication.run(LabtimesheetApplication.class, args);
    }

}
