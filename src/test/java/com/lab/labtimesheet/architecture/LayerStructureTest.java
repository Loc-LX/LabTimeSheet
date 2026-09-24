package com.lab.labtimesheet.architecture;

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
    private static final Path PRODUCTION_SOURCES = Path.of("src/main/java");
    private static final Path TEST_SOURCES = Path.of("src/test/java");
    private static final String BASE_PACKAGE_NAME = "com.lab.labtimesheet";
    private static final String FEATURE_PACKAGE_NAME = BASE_PACKAGE_NAME + ".feature.";
    private static final Set<String> APPROVED_ROOT_PACKAGES = Set.of("config", "feature", "platform");
    private static final Set<String> APPROVED_FEATURES = Set.of(
            "identity", "internship", "project", "attendance", "calendar", "notification", "reporting");
    private static final Set<String> APPROVED_FEATURE_PACKAGES = Set.of(
            "controller", "exception", "model", "model/dto", "model/entity", "repository", "service");
    private static final Set<String> TEST_PACKAGE_EXCEPTIONS = Set.of(
            BASE_PACKAGE_NAME + ".architecture", BASE_PACKAGE_NAME + ".ui");
    private static final Pattern PACKAGE_DECLARATION = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
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
        Set<String> features = sourcePackages(featurePackage).stream()
                .map(source -> featureName(source.packageName()))
                .collect(Collectors.toSet());
        assertThat(features).containsExactlyInAnyOrderElementsOf(APPROVED_FEATURES);

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

    /**
     * Protects {@code ARC-005} and {@code AC-ARC-001}. Observable break: a test package no longer
     * mirrors a production package, so tests drift into a module that does not own the code they
     * exercise and the build does not expose the misplaced package.
     */
    @Test
    void testPackagesMirrorProductionExceptArchitectureAndUi() throws IOException {
        Set<String> productionPackages = sourcePackages(PRODUCTION_SOURCES).stream()
                .map(SourcePackage::packageName)
                .collect(Collectors.toSet());
        List<String> unmirroredTestSources = sourcePackages(TEST_SOURCES).stream()
                .filter(source -> !TEST_PACKAGE_EXCEPTIONS.contains(source.packageName()))
                .filter(source -> !productionPackages.contains(source.packageName()))
                .map(source -> TEST_SOURCES.relativize(source.path())
                        .toString()
                        .replace(source.path().getFileSystem().getSeparator(), "/")
                        + " -> " + source.packageName())
                .sorted()
                .toList();

        assertThat(unmirroredTestSources).isEmpty();
    }

    private static List<SourcePackage> sourcePackages(Path sourceRoot) throws IOException {
        try (var sources = Files.walk(sourceRoot)) {
            return sources
                    .filter(path -> path.toString().endsWith(".java"))
                    .map(LayerStructureTest::sourcePackage)
                    .toList();
        }
    }

    private static SourcePackage sourcePackage(Path source) {
        try {
            var matcher = PACKAGE_DECLARATION.matcher(Files.readString(source));
            if (!matcher.find()) {
                throw new IllegalArgumentException("Missing package declaration in " + source);
            }
            return new SourcePackage(source, matcher.group(1));
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot inspect " + source, exception);
        }
    }

    private static String featureName(String packageName) {
        if (!packageName.startsWith(FEATURE_PACKAGE_NAME)) {
            throw new IllegalArgumentException("Not a feature package: " + packageName);
        }
        return packageName.substring(FEATURE_PACKAGE_NAME.length()).split("\\.")[0];
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

    private record SourcePackage(Path path, String packageName) {}
}
