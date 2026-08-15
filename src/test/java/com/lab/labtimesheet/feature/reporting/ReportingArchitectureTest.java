package com.lab.labtimesheet.feature.reporting;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ReportingArchitectureTest {

    @Test
    void reportingUsesFeaturePackageWithoutGlobalLayersOrPlaceholderBoundary() throws Exception {
        Class<?> view = Class.forName("com.lab.labtimesheet.feature.reporting.model.dto.DashboardView");
        Class<?> controller = Class.forName("com.lab.labtimesheet.feature.reporting.controller.DashboardController");
        Class<?> service = Class.forName("com.lab.labtimesheet.feature.reporting.service.DashboardService");
        Class<?> exception = Class.forName("com.lab.labtimesheet.feature.reporting.exception.DashboardAccessDeniedException");
        assertTrue(view.isSealed());
        assertFalse(view.getPackageName().startsWith("com.lab.labtimesheet.model"));
        assertFalse(usesJdbcTemplate(controller, service, exception));

        assertMissing("com.lab.labtimesheet.controller.DashboardController");
        assertMissing("com.lab.labtimesheet.dto.DashboardView");
        assertMissing("com.lab.labtimesheet.exception.DashboardAccessDeniedException");
        assertMissing("com.lab.labtimesheet.model.DashboardAccount");
        assertMissing("com.lab.labtimesheet.repository.DashboardRepository");
        assertMissing("com.lab.labtimesheet.service.DashboardService");
        assertMissing("com.lab.labtimesheet.reporting.ModuleBoundary");
        assertMissing("com.lab.labtimesheet.feature.reporting.ModuleBoundary");
        assertAll(
                () -> assertMissing("com.lab.labtimesheet.feature.reporting.model.DashboardAccount"),
                () -> assertMissing("com.lab.labtimesheet.feature.reporting.repository.DashboardRepository"));
    }

    private boolean usesJdbcTemplate(Class<?>... types) {
        return Arrays.stream(types)
                .flatMap(type -> Arrays.stream(type.getDeclaredFields()))
                .anyMatch(field -> field.getType().equals(JdbcTemplate.class));
    }

    private void assertMissing(String className) {
        assertThrows(ClassNotFoundException.class, () -> Class.forName(className));
    }
}
