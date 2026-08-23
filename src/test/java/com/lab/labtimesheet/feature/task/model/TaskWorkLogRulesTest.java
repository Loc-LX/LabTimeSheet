package com.lab.labtimesheet.feature.task.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lab.labtimesheet.feature.task.model.entity.TaskWorkLog;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TaskWorkLogRulesTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-20T00:00:00Z");

    @Test
    void correctionKeepsOriginalAuthorAndCreationTimestamp() {
        TaskWorkLog log = new TaskWorkLog(
                10L,
                25L,
                70L,
                LocalDate.of(2026, 8, 20),
                120,
                "Initial note",
                CREATED_AT);

        log.correct(240, "Corrected note", Instant.parse("2026-08-20T01:00:00Z"));

        assertThat(log.getProjectId()).isEqualTo(10L);
        assertThat(log.getTaskId()).isEqualTo(25L);
        assertThat(log.getMembershipId()).isEqualTo(70L);
        assertThat(log.getWorkDate()).isEqualTo(LocalDate.of(2026, 8, 20));
        assertThat(log.getMinutes()).isEqualTo(240);
        assertThat(log.getNote()).isEqualTo("Corrected note");
        assertThat(log.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(log.getUpdatedAt()).isEqualTo(Instant.parse("2026-08-20T01:00:00Z"));
    }

    @Test
    void blankNoteIsNotAnOptionalNote() {
        assertThatThrownBy(() -> new TaskWorkLog(
                        10L,
                        20L,
                        30L,
                        LocalDate.of(2026, 8, 20),
                        30,
                        "   ",
                        CREATED_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Work-log note cannot be blank");
    }
}
