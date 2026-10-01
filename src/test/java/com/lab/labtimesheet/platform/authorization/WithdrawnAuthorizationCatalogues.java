package com.lab.labtimesheet.platform.authorization;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Builds a test-only catalogue copy with the one Admin Attendance report grant withdrawn. */
public final class WithdrawnAuthorizationCatalogues {

    private static final List<String> HEADER = List.of(
            "Key", "Capability", "Admin", "Owning Mentor", "Current Leader", "Active member / assignee");

    private WithdrawnAuthorizationCatalogues() {}

    /** Returns a validated catalogue whose only changed cell is Admin / ATTENDANCE_REPORT. */
    public static AuthorizationCatalogue attendanceReportAdminWithdrawn() {
        List<List<String>> rows = new ArrayList<>();
        rows.add(HEADER);
        try (var stream = AuthorizationCatalogue.class.getClassLoader()
                .getResourceAsStream("platform/authorization-matrix.tsv")) {
            if (stream == null) throw new IllegalStateException("Authorization matrix resource is missing");
            try (var reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank() || line.startsWith("#") || line.startsWith("Key\t")) continue;
                    rows.add(new ArrayList<>(Arrays.asList(line.split("\t", -1))));
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Authorization matrix could not be read", exception);
        }
        List<String> selected = rows.stream()
                .filter(row -> row.size() == HEADER.size() && row.getFirst().equals("ATTENDANCE_REPORT"))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("ATTENDANCE_REPORT row is missing"));
        if (!selected.get(2).equals("Yes")) {
            throw new IllegalStateException("Admin ATTENDANCE_REPORT grant is not the expected baseline");
        }
        selected.set(2, "No");
        AuthorizationCatalogue baseline = AuthorizationCatalogue.loadDefault();
        AuthorizationCatalogue withdrawn = new AuthorizationCatalogue(rows);
        List<String> baselineRows = baseline.rowsInMatrixOrder();
        List<String> withdrawnRows = withdrawn.rowsInMatrixOrder();
        long changedCells = 0;
        for (int index = 0; index < baselineRows.size(); index++) {
            String[] before = baselineRows.get(index).split("\t", -1);
            String[] after = withdrawnRows.get(index).split("\t", -1);
            for (int column = 0; column < before.length; column++) {
                if (!before[column].equals(after[column])) changedCells++;
            }
        }
        if (changedCells != 1) {
            throw new IllegalStateException("Withdrawn catalogue must differ in exactly one matrix cell");
        }
        return withdrawn;
    }
}
