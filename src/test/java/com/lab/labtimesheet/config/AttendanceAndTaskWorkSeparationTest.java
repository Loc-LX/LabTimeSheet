package com.lab.labtimesheet.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Holds attendance time and Task work time apart at the persistence layer.
 *
 * <p>Protects {@code GOV-004}, which the constitution lists among its own enforcement gaps and
 * proposes closing with exactly this check.
 */
class AttendanceAndTaskWorkSeparationTest {

    private static final Path PRODUCTION_SOURCES = Path.of("src/main/java/com/lab/labtimesheet");

    /** Identifiers through which attendance time is stored and read. */
    private static final List<String> ATTENDANCE_TIME = List.of(
            "attendance_records", "AttendanceRecordEntity", "AttendanceRecordRepository", "AttendanceQueryRepository");

    /** Identifiers through which Task work time is stored and read. */
    private static final List<String> TASK_WORK_TIME = List.of("task_work_logs", "TaskWorkLog");

    /**
     * Protects {@code GOV-004}. Observable break: one file gains a read path that reaches both
     * stores, and from there a report, a metric or a validation can derive attendance from logged
     * work or the reverse. An Intern who logs eight hours from home starts counting as present, or
     * a present day starts implying work that was never recorded.
     *
     * <p>The check is that no production source file names both domains at the persistence layer.
     * That is stricter than the join the constitution proposed asserting against, and it currently
     * holds: the separation is complete file by file, not merely query by query.
     *
     * <p>Service contracts are deliberately outside the check. {@code ARC-006} permits a feature to
     * call another feature's service and DTOs, and the Daily Project Work Report does exactly that
     * to show attendance context beside logged work. {@code GOV-004} forbids deriving one from the
     * other, not displaying them together, and what makes derivation possible is a shared read path
     * into the two tables. Those are the identifiers listed here.
     *
     * @throws IOException if a production source cannot be read
     */
    @Test
    void noProductionSourceReachesBothAttendanceRecordsAndTaskWorkLogs() throws IOException {
        List<String> filesTouchingBothDomains = new ArrayList<>();

        try (Stream<Path> sources = Files.walk(PRODUCTION_SOURCES)) {
            for (Path source : sources.filter(path -> path.toString().endsWith(".java")).toList()) {
                String text = Files.readString(source, StandardCharsets.UTF_8);
                boolean readsAttendanceTime = ATTENDANCE_TIME.stream().anyMatch(text::contains);
                boolean readsTaskWorkTime = TASK_WORK_TIME.stream().anyMatch(text::contains);
                if (readsAttendanceTime && readsTaskWorkTime) {
                    filesTouchingBothDomains.add(
                            PRODUCTION_SOURCES.relativize(source).toString().replace('\\', '/'));
                }
            }
        }

        assertThat(filesTouchingBothDomains)
                .as("GOV-004 keeps attendance time and Task work time in separate domains, "
                        + "so no production file may hold a read path into both")
                .isEmpty();
    }
}
