package com.lab.labtimesheet;

import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer;

/** Configures the application when deployed as a traditional servlet-container WAR. */
public class ServletInitializer extends SpringBootServletInitializer {

    /**
     * Registers the same application source used by the standalone launcher.
     *
     * @param application servlet-container application builder
     * @return builder configured with the Lab Timesheet application source
     */
    @Override
    protected SpringApplicationBuilder configure(SpringApplicationBuilder application) {
        return application.sources(LabtimesheetApplication.class);
    }

}
