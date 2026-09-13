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

    /**
     * Protects {@code UI-002} and {@code UI-008}. Observable break: a Project section page stops
     * opening with the shared tab strip, so one section loses the navigation the others carry and
     * a reader cannot move between them.
     *
     * <p>This assertion required the literal text {@code "<main>\n    <th:block th:replace="} until
     * 13 September 2026, which also required {@code <main>} to carry no attributes.
     * {@code projects/detail.html} and {@code projects/workflows.html} open with
     * {@code <main class="project-overview">} and {@code <main class="project-workflow-page">}, and
     * both classes are styled in {@code src/main/frontend/app.css}, so removing them to satisfy the
     * assertion would break the layout the rule asks for. Five of this project's templates carry a
     * classed {@code <main>}. The assertion now states the intent the method is named after, that
     * the tab strip is the first thing inside {@code <main>}, and it is stricter than the text it
     * replaced because the replaced fragment must be {@code projectTabs} rather than any fragment.
     * Same finding as {@code D10} in {@code .sdd/reviews/open-decisions.md}, second instance.
     *
     * @param relativeTemplate template path below {@code src/main/resources/templates}
     */
    @ParameterizedTest
    @MethodSource("projectSectionTemplates")
    void projectSectionsUseTheSharedNavigationDirectlyBelowThePageHeading(String relativeTemplate)
            throws IOException {
        String template = Files.readString(TEMPLATES.resolve(relativeTemplate))
                .replace("\r\n", "\n");

        assertThat(template)
                .containsPattern(
                        "<main[^>]*>\\n\\s*<th:block th:replace=\"~\\{fragments/components :: projectTabs\\(");
    }

    private static Stream<String> projectAndTaskTemplates() {
        return Stream.of(
                "projects/list.html",
                "projects/form.html",
                "projects/detail.html",
                "projects/members.html",
                "projects/leadership.html",
                "projects/workflows.html",
                "tasks/list.html",
                "tasks/form.html",
                "tasks/detail.html");
    }

    private static Stream<String> projectAndTaskForms() {
        return Stream.of("projects/form.html", "tasks/form.html");
    }

    private static Stream<String> projectSectionTemplates() {
        return Stream.of(
                "projects/detail.html",
                "projects/members.html",
                "projects/leadership.html",
                "projects/workflows.html",
                "tasks/list.html");
    }
}
