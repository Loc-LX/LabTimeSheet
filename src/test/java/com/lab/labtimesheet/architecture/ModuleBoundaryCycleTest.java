package com.lab.labtimesheet.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Guards the module graph and measured violations at the start of step 6.
 *
 * <p>Protects {@code ARC-005}, {@code ARC-006} and {@code AC-ARC-001}. The allowance
 * list is historical: later tasks may delete entries, but may not add a new exception.
 */
class ModuleBoundaryCycleTest {

    private static final Path ROOT = Path.of("src/main/java/com/lab/labtimesheet");
    private static final Path BOUNDARIES = Path.of("src/test/resources/architecture/module-boundaries.tsv");
    private static final Path ADR = Path.of(".sdd/rfcs/ADR-006-module-boundaries.md");
    private static final Set<String> MODULES = Set.of(
            "platform", "identity", "calendar", "notification", "internship", "attendance", "project", "reporting");
    private static final Map<String, Integer> LAYERS = Map.of(
            "platform", 0, "identity", 1, "calendar", 2, "notification", 2,
            "internship", 3, "attendance", 4, "project", 4, "reporting", 5);
    private static final Pattern PACKAGE = Pattern.compile("^\\s*package\\s+([\\w.]+)\\s*;", Pattern.MULTILINE);
    private static final Pattern IMPORT = Pattern.compile("^\\s*import\\s+(com\\.lab\\.labtimesheet\\.[\\w.]+)\\s*;", Pattern.MULTILINE);
    private static final Pattern TYPE = Pattern.compile("\\b[A-Z][A-Za-z0-9_]*\\b");
    private static final Pattern STRING = Pattern.compile("\"([^\"\\\\]*(?:\\\\.[^\"\\\\]*)*)\"");
    private static final Pattern INTERFACE = Pattern.compile("\\binterface\\s+(\\w+)");
    private static final Pattern IMPLEMENTS = Pattern.compile("\\b(?:implements|extends)\\s+([\\w, ]+)");

    @Test
    void moduleGraphHasNoUnrecordedCycleOrBoundaryReference() throws IOException {
        Graph graph = scan();
        assertThat(placementViolations(graph.placementProblems)).isEmpty();
        assertThat(configViolations(graph.configReferences)).isEmpty();
        assertThat(unallowedReferences(graph.references, graph.allowances)).isEmpty();
        assertThat(graph.allowances).containsExactlyInAnyOrderElementsOf(graph.references);
        assertThat(cycles(graph.edges)).as("ARC-005 requires an acyclic graph").isEmpty();
        assertThat(interfaceViolations(graph.interfaces, graph.adrText)).isEmpty();
    }

    @Test
    void rejectsAnArtificialCycle() {
        assertThat(cycles(Map.of("identity", Set.of("project"), "project", Set.of("identity"))))
                .as("artificial cycle must fail the pure cycle check").isNotEmpty();
    }

    @Test
    void rejectsAReferenceIntoConfig() {
        assertThat(configViolations(Set.of(new Reference("BusinessService", "SecurityConfiguration"))))
                .as("reference into config must fail the pure boundary check").isNotEmpty();
    }

    @Test
    void rejectsANewReferenceBetweenAlreadyConnectedModules() {
        Reference newReference = new Reference("NewAttendanceService", "ProjectQueryService");
        assertThat(unallowedReferences(Set.of(newReference), Set.of()))
                .as("a new reference must not be hidden by an existing module relation").containsExactly(newReference);
    }

    @Test
    void rejectsAnInterfaceThatBreaksAnArc006Condition() {
        assertThat(interfaceViolations(List.of(new InterfaceContract(
            "UnlistedPort", "identity", Set.of("project"), false, false)), "ADR-006"))
                .as("an interface missing ADR-006 conditions must fail the pure contract check")
                .isNotEmpty();
    }

    private static List<String> placementViolations(Set<String> problems) {
        return List.copyOf(problems);
    }

    private static Set<String> entityNames(List<Source> sources) {
        return sources.stream().filter(source -> source.text.contains("@Entity"))
                .map(Source::simpleName).collect(java.util.stream.Collectors.toSet());
    }

    private static String removeStrings(String source) {
        return STRING.matcher(source).replaceAll(" ");
    }

    private static List<Reference> configViolations(Set<Reference> references) {
        return List.copyOf(references);
    }

    private static List<Reference> unallowedReferences(Set<Reference> references, Set<Reference> allowances) {
        return references.stream().filter(reference -> !allowances.contains(reference)).toList();
    }

    private static List<String> interfaceViolations(List<InterfaceContract> contracts, String adrText) {
        List<String> violations = new ArrayList<>();
        for (InterfaceContract contract : contracts) {
            if (!adrText.contains(contract.name)) violations.add(contract.name + ": not listed in ADR-006");
            if (contract.implementationModules.stream().anyMatch(contract.declaringModule::equals)) {
                violations.add(contract.name + ": implementation is in declaring module");
            }
            if (!contract.called) violations.add(contract.name + ": not called by declaring module");
            if (!contract.independentDependency) violations.add(contract.name + ": no independent dependency");
        }
        return violations;
    }

    private static Graph scan() throws IOException {
        List<Source> sources = new ArrayList<>();
        Map<String, List<Source>> bySimpleName = new HashMap<>();
        try (Stream<Path> paths = Files.walk(ROOT)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).toList()) {
                String text = stripComments(Files.readString(path, StandardCharsets.UTF_8));
                Matcher packageMatcher = PACKAGE.matcher(text);
                if (!packageMatcher.find()) continue;
                Source source = new Source(path, packageMatcher.group(1), text);
                sources.add(source);
                bySimpleName.computeIfAbsent(source.simpleName(), ignored -> new ArrayList<>()).add(source);
            }
        }
        PlacementTable placements = PlacementTable.read(BOUNDARIES);
        Set<String> placementProblems = new HashSet<>(placements.problems);
        Map<Source, String> modules = new HashMap<>();
        for (Source source : sources) {
            List<String> matches = placements.matches(source.fullyQualifiedName());
            if (matches.size() > 1) placementProblems.add(source.fullyQualifiedName());
            String module = matches.isEmpty() ? packageModule(source.packageName) : matches.get(0);
            if (module == null) placementProblems.add(source.fullyQualifiedName());
            else modules.put(source, module);
        }
        for (Row row : placements.rows) {
            if (sources.stream().noneMatch(source -> PlacementTable.covers(row, source.fullyQualifiedName())
                    && placements.matches(source.fullyQualifiedName()).contains(row.module))) placementProblems.add(row.pattern);
        }
        Set<Reference> references = new HashSet<>();
        Set<Reference> allowances = placements.allowances;
        Set<Reference> configReferences = new HashSet<>();
        Set<String> ambiguityProblems = new HashSet<>();
        Map<String, Set<String>> edges = new HashMap<>();
        Map<String, InterfaceInfo> interfaces = new HashMap<>();
        for (Source source : sources) {
            Matcher interfaceMatcher = INTERFACE.matcher(source.text);
            while (interfaceMatcher.find()) {
                interfaces.put(interfaceMatcher.group(1), new InterfaceInfo(interfaceMatcher.group(1), source));
            }
        }
        for (Source source : sources) {
            String implementationModule = modules.get(source);
            if (implementationModule == null) continue;
            Matcher implementationMatcher = IMPLEMENTS.matcher(source.text);
            while (implementationMatcher.find()) {
                for (String name : implementationMatcher.group(1).split(",")) {
                    InterfaceInfo info = interfaces.get(name.trim());
                    if (info != null && !modules.get(info.source).equals(implementationModule)) {
                        info.implementations.add(source);
                        info.implementationModules.add(implementationModule);
                    }
                }
            }
        }
        for (Source source : sources) {
            String from = modules.get(source);
            if (from == null || !MODULES.contains(from)) continue;
            Map<String, Source> resolved = resolveNames(source, bySimpleName);
            Set<String> referencedNames = new HashSet<>();
            Matcher typeMatcher = TYPE.matcher(removeStrings(source.text));
            while (typeMatcher.find()) referencedNames.add(typeMatcher.group());
            for (String entityName : entityNames(sources)) {
                for (Matcher stringMatcher = STRING.matcher(source.text); stringMatcher.find();) {
                    String literal = stringMatcher.group(1);
                    if (literal.matches("(?is).*\\b(select|from|join|update|delete)\\b.*") && literal.matches("(?s).*\\b" + Pattern.quote(entityName) + "\\b.*")) {
                        referencedNames.add(entityName);
                    }
                }
            }
            for (String referencedName : referencedNames) {
                Source target = resolved.get(referencedName);
                if (target == null && bySimpleName.getOrDefault(referencedName, List.of()).size() > 1) {
                    ambiguityProblems.add(source.simpleName() + " -> " + referencedName);
                }
                if (target == null || target.fullyQualifiedName().equals(source.fullyQualifiedName())) continue;
                String to = modules.get(target);
                if (to == null || from.equals(to)) continue;
                for (InterfaceInfo info : interfaces.values()) {
                    if (info.implementations.stream().anyMatch(candidate -> from.equals(modules.get(candidate)))
                            && !info.implementations.contains(source)
                            && modules.get(info.source).equals(to) && !info.name.equals(target.simpleName())) {
                        info.implementationDependency = true;
                    }
                    if (info.name.equals(target.simpleName()) && modules.get(info.source).equals(from)) {
                        info.called = true;
                    }
                }
                Reference reference = new Reference(source.simpleName(), target.simpleName());
                if (to.equals("config")) configReferences.add(reference);
                else if (!MODULES.contains(to)) continue;
                else if (LAYERS.get(to) >= LAYERS.get(from)) {
                    references.add(reference);
                    if (!allowances.contains(reference)) edges.computeIfAbsent(from, ignored -> new HashSet<>()).add(to);
                } else edges.computeIfAbsent(from, ignored -> new HashSet<>()).add(to);
            }
            Matcher implementationMatcher = IMPLEMENTS.matcher(source.text);
            while (implementationMatcher.find()) {
                for (String name : implementationMatcher.group(1).split(",")) {
                    InterfaceInfo info = interfaces.get(name.trim());
                    if (info != null && !modules.get(info.source).equals(from)) info.implementations.add(source);
                }
            }
        }
        placementProblems.addAll(ambiguityProblems);
        return new Graph(references, allowances, configReferences, edges, interfaces, placementProblems, modules,
            Files.readString(ADR, StandardCharsets.UTF_8));
    }

    private static String packageModule(String packageName) {
        if (packageName.equals("com.lab.labtimesheet") || packageName.startsWith("com.lab.labtimesheet.config")) {
            return packageName.equals("com.lab.labtimesheet") ? "composition" : "config";
        }
        if (packageName.equals("com.lab.labtimesheet.platform") || packageName.startsWith("com.lab.labtimesheet.platform.")) {
            return "platform";
        }
        String[] parts = packageName.split("\\.");
        if (parts.length < 5 || !parts[3].equals("feature")) return null;
        return switch (parts[4]) {
            case "attendance", "calendar", "identity", "internship", "notification", "project", "reporting" -> parts[4];
            default -> null;
        };
    }

    private static Map<String, Source> resolveNames(Source source, Map<String, List<Source>> bySimpleName) {
        Map<String, Source> resolved = new HashMap<>();
        for (List<Source> candidates : bySimpleName.values()) {
            Source samePackage = candidates.stream().filter(candidate -> candidate.packageName.equals(source.packageName))
                    .findFirst().orElse(null);
            if (samePackage != null) resolved.put(samePackage.simpleName(), samePackage);
            List<Source> otherPackages = candidates.stream()
                    .filter(candidate -> !candidate.fullyQualifiedName().equals(source.fullyQualifiedName())).toList();
            if (otherPackages.size() == 1) resolved.putIfAbsent(otherPackages.get(0).simpleName(), otherPackages.get(0));
        }
        Matcher matcher = IMPORT.matcher(source.text);
        while (matcher.find()) {
            String fqn = matcher.group(1);
            bySimpleName.getOrDefault(simpleName(fqn), List.of()).stream()
                    .filter(candidate -> candidate.fullyQualifiedName().equals(fqn)).findFirst()
                    .ifPresent(candidate -> resolved.put(candidate.simpleName(), candidate));
        }
        return resolved;
    }

    private static List<String> interfaceViolations(Map<String, InterfaceInfo> interfaces, String adrText) {
        List<InterfaceContract> contracts = new ArrayList<>();
        for (InterfaceInfo info : interfaces.values()) {
            if (info.implementations.isEmpty()) continue;
            String declaringModule = info.declaringModule;
            contracts.add(new InterfaceContract(info.name, declaringModule,
                    info.implementationModules,
                    info.called, info.implementationDependency));
        }
        return interfaceViolations(contracts, adrText);
    }

    private static Set<Set<String>> cycles(Map<String, Set<String>> edges) {
        Set<Set<String>> found = new HashSet<>();
        for (String start : MODULES) {
            ArrayDeque<List<String>> queue = new ArrayDeque<>();
            queue.add(List.of(start));
            while (!queue.isEmpty()) {
                List<String> path = queue.removeFirst();
                for (String next : edges.getOrDefault(path.get(path.size() - 1), Set.of())) {
                    if (next.equals(start) && path.size() > 1) found.add(new HashSet<>(path));
                    else if (!path.contains(next)) { List<String> extended = new ArrayList<>(path); extended.add(next); queue.add(extended); }
                }
            }
        }
        return found;
    }

    private static String stripComments(String source) {
        return source.replaceAll("/\\*[\\s\\S]*?\\*/", "").replaceAll("(?m)(^|[^:])//.*$", "$1");
    }

    private static String simpleName(String fqn) { return fqn.substring(fqn.lastIndexOf('.') + 1); }

    private record Source(Path path, String packageName, String text) {
        private String simpleName() { return path.getFileName().toString().replace(".java", ""); }
        private String fullyQualifiedName() { return packageName + "." + simpleName(); }
    }

    private record Reference(String source, String target) {}

        private record InterfaceContract(String name, String declaringModule, Set<String> implementationModules,
            boolean called, boolean independentDependency) {}

    private static final class InterfaceInfo {
        private final String name;
        private final Source source;
        private final Set<Source> implementations = new HashSet<>();
        private final Set<String> implementationModules = new HashSet<>();
        private final String declaringModule;
        private boolean called;
        private boolean implementationDependency;

        private InterfaceInfo(String name, Source source) {
            this.name = name;
            this.source = source;
            this.declaringModule = packageModule(source.packageName);
        }
    }

        private record Graph(Set<Reference> references, Set<Reference> allowances, Set<Reference> configReferences, Map<String, Set<String>> edges,
            Map<String, InterfaceInfo> interfaces, Set<String> placementProblems, Map<Source, String> modules,
            String adrText) {}

    private static final class PlacementTable {
        private final List<Row> rows;
        private final Set<String> problems;
        private final Set<Reference> allowances;
        private PlacementTable(List<Row> rows, Set<String> problems, Set<Reference> allowances) {
            this.rows = rows;
            this.problems = problems;
            this.allowances = allowances;
        }
        private static PlacementTable read(Path path) throws IOException {
            List<Row> rows = new ArrayList<>();
            Set<String> problems = new HashSet<>();
            Set<Reference> allowances = new HashSet<>();
            for (String line : Files.readAllLines(path, StandardCharsets.UTF_8)) {
                if (line.isBlank() || line.startsWith("#")) continue;
                line = line.replace("\\t", "\t");
                String[] fields = line.split("\t", -1);
                if (fields.length == 3 && fields[0].equals("P") && MODULES.contains(fields[2])) {
                    rows.add(new Row(fields[1], fields[2]));
                } else if (fields.length == 4 && fields[0].equals("A") && !fields[1].isBlank()
                        && !fields[2].isBlank() && Set.of("R1", "R3", "R4", "R7", "R8", "R10").contains(fields[3])) {
                    allowances.add(new Reference(fields[1], fields[2]));
                } else problems.add(line);
            }
            return new PlacementTable(rows, problems, allowances);
        }
        private static boolean covers(Row row, String fqn) {
            if (row.pattern.endsWith(".*")) {
                return fqn.startsWith(row.pattern.substring(0, row.pattern.length() - 1));
            }
            return row.pattern.contains(".") ? fqn.equals(row.pattern) : simpleName(fqn).equals(row.pattern);
        }

        private List<String> matches(String fqn) {
            List<Row> matching = rows.stream().filter(row -> covers(row, fqn)).toList();
            int specificity = matching.stream().mapToInt(row -> row.pattern.endsWith(".*") ? 0 : 1).max().orElse(-1);
            return matching.stream().filter(row -> (row.pattern.endsWith(".*") ? 0 : 1) == specificity)
                .map(row -> row.module).toList();
        }
    }

    private record Row(String pattern, String module) {}
}