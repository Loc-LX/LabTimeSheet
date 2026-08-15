package com.lab.labtimesheet.feature.reporting.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class ProjectTaskShellContractTest {

    private static final Path TEMPLATES = Path.of("src/main/resources/templates");

    @ParameterizedTest
    @MethodSource("projectAndTaskTemplates")
    void projectAndTaskPageUsesSharedDesktopShell(String relativeTemplate) throws IOException {
        String template = Files.readString(TEMPLATES.resolve(relativeTemplate));

        assertThat(template)
                .contains("fragments/layout :: shell(")
                .contains("activeNav='projects'")
                .doesNotContain("<head>");
    }

    @ParameterizedTest
    @MethodSource("projectAndTaskForms")
    void projectAndTaskFormProvidesAnErrorSummary(String relativeTemplate) throws IOException {
        String template = Files.readString(TEMPLATES.resolve(relativeTemplate));

        assertThat(template)
                .contains("#fields.hasAnyErrors()")
                .contains("#fields.allErrors()");
    }

    private static Stream<String> projectAndTaskTemplates() {
        return Stream.of(
                "projects/list.html",
                "projects/form.html",
                "projects/detail.html",
                "projects/members.html",
                "projects/leadership.html",
                "tasks/list.html",
                "tasks/form.html",
                "tasks/detail.html");
    }

    private static Stream<String> projectAndTaskForms() {
        return Stream.of("projects/form.html", "tasks/form.html");
    }
}
