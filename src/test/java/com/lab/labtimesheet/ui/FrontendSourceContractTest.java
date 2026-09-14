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

/**
 * Guards the hand-written Tailwind source and the screen templates against losing the
 * design-token set and the table overflow affordances.
 *
 * <p>Protects {@code UI-001}, {@code UI-002}, {@code UI-005} and {@code UI-015}.
 */
class FrontendSourceContractTest {

    private static final Path FRONTEND = Path.of("src/main/frontend/app.css");
    private static final Path TEMPLATES = Path.of("src/main/resources/templates");

    /**
     * The standalone A4 print template. It carries its own {@code <style>} block, does not
     * use the screen shell, and is rendered to paper and PDF, where a horizontal scroll
     * region cannot exist. {@code UI-015} bounds its obligation to viewport width, so this
     * template is outside the scope of the scroll-region contract.
     */
    private static final String PRINT_ONLY_TEMPLATE = "reports/print.html";

    /**
     * Protects {@code UI-001} and {@code UI-005}. Observable break: a theme token is dropped
     * from the Tailwind source, so every surface that reads it falls back to a browser default
     * and the light and dark palettes stop agreeing with each other.
     */
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

    /**
     * Protects {@code UI-002} and {@code UI-015}. Observable break: the containment declarations
     * are dropped, so an over-wide panel or table pushes the whole page sideways and the fixed
     * sidebar scrolls out of reach.
     *
     * <p>This method asserted {@code min-width: 64rem} on {@code html} until 12 September 2026.
     * That declaration was deliberately replaced with {@code min-width: 0} when the narrow-screen
     * blocks were added, because a 64rem floor makes {@code @media (max-width: 64rem)} dead code.
     * {@code src/test/js/narrow-screen-contract.test.mjs} requires that block to exist, so the two
     * assertions demanded incompatible stylesheets and this one contradicted {@code UI-015}, which
     * asks a narrower viewport to wrap or scroll rather than be blocked. Recorded as {@code D10} in
     * {@code .sdd/decisions.md}. Containment is asserted through the two declarations
     * that still carry it.
     */
    @Test
    void desktopLayoutContainsPageLevelOverflowContainment() throws IOException {
        String source = Files.readString(FRONTEND, StandardCharsets.UTF_8);

        assertThat(source).contains("overflow-x: hidden");
        assertThat(source).contains("max-width: 100%");
    }

    /**
     * Protects {@code UI-015}. Observable break: the scroll region loses its own horizontal
     * overflow, so a wide table resolves its width against the page instead of its container.
     */
    @Test
    void tableContentScrollsInsideItsRegionWithoutCreatingPageLevelOverflow() throws IOException {
        String source = Files.readString(FRONTEND, StandardCharsets.UTF_8);

        assertThat(source).contains(".table-scroll");
        assertThat(source).contains("overflow-x: auto;");
    }

    /**
     * Protects {@code UI-015}. Observable break: a new screen template ships a table outside a
     * scroll region, so that one page overflows horizontally while every other page does not.
     *
     * @param relativeTemplate template path below {@code src/main/resources/templates}
     */
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
                    .filter(relativeTemplate -> !PRINT_ONLY_TEMPLATE.equals(relativeTemplate))
                    .toList()
                    .stream();
        }
    }
}
