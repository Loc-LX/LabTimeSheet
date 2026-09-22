package com.lab.labtimesheet;

import org.springframework.boot.context.event.ApplicationStartingEvent;
import org.springframework.context.ApplicationListener;

/**
 * Canonicalizes the legacy Windows Vietnam timezone alias as soon as a Spring Boot test context
 * starts.
 *
 * <p>A test context never passes through {@link LabtimesheetApplication#main(String[])}, so without
 * this listener pgJDBC sends {@code Asia/Saigon} and PostgreSQL refuses the connection. It applies
 * the same canonicalization the application applies, and leaves every other timezone unchanged.
 * Registered in {@code META-INF/spring.factories}; decision {@code D19}.
 */
class LegacyTimeZoneTestListener implements ApplicationListener<ApplicationStartingEvent> {

    /**
     * Replaces the legacy alias before any bean, and therefore any datasource, is created.
     *
     * @param event the first event a starting Spring Boot application publishes
     */
    @Override
    public void onApplicationEvent(ApplicationStartingEvent event) {
        LabtimesheetApplication.normalizeDefaultTimeZone();
    }
}
