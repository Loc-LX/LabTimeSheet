package com.lab.labtimesheet.feature.project.model;

import static org.assertj.core.api.Assertions.assertThat;

import com.lab.labtimesheet.feature.project.model.entity.Task;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class TaskDefinitionRulesTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-20T00:00:00Z");
    private static final Instant UPDATED_AT = Instant.parse("2026-08-20T01:00:00Z");

    @Test
    void definitionEditChangesOnlyCurrentDefinitionAndUpdateTimestamp() {
        Task task = task();

        task.updateDefinition("Updated title", "Updated description", LocalDate.of(2026, 8, 25), UPDATED_AT);

        assertThat(task.getTitle()).isEqualTo("Updated title");
        assertThat(task.getDescription()).isEqualTo("Updated description");
        assertThat(task.getDueDate()).isEqualTo(LocalDate.of(2026, 8, 25));
        assertThat(task.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(task.getAssigneeMembershipId()).isEqualTo(70L);
        assertThat(task.getCreatorMembershipId()).isEqualTo(70L);
        assertThat(task.getAssignerMembershipId()).isEqualTo(70L);
        assertThat(task.getAssignedAt()).isEqualTo(CREATED_AT);
        assertThat(task.getUpdatedAt()).isEqualTo(UPDATED_AT);
    }

    @Test
    void reassignmentPreservesCreatorStateAndLifecycleAttribution() {
        Task task = task();
        task.changeStatus(TaskStatus.IN_PROGRESS, UPDATED_AT);

        task.reassign(71L, 72L, Instant.parse("2026-08-20T02:00:00Z"));

        assertThat(task.getAssigneeMembershipId()).isEqualTo(71L);
        assertThat(task.getAssignerMembershipId()).isEqualTo(72L);
        assertThat(task.getAssignedAt()).isEqualTo(Instant.parse("2026-08-20T02:00:00Z"));
        assertThat(task.getCreatorMembershipId()).isEqualTo(70L);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(task.getCreatedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    void softDeletionRetainsTaskAndStoresDeletionAttribution() {
        Task task = task();

        task.softDelete(72L, UPDATED_AT);

        assertThat(task.getDeletedAt()).isEqualTo(UPDATED_AT);
        assertThat(task.getDeletedByMembershipId()).isEqualTo(72L);
        assertThat(task.getUpdatedAt()).isEqualTo(UPDATED_AT);
        assertThat(task.isDeleted()).isTrue();
    }

    private static Task task() {
        return new Task(
                10L,
                70L,
                "Original title",
                "Original description",
                LocalDate.of(2026, 8, 20),
                70L,
                CREATED_AT);
    }
}
