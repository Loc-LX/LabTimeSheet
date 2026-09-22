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
    private static final Set<String> APPROVED_ROOT_PACKAGES = Set.of("config", "feature", "platform");
    private static final Set<String> APPROVED_FEATURES = Set.of(
            "identity", "integration", "project", "task", "attendance", "notification", "reporting");
    private static final Set<String> APPROVED_FEATURE_PACKAGES = Set.of(
            "controller", "exception", "model", "model/dto", "model/entity", "repository", "service");
    private static final Pattern INTERNAL_IMPORT = Pattern.compile(
            "import com\\.lab\\.labtimesheet\\.(?:feature\\.([^.]+)|platform)\\.(?:repository|model\\.entity)\\.");

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

        for (String moduleDirectory : List.of("feature", "platform")) {
            Path modulePackage = BASE_PACKAGE.resolve(moduleDirectory);
            try (var entries = Files.walk(modulePackage)) {
                List<String> crossModulePersistenceImports = entries
                        .filter(path -> path.toString().endsWith(".java"))
                        .flatMap(path -> persistenceImportsFromAnotherModule(modulePackage, moduleDirectory, path).stream())
                        .toList();

                assertThat(crossModulePersistenceImports).isEmpty();
            }
        }
    }

    private static List<String> persistenceImportsFromAnotherModule(Path modulePackage, String moduleDirectory,
            Path source) {
        String owningModule = moduleDirectory.equals("platform")
                ? "platform"
                : modulePackage.relativize(source).getName(0).toString();
        try {
            return Files.readAllLines(source).stream()
                    .filter(line -> {
                        var matcher = INTERNAL_IMPORT.matcher(line);
                        if (!matcher.find()) {
                            return false;
                        }
                        String importedModule = matcher.group(1) == null ? "platform" : matcher.group(1);
                        return !owningModule.equals(importedModule);
                    })
                    .map(line -> source + ": " + line.trim())
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot inspect " + source, exception);
        }
    }
}
