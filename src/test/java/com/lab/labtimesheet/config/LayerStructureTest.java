package com.lab.labtimesheet.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.lab.labtimesheet.LabtimesheetApplication;
import org.junit.jupiter.api.Test;

class LayerStructureTest {
    private static final Path BASE_PACKAGE = Path.of("src/main/java/com/lab/labtimesheet");
    private static final Set<String> APPROVED_ROOT_PACKAGES = Set.of("config", "feature");
    private static final Set<String> APPROVED_FEATURES = Set.of(
            "account", "integration", "project", "task", "attendance", "notification", "reporting");
    private static final Set<String> APPROVED_FEATURE_PACKAGES = Set.of(
            "controller", "exception", "model", "model/dto", "model/entity", "repository", "service");
    private static final Pattern INTERNAL_IMPORT = Pattern.compile(
            "import com\\.lab\\.labtimesheet\\.feature\\.([^.]+)\\.(?:repository|model\\.entity)\\.");

    @Test
    void applicationUsesOnlyApprovedPackageByFeatureStructure() throws IOException {
        assertThat(LabtimesheetApplication.class.getPackageName()).isEqualTo("com.lab.labtimesheet");

        try (var entries = Files.list(BASE_PACKAGE)) {
            Set<String> directories = entries
                    .filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toSet());

            assertThat(directories).containsExactlyInAnyOrderElementsOf(APPROVED_ROOT_PACKAGES);
        }

        Path featurePackage = BASE_PACKAGE.resolve("feature");
        try (var entries = Files.list(featurePackage)) {
            Set<String> features = entries
                    .filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .collect(Collectors.toSet());

            assertThat(features).isNotEmpty().isSubsetOf(APPROVED_FEATURES);
        }

        try (var entries = Files.walk(featurePackage)) {
            List<String> featurePackages = entries
                    .filter(Files::isDirectory)
                    .filter(path -> path.getNameCount() > featurePackage.getNameCount() + 1)
                    .map(path -> path.subpath(featurePackage.getNameCount() + 1, path.getNameCount()))
                    .map(path -> path.toString().replace(path.getFileSystem().getSeparator(), "/"))
                    .toList();

            assertThat(featurePackages).allMatch(APPROVED_FEATURE_PACKAGES::contains);
        }

        try (var entries = Files.walk(featurePackage)) {
            List<String> crossFeaturePersistenceImports = entries
                    .filter(path -> path.toString().endsWith(".java"))
                    .flatMap(path -> persistenceImportsFromAnotherFeature(featurePackage, path).stream())
                    .toList();

            assertThat(crossFeaturePersistenceImports).isEmpty();
        }
    }

    private static List<String> persistenceImportsFromAnotherFeature(Path featurePackage, Path source) {
        String owningFeature = featurePackage.relativize(source).getName(0).toString();
        try {
            return Files.readAllLines(source).stream()
                    .filter(line -> {
                        var matcher = INTERNAL_IMPORT.matcher(line);
                        return matcher.find() && !matcher.group(1).equals(owningFeature);
                    })
                    .map(line -> source + ": " + line.trim())
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot inspect " + source, exception);
        }
    }
}
