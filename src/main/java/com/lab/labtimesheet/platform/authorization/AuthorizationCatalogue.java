package com.lab.labtimesheet.platform.authorization;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Reads and validates the immutable §5.2 permission catalogue once during application startup. */
@Component
public final class AuthorizationCatalogue {

    private static final String RESOURCE = "platform/authorization-matrix.tsv";
    private static final List<String> HEADER = List.of(
            "Key", "Capability", "Admin", "Owning Mentor", "Current Leader", "Active member / assignee");

    private final Map<AuthorizationCapability, Map<AuthorizationColumn, Cell>> cells;
    private final Map<AuthorizationCapability, String> labels;
    private final Map<String, AuthorizationCapability> capabilitiesByLabel;
    private final List<String> rowsInMatrixOrder;

    /** Loads the classpath catalogue and fails startup if any capability or actor column is invalid. */
    public AuthorizationCatalogue() {
        this(loadResource());
    }

    /** @return a catalogue loaded from the application classpath resource */
    public static AuthorizationCatalogue loadDefault() {
        return new AuthorizationCatalogue();
    }

    AuthorizationCatalogue(List<List<String>> rows) {
        if (rows.isEmpty() || !HEADER.equals(rows.get(0))) {
            throw new IllegalStateException("Authorization catalogue has an invalid header");
        }
        Map<AuthorizationCapability, Map<AuthorizationColumn, Cell>> parsed = new LinkedHashMap<>();
        EnumMap<AuthorizationCapability, String> parsedLabels = new EnumMap<>(AuthorizationCapability.class);
        Map<String, AuthorizationCapability> parsedByLabel = new LinkedHashMap<>();
        List<String> orderedRows = new ArrayList<>();
        for (int rowIndex = 1; rowIndex < rows.size(); rowIndex++) {
            List<String> row = rows.get(rowIndex);
            if (row.size() != HEADER.size()) {
                throw new IllegalStateException("Authorization catalogue row has the wrong number of cells: "
                        + rowIndex);
            }
            AuthorizationCapability capability;
            try {
                capability = AuthorizationCapability.valueOf(row.get(0));
            } catch (IllegalArgumentException exception) {
                throw new IllegalStateException("Authorization catalogue contains an unknown capability key: "
                        + row.get(0), exception);
            }
            String label = row.get(1);
            if (label.isBlank()) {
                throw new IllegalStateException("Authorization catalogue label is blank: " + capability);
            }
            EnumMap<AuthorizationColumn, Cell> actorCells = new EnumMap<>(AuthorizationColumn.class);
            AuthorizationColumn[] columns = AuthorizationColumn.values();
            for (int index = 0; index < columns.length; index++) {
                actorCells.put(columns[index], parseCell(row.get(index + 2), label, columns[index]));
            }
            boolean transitionCapability = actorCells.values().stream()
                    .anyMatch(cell -> !cell.transitions().isEmpty());
            boolean grantedCellWithoutEdges = actorCells.values().stream()
                    .anyMatch(cell -> cell.granted() && cell.transitions().isEmpty());
            if (transitionCapability && grantedCellWithoutEdges) {
                throw new IllegalStateException("Authorization catalogue transition capability must define edges "
                        + "on all granted cells: " + capability);
            }
            if (parsed.putIfAbsent(capability, Map.copyOf(actorCells)) != null) {
                throw new IllegalStateException("Authorization catalogue repeats capability: " + capability);
            }
            if (parsedByLabel.putIfAbsent(label, capability) != null) {
                throw new IllegalStateException("Authorization catalogue repeats label: " + label);
            }
            parsedLabels.put(capability, label);
            orderedRows.add(String.join("\t", row.subList(1, row.size())));
        }
        Set<AuthorizationCapability> missing = java.util.Arrays.stream(AuthorizationCapability.values())
                .filter(capability -> !parsed.containsKey(capability))
                .collect(Collectors.toUnmodifiableSet());
        if (!missing.isEmpty()) {
            throw new IllegalStateException("Authorization catalogue is missing capabilities: "
                    + missing.stream().map(AuthorizationCapability::name).sorted().toList());
        }
        cells = Map.copyOf(parsed);
        labels = Map.copyOf(parsedLabels);
        capabilitiesByLabel = Map.copyOf(parsedByLabel);
        rowsInMatrixOrder = List.copyOf(orderedRows);
    }

    /** Returns the parsed grant and state predicates for one capability and actor column. */
    Cell cell(AuthorizationCapability capability, AuthorizationColumn column) {
        Map<AuthorizationColumn, Cell> actorCells = cells.get(capability);
        if (actorCells == null) {
            throw new IllegalArgumentException("Unknown authorization capability: " + capability);
        }
        return actorCells.get(Objects.requireNonNull(column, "column"));
    }

    /** @return the exact §5.2 label of one capability, read from the catalogue resource */
    public String label(AuthorizationCapability capability) {
        String label = labels.get(capability);
        if (label == null) {
            throw new IllegalArgumentException("Unknown authorization capability: " + capability);
        }
        return label;
    }

    /** Resolves an exact §5.2 label, rejecting a capability that is not in the catalogue. */
    public AuthorizationCapability capability(String label) {
        AuthorizationCapability capability = capabilitiesByLabel.get(label);
        if (capability == null) {
            throw new IllegalArgumentException("Unknown authorization capability: " + label);
        }
        return capability;
    }

    /** @return capability labels in resource order */
    public List<String> capabilities() {
        return rowsInMatrixOrder.stream().map(row -> row.substring(0, row.indexOf('\t'))).toList();
    }

    /** @return tab-separated rows in resource order, for exact contract verification */
    public List<String> rowsInMatrixOrder() {
        return rowsInMatrixOrder;
    }

    private static List<List<String>> loadResource() {
        ClassPathResource resource = new ClassPathResource(RESOURCE);
        try (InputStream input = resource.getInputStream();
                BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            return reader.lines()
                    .filter(line -> !line.isBlank() && !line.stripLeading().startsWith("#"))
                    .map(line -> List.of(line.split("\\t", -1)))
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("Cannot load authorization catalogue " + RESOURCE, exception);
        }
    }

    private static Cell parseCell(String raw, String capability, AuthorizationColumn column) {
        String location = capability + " / " + column.matrixHeading();
        if ("No".equals(raw)) {
            return new Cell(false, Set.of(), Set.of(), Set.of());
        }
        if (raw == null || raw.isBlank()) {
            throw invalidCell(location, raw);
        }
        String[] parts = raw.split("; ", -1);
        if (!"Yes".equals(parts[0])) {
            throw invalidCell(location, raw);
        }
        Set<String> scopeStates = new LinkedHashSet<>();
        Set<String> excludedRecordStates = new LinkedHashSet<>();
        Set<Transition> transitions = new LinkedHashSet<>();
        Set<String> seenKeys = new HashSet<>();
        for (int index = 1; index < parts.length; index++) {
            String part = parts[index];
            int separator = part.indexOf('=');
            if (separator <= 0 || separator == part.length() - 1) {
                throw invalidCell(location, raw);
            }
            String key = part.substring(0, separator);
            String value = part.substring(separator + 1);
            if (!seenKeys.add(key)) {
                throw invalidCell(location, raw);
            }
            switch (key) {
                case "scope" -> scopeStates.addAll(parseStates(value, location, raw));
                case "not" -> excludedRecordStates.addAll(parseStates(value, location, raw));
                case "edges" -> transitions.addAll(parseTransitions(value, location, raw));
                default -> throw invalidCell(location, raw);
            }
        }
        if (parts.length > 4) {
            throw invalidCell(location, raw);
        }
        return new Cell(true, Set.copyOf(scopeStates), Set.copyOf(excludedRecordStates), Set.copyOf(transitions));
    }

    private static Set<String> parseStates(String value, String location, String raw) {
        List<String> states = Arrays.asList(value.split(",", -1));
        if (states.isEmpty() || states.stream().anyMatch(state -> !state.matches("[A-Z][A-Z0-9_]*"))
                || new HashSet<>(states).size() != states.size()) {
            throw invalidCell(location, raw);
        }
        return Set.copyOf(states);
    }

    private static Set<Transition> parseTransitions(String value, String location, String raw) {
        Set<Transition> transitions = new LinkedHashSet<>();
        for (String edge : value.split(",", -1)) {
            String[] states = edge.split(">", -1);
            if (states.length != 2 || !states[0].matches("[A-Z][A-Z0-9_]*")
                    || !states[1].matches("[A-Z][A-Z0-9_]*")
                    || !transitions.add(new Transition(states[0], states[1]))) {
                throw invalidCell(location, raw);
            }
        }
        if (transitions.isEmpty()) {
            throw invalidCell(location, raw);
        }
        return Set.copyOf(transitions);
    }

    private static IllegalStateException invalidCell(String location, String raw) {
        return new IllegalStateException("Authorization catalogue cell has invalid syntax at " + location + ": "
                + raw);
    }

    /** One parsed cell; the policy evaluates these generic predicates without knowing business states. */
    record Cell(boolean granted, Set<String> scopeStates, Set<String> excludedRecordStates,
            Set<Transition> transitions) {

        /** Evaluates this cell against resolved actor scope and record-state facts. */
        boolean allows(AuthorizationRequest request) {
            if (!granted) {
                return false;
            }
            if (!scopeStates.isEmpty() && !scopeStates.contains(request.scopeState())) {
                return false;
            }
            if (!excludedRecordStates.isEmpty()
                    && (request.recordState() == null || excludedRecordStates.contains(request.recordState()))) {
                return false;
            }
            if (!transitions.isEmpty()) {
                if (request.recordState() == null || request.targetState() == null
                        || !transitions.contains(new Transition(request.recordState(), request.targetState()))) {
                    return false;
                }
            }
            return true;
        }
    }

    /** A generic directed state transition stored in an actor cell. */
    record Transition(String from, String to) {}
}
