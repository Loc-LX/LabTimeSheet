package com.lab.labtimesheet.feature.project.model.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lab.labtimesheet.feature.project.exception.ProjectAccessDeniedException;
import com.lab.labtimesheet.feature.project.exception.ProjectRuleViolationException;
import com.lab.labtimesheet.feature.project.model.ProjectInternEligibility;
import com.lab.labtimesheet.feature.project.model.ProjectStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ProjectEntityTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-14T02:00:00Z");

    @Test
    void planningCreatesTheInitialLeaderMembershipAndTermTogether() {
        var project = ProjectEntity.plan(
                10L,
                " Intern Portal Refresh ",
                " Refresh the portal ",
                LocalDate.of(2026, 8, 15),
                LocalDate.of(2026, 9, 30),
                activeIntern(20L),
                CREATED_AT);

        assertEquals(ProjectStatus.PLANNED, project.status());
        assertEquals("Intern Portal Refresh", project.name());
        assertEquals("Refresh the portal", project.description());
        assertEquals(1, project.memberships().size());
        assertEquals(20L, project.memberships().getFirst().internUserId());
        assertEquals(10L, project.memberships().getFirst().addedByUserId());
        assertEquals(1, project.leadershipTerms().size());
        assertEquals(20L, project.currentLeader().internUserId());
    }

    @Test
    void planningRejectsAnIneligibleInitialLeaderAndInvalidDates() {
        assertThrows(ProjectRuleViolationException.class, () -> ProjectEntity.plan(
                10L,
                "Project",
                null,
                LocalDate.of(2026, 9, 1),
                LocalDate.of(2026, 8, 31),
                activeIntern(20L),
                CREATED_AT));
        assertThrows(ProjectRuleViolationException.class, () -> ProjectEntity.plan(
                10L,
                "Project",
                null,
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                new ProjectInternEligibility(20L, false),
                CREATED_AT));
    }

    @Test
    void onlyPlannedProjectsCanBeDeletedByTheirOwner() {
        var project = plannedProject();

        project.requireDeletable(10L);
        assertThrows(ProjectAccessDeniedException.class, () -> project.requireDeletable(11L));

        project.activate(10L, Set.of(20L), true, CREATED_AT.plusSeconds(60));
        assertThrows(ProjectRuleViolationException.class, () -> project.requireDeletable(10L));
    }

    @Test
    void ownerAddsEligibleMembersButNotDuplicateCurrentMemberships() {
        var project = plannedProject();

        project.addMember(10L, activeIntern(21L), CREATED_AT.plusSeconds(60));

        assertEquals(2, project.memberships().size());
        assertTrue(project.hasCurrentMember(21L));
        assertThrows(ProjectRuleViolationException.class,
                () -> project.addMember(10L, activeIntern(21L), CREATED_AT.plusSeconds(120)));
        assertThrows(ProjectAccessDeniedException.class,
                () -> project.addMember(11L, activeIntern(22L), CREATED_AT.plusSeconds(120)));
    }

    @Test
    void theSameInternCanBelongToSeparateProjects() {
        var first = plannedProject();
        var second = ProjectEntity.plan(
                11L,
                "Second",
                null,
                LocalDate.of(2026, 8, 15),
                LocalDate.of(2026, 9, 30),
                activeIntern(21L),
                CREATED_AT);

        first.addMember(10L, activeIntern(21L), CREATED_AT.plusSeconds(60));

        assertTrue(first.hasCurrentMember(21L));
        assertTrue(second.hasCurrentMember(21L));
    }

    @Test
    void ownerChangesExactlyOneLeaderWithoutChangingMemberships() {
        var project = plannedProject();
        project.addMember(10L, activeIntern(21L), CREATED_AT.plusSeconds(60));

        var change = project.prepareLeaderChange(10L, activeIntern(21L), CREATED_AT.plusSeconds(120));
        project.completeLeaderChange(10L, change);

        assertEquals(2, project.memberships().size());
        assertEquals(2, project.leadershipTerms().size());
        assertEquals(1, project.leadershipTerms().stream().filter(ProjectLeadershipTermEntity::isCurrent).count());
        assertEquals(21L, project.currentLeader().internUserId());
        assertFalse(project.leadershipTerms().getFirst().isCurrent());
        assertThrows(ProjectRuleViolationException.class,
                () -> project.prepareLeaderChange(10L, activeIntern(21L), CREATED_AT.plusSeconds(180)));
        assertThrows(ProjectRuleViolationException.class,
                () -> project.prepareLeaderChange(10L, activeIntern(22L), CREATED_AT.plusSeconds(180)));
    }

    @Test
    void activationRequiresOwnerAndValidCurrentTaskAssignees() {
        var project = plannedProject();

        assertThrows(ProjectAccessDeniedException.class,
                () -> project.activate(11L, Set.of(20L), true, CREATED_AT.plusSeconds(60)));
        assertThrows(ProjectRuleViolationException.class,
                () -> project.activate(10L, Set.of(), true, CREATED_AT.plusSeconds(60)));
        assertThrows(ProjectRuleViolationException.class,
                () -> project.activate(10L, Set.of(21L), true, CREATED_AT.plusSeconds(60)));
        assertThrows(ProjectRuleViolationException.class,
                () -> project.activate(10L, Set.of(20L), false, CREATED_AT.plusSeconds(60)));

        project.activate(10L, Set.of(20L), true, CREATED_AT.plusSeconds(60));

        assertEquals(ProjectStatus.ACTIVE, project.status());
        assertEquals(CREATED_AT.plusSeconds(60), project.activatedAt());
        assertThrows(ProjectRuleViolationException.class,
                () -> project.activate(10L, Set.of(20L), true, CREATED_AT.plusSeconds(120)));
    }

    @Test
    void completionClosesRetainedMembershipAndLeadershipIntervals() {
        var project = plannedProject();
        project.activate(10L, Set.of(20L), true, CREATED_AT.plusSeconds(60));

        project.complete(10L, CREATED_AT.plusSeconds(120));

        assertEquals(ProjectStatus.COMPLETED, project.status());
        assertEquals(CREATED_AT.plusSeconds(120), project.completedAt());
        assertTrue(project.memberships().stream().allMatch(membership -> membership.leftAt() != null));
        assertTrue(project.leadershipTerms().stream().noneMatch(ProjectLeadershipTermEntity::isCurrent));
        assertThrows(ProjectRuleViolationException.class,
                () -> project.complete(10L, CREATED_AT.plusSeconds(180)));
    }

    /**
     * Protects the second sentence of {@code PRJ-002}, which forbids reopening a `COMPLETED`
     * Project. Observable break: the activation guard is relaxed from "only a planned Project" to
     * anything that is not already active, and a finished Project can be brought back to life. Its
     * memberships and leadership terms were closed at completion, so it would reopen without a
     * current Leader and without members, which `PRJ-005` requires every open Project to have.
     *
     * <p>The forward transitions and the refusal to complete twice are asserted by the tests above.
     * The path back was the one nothing tried.
     *
     * <p>The message is asserted, not merely the exception type, and that distinction is the whole
     * test. Completion closes every membership and leadership term, so a reopened Project also
     * fails the "requires an active member" guard and raises the same exception type for an
     * unrelated reason. An assertion on the type alone passes even after the lifecycle guard is
     * removed, which was checked. Pinning the message makes the test fail for the intended reason
     * and only that one. The unchanged status is asserted as well, because a guard that threw after
     * mutating would satisfy the first assertion.
     */
    @Test
    void aCompletedProjectCannotBeReopened() {
        var project = plannedProject();
        project.activate(10L, Set.of(20L), true, CREATED_AT.plusSeconds(60));
        project.complete(10L, CREATED_AT.plusSeconds(120));

        ProjectRuleViolationException refusal = assertThrows(ProjectRuleViolationException.class,
                () -> project.activate(10L, Set.of(20L), true, CREATED_AT.plusSeconds(180)));

        assertTrue(
                refusal.getMessage().contains("Only a planned Project can be activated"),
                "PRJ-002 must refuse a COMPLETED Project at the lifecycle guard, "
                        + "not incidentally at a later one. Actual: " + refusal.getMessage());
        assertEquals(ProjectStatus.COMPLETED, project.status());
        assertEquals(CREATED_AT.plusSeconds(120), project.completedAt());
    }

    private static ProjectEntity plannedProject() {
        return ProjectEntity.plan(
                10L,
                "Project",
                null,
                LocalDate.of(2026, 8, 15),
                LocalDate.of(2026, 9, 30),
                activeIntern(20L),
                CREATED_AT);
    }

    private static ProjectInternEligibility activeIntern(long userId) {
        return new ProjectInternEligibility(userId, true);
    }
}
