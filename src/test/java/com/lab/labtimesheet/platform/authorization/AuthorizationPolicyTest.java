package com.lab.labtimesheet.platform.authorization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Protects {@code AUTH-012}, {@code AC-AUTH-011}, and {@code TSK-023}. Observable break: a role
 * column can leak into another column's rights, the catalogue can drift from §5.2, or a Mentor can
 * perform an ungranted Task transition. Expected cell values are read directly from §5.2.
 */
class AuthorizationPolicyTest {

    private final AuthorizationCatalogue catalogue = AuthorizationCatalogue.loadDefault();
    private final AuthorizationPolicy policy = new AuthorizationPolicy(catalogue);

    @Test
    void catalogueMatchesEveryCapabilityAndCellInTheSpec() throws Exception {
        List<String> document = Files.readAllLines(Path.of(
                ".sdd/specs/platform/features/authorization/SPEC.md"));
        int matrixStart = document.indexOf("#### §5.2 Permission matrix");
        int matrixEnd = indexOfAfter(document, "| ID | Requirement |", matrixStart + 1);
        List<List<String>> matrixRows = document.subList(matrixStart + 1, matrixEnd).stream()
                .filter(line -> line.startsWith("| ") && line.split("\\|", -1).length == 7)
                .filter(line -> !line.contains("---") && !line.contains("| Capability |"))
                .map(AuthorizationPolicyTest::splitRow)
                .toList();

        assertThat(catalogue.rowsInMatrixOrder())
                .containsExactlyElementsOf(matrixRows.stream().map(this::catalogueRow).toList());
        assertThat(catalogue.capabilities()).hasSize(36);
    }

    private String catalogueRow(List<String> specRow) {
        String capability = specRow.get(0);
        String key = catalogue.capability(capability).name();
        List<String> cells = new java.util.ArrayList<>();
        cells.add(capability);
        for (int index = 0; index < AuthorizationColumn.values().length; index++) {
            cells.add(structuredCell(key, AuthorizationColumn.values()[index], specRow.get(index + 1)));
        }
        return String.join("\t", cells);
    }

    private static String structuredCell(String key, AuthorizationColumn column, String specCell) {
        if ("No".equals(specCell)) {
            return "No";
        }
        List<String> tokens = new java.util.ArrayList<>();
        String lower = specCell.toLowerCase(java.util.Locale.ROOT);
        if (lower.contains("planned` or `active` project")) {
            tokens.add("scope=PLANNED,ACTIVE");
        } else if (lower.contains("active` project")) {
            tokens.add("scope=ACTIVE");
        } else if (lower.contains("if active intern")) {
            tokens.add("scope=ACTIVE");
        } else if (key.equals(AuthorizationCapability.DELETE_EMPTY_PLANNED_PROJECT.name())
                && column == AuthorizationColumn.OWNING_MENTOR) {
            tokens.add("scope=PLANNED");
        } else if (key.equals(AuthorizationCapability.CANCEL_PROJECT.name())
                && column == AuthorizationColumn.OWNING_MENTOR) {
            tokens.add("scope=PLANNED,ACTIVE");
        }
        if (lower.contains("unfinished")) {
            tokens.add("not=DONE");
        }
        if (key.equals(AuthorizationCapability.BLOCK_UNBLOCK_REOPEN_TASK.name())
                && (column == AuthorizationColumn.OWNING_MENTOR
                        || column == AuthorizationColumn.CURRENT_LEADER)) {
            tokens.add("edges=TODO>BLOCKED,IN_PROGRESS>BLOCKED,BLOCKED>TODO,BLOCKED>IN_PROGRESS,DONE>IN_PROGRESS");
        }
        rejectUnmappedStateConditions(specCell, lower);
        return tokens.isEmpty() ? "Yes" : "Yes; " + String.join("; ", tokens);
    }

    private static void rejectUnmappedStateConditions(String cell, String lower) {
        java.util.regex.Matcher states = java.util.regex.Pattern
                .compile("\\b(?:PLANNED|ACTIVE|DONE|TODO|IN_PROGRESS|BLOCKED|PENDING|OVERDUE|unfinished)\\b",
                        java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(cell);
        while (states.find()) {
            String state = states.group().toLowerCase(java.util.Locale.ROOT);
            boolean mapped = switch (state) {
                case "unfinished" -> lower.contains("unfinished");
                case "planned", "active" -> lower.contains("planned` or `active` project")
                        || lower.contains("active` project") || lower.contains("if active intern")
                        || lower.contains("active assignee");
                default -> false;
            };
            assertThat(mapped)
                    .as("§5.2 condition '%s' in cell '%s' must have an explicit phrase-to-token mapping",
                            states.group(), cell)
                    .isTrue();
        }
    }

    @Test
    void takesTheUnionOfGrantedColumnsWithoutRankingThem() {
        assertThat(allows(
                        AuthorizationCapability.BLOCK_UNBLOCK_REOPEN_TASK,
                        Set.of(AuthorizationColumn.ADMIN, AuthorizationColumn.OWNING_MENTOR),
                        "ACTIVE", "TODO", "BLOCKED"))
                .isTrue();
    }

    @Test
    void refusesMentorTransitionsOutsideBlockUnblockAndReopen() {
        assertThat(allows(
                        AuthorizationCapability.BLOCK_UNBLOCK_REOPEN_TASK,
                        Set.of(AuthorizationColumn.OWNING_MENTOR),
                        "ACTIVE", "TODO", "IN_PROGRESS"))
                .isFalse();
        assertThat(allows(
                        AuthorizationCapability.BLOCK_UNBLOCK_REOPEN_TASK,
                        Set.of(AuthorizationColumn.OWNING_MENTOR),
                        "ACTIVE", "IN_PROGRESS", "DONE"))
                .isFalse();
    }

    @Test
    void allowsBlockUnblockAndReopenEdgesForOwningMentorOnAnActiveProject() {
        assertThat(allows(AuthorizationCapability.BLOCK_UNBLOCK_REOPEN_TASK,
                Set.of(AuthorizationColumn.OWNING_MENTOR), "ACTIVE", "TODO", "BLOCKED")).isTrue();
        assertThat(allows(AuthorizationCapability.BLOCK_UNBLOCK_REOPEN_TASK,
                Set.of(AuthorizationColumn.OWNING_MENTOR), "ACTIVE", "BLOCKED", "IN_PROGRESS")).isTrue();
        assertThat(allows(AuthorizationCapability.BLOCK_UNBLOCK_REOPEN_TASK,
                Set.of(AuthorizationColumn.OWNING_MENTOR), "ACTIVE", "IN_PROGRESS", "BLOCKED")).isTrue();
        assertThat(allows(AuthorizationCapability.BLOCK_UNBLOCK_REOPEN_TASK,
                Set.of(AuthorizationColumn.OWNING_MENTOR), "ACTIVE", "BLOCKED", "TODO")).isTrue();
        assertThat(allows(AuthorizationCapability.BLOCK_UNBLOCK_REOPEN_TASK,
                Set.of(AuthorizationColumn.OWNING_MENTOR), "ACTIVE", "DONE", "IN_PROGRESS")).isTrue();
    }

    @Test
    void refusesTaskBlockTransitionOutsideAnActiveProject() {
        assertThat(allows(
                        AuthorizationCapability.BLOCK_UNBLOCK_REOPEN_TASK,
                        Set.of(AuthorizationColumn.OWNING_MENTOR),
                        "PLANNED", "TODO", "BLOCKED"))
                .isFalse();
    }

    @Test
    void appliesScopeAndRecordPredicatesFromTheCatalogue() {
        assertThat(allows(AuthorizationCapability.CANCEL_PROJECT,
                Set.of(AuthorizationColumn.OWNING_MENTOR), "PLANNED", null, null)).isTrue();
        assertThat(allows(AuthorizationCapability.CANCEL_PROJECT,
                Set.of(AuthorizationColumn.OWNING_MENTOR), "COMPLETED", null, null)).isFalse();
        assertThat(allows(AuthorizationCapability.EDIT_OR_DELETE_TASK,
                Set.of(AuthorizationColumn.CURRENT_LEADER), null, "IN_PROGRESS", null)).isTrue();
        assertThat(allows(AuthorizationCapability.EDIT_OR_DELETE_TASK,
                Set.of(AuthorizationColumn.CURRENT_LEADER), null, "DONE", null)).isFalse();
        assertThat(allows(AuthorizationCapability.SUBMIT_ATTENDANCE_REQUEST,
                Set.of(AuthorizationColumn.CURRENT_LEADER), "ACTIVE", null, null)).isTrue();
        assertThat(allows(AuthorizationCapability.SUBMIT_ATTENDANCE_REQUEST,
                Set.of(AuthorizationColumn.CURRENT_LEADER), "LOCKED", null, null)).isFalse();
    }

    /**
     * Protects AUTH-012 and AC-AUTH-011. Observable break: an incomplete record could pass a
     * negated-state grant; expected result for §5.2's {@code not=DONE} cell with no record state is
     * denial.
     */
    @Test
    void refusesNegatedRecordStatePredicateWhenRecordStateIsMissing() {
        assertThat(allows(AuthorizationCapability.EDIT_OR_DELETE_TASK,
                Set.of(AuthorizationColumn.CURRENT_LEADER), null, null, null)).isFalse();
    }

    /**
     * Protects AUTH-012 and AC-AUTH-011. Observable break: a role cell in a transition capability
     * could omit its edge restriction; expected startup result for removing Current Leader's
     * {@code edges=} from §5.2's block/unblock/reopen row is catalogue rejection.
     */
    @Test
    void rejectsTransitionCapabilityWhenGrantedColumnOmitsEdges() throws Exception {
        List<List<String>> malformed = resourceRows();
        List<String> transitionRow = malformed.stream()
                .filter(row -> row.get(0).equals("BLOCK_UNBLOCK_REOPEN_TASK"))
                .findFirst().orElseThrow();
        transitionRow.set(4, "Yes; scope=ACTIVE");

        assertThatThrownBy(() -> new AuthorizationCatalogue(malformed))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must define edges on all granted cells");
    }

    @Test
    void refusesWhenNoActorScopeWasResolved() {
        assertThat(allows(AuthorizationCapability.ATTENDANCE_REPORT, Set.of(), "ACTIVE", "ACTIVE", null))
                .isFalse();
    }

    @Test
    void catalogueRejectsUnknownPredicateKeysAndMalformedSyntax() throws Exception {
        List<List<String>> malformed = resourceRows();
        List<String> createProject = malformed.stream()
                .filter(row -> row.get(0).equals("CREATE_PROJECT"))
                .findFirst().orElseThrow();
        createProject.set(3, "Yes; state=ACTIVE");
        assertThatThrownBy(() -> new AuthorizationCatalogue(malformed))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("invalid syntax");

        List<List<String>> unknownKey = resourceRows();
        unknownKey.get(1).set(0, "UNKNOWN_CAPABILITY");
        assertThatThrownBy(() -> new AuthorizationCatalogue(unknownKey))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("unknown capability key");
    }

    private boolean allows(AuthorizationCapability capability, Set<AuthorizationColumn> columns,
            String scopeState, String recordState, String targetState) {
        return policy.allows(capability, new AuthorizationRequest(columns, scopeState, recordState, targetState));
    }

    private static List<List<String>> resourceRows() throws Exception {
        return Files.readAllLines(Path.of("src/main/resources/platform/authorization-matrix.tsv")).stream()
                .filter(line -> !line.isBlank() && !line.stripLeading().startsWith("#"))
                .map(line -> new ArrayList<>(Arrays.asList(line.split("\\t", -1))))
                .map(row -> (List<String>) row)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private static List<String> splitRow(String row) {
        return java.util.Arrays.stream(row.substring(1, row.length() - 1).split("\\|", -1))
                .map(String::trim)
                .toList();
    }

    private static int indexOfAfter(List<String> lines, String target, int start) {
        for (int index = start; index < lines.size(); index++) {
            if (target.equals(lines.get(index))) {
                return index;
            }
        }
        return -1;
    }
}
