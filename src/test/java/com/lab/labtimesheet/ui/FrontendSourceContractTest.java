package com.lab.labtimesheet.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

class FrontendSourceContractTest {

    private static final Path FRONTEND = Path.of("src/main/frontend/app.css");
    private static final Path TEMPLATES = Path.of("src/main/resources/templates");

    @Test
    void tailwindSourceIsValidAndDeclaresTheDesignTokenSet() throws IOException {
        String source = Files.readString(FRONTEND, StandardCharsets.UTF_8);

        assertThat(source).startsWith("@import \"tailwindcss\"");
        assertThat(source).contains("@theme {");
        for (String token : new String[] {
            "ink", "canvas", "sidebar", "panel", "panel-muted", "border", "border-strong",
            "muted", "accent", "success", "warning", "danger"
        }) {
            assertThat(source).contains("--color-" + token + ":");
        }
    }

    @Test
    void desktopLayoutContainsPageLevelOverflowContainment() throws IOException {
        String source = Files.readString(FRONTEND, StandardCharsets.UTF_8);

        assertThat(source).contains("min-width: 64rem");
        assertThat(source).contains("overflow-x: hidden");
        assertThat(source).contains("max-width: 100%");
    }

    @Test
    void tableContentScrollsInsideItsRegionWithoutCreatingPageLevelOverflow() throws IOException {
        String source = Files.readString(FRONTEND, StandardCharsets.UTF_8);

        assertThat(source).contains(".table-scroll");
        assertThat(source).contains("overflow-x: auto;");
    }

    @ParameterizedTest
    @MethodSource("templatesWithTables")
    void everyTemplateTableIsWrappedInAScrollRegion(String relativeTemplate) throws IOException {
        String template = Files.readString(TEMPLATES.resolve(relativeTemplate), StandardCharsets.UTF_8);

        assertThat(template).contains("table-scroll");
    }

    private static Stream<String> templatesWithTables() throws IOException {
        try (Stream<Path> paths = Files.walk(TEMPLATES)) {
            return paths
                    .filter(path -> path.toString().endsWith(".html"))
                    .filter(path -> {
                        try {
                            return Files.readString(path, StandardCharsets.UTF_8).contains("<table");
                        } catch (IOException exception) {
                            throw new RuntimeException(exception);
                        }
                    })
                    .map(path -> TEMPLATES.relativize(path).toString().replace('\\', '/'))
                    .toList()
                    .stream();
        }
    }
}
