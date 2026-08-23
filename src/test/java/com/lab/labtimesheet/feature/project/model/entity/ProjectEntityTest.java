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

    /** [I1-PRJ-01, I1-PRJ-03] Tạo aggregate có membership và Leader đầu tiên cùng lúc. */
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

    /** [I1-PRJ-01, I1-PRJ-03] Từ chối ngày hoặc Leader ban đầu không hợp lệ. */
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

    /** [I1-PRJ-02] Thêm member đủ điều kiện và chặn membership hiện tại bị trùng. */
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

    /** [I1-PRJ-02] Một Intern có thể có membership hiện tại ở nhiều Project. */
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

    /** [I1-PRJ-03, I2-PRJ-01, I2-PRJ-02] Leader cũ hạ vai trò nhưng vẫn là thành viên hiện tại của Project. */
    @Test
    void ownerChangesExactlyOneLeaderWithoutChangingMemberships() {
        var project = plannedProject();
        project.addMember(10L, activeIntern(21L), CREATED_AT.plusSeconds(60));

        var change = project.prepareLeaderChange(10L, null, activeIntern(21L), CREATED_AT.plusSeconds(120));
        project.completeLeaderChange(10L, change);

        assertEquals(2, project.memberships().size());
        assertTrue(project.hasCurrentMember(20L));
        assertTrue(project.hasCurrentMember(21L));
        assertEquals(2, project.leadershipTerms().size());
        assertEquals(1, project.leadershipTerms().stream().filter(ProjectLeadershipTermEntity::isCurrent).count());
        assertEquals(21L, project.currentLeader().internUserId());
        assertFalse(project.leadershipTerms().getFirst().isCurrent());
        assertThrows(ProjectRuleViolationException.class,
                () -> project.prepareLeaderChange(10L, null, activeIntern(21L), CREATED_AT.plusSeconds(180)));
        assertThrows(ProjectRuleViolationException.class,
                () -> project.prepareLeaderChange(10L, null, activeIntern(22L), CREATED_AT.plusSeconds(180)));
    }

    /** [I2-PRJ-03] Replacement được mở trước rồi mới đóng membership Leader cũ. */
    @Test
    void ownerRemovesCurrentLeaderOnlyAfterPreparingAReplacement() {
        var project = plannedProject();
        project.addMember(10L, activeIntern(21L), CREATED_AT.plusSeconds(60));

        var removal = project.prepareLeaderRemoval(
                10L, null, activeIntern(21L), CREATED_AT.plusSeconds(120));
        project.completeLeaderRemoval(10L, removal);

        assertFalse(project.memberships().getFirst().isCurrent());
        assertTrue(project.memberships().get(1).isCurrent());
        assertEquals(21L, project.currentLeader().internUserId());
        assertEquals(2, project.leadershipTerms().size());
        assertEquals(1, project.leadershipTerms().stream().filter(ProjectLeadershipTermEntity::isCurrent).count());
        assertEquals(10L, project.memberships().getFirst().removedByMentorUserId());
    }

    /** [I2-PRJ-03] Replacement lỗi không được làm thay đổi Leader hoặc membership hiện tại. */
    @Test
    void leaderRemovalRejectsAnIneligibleReplacementBeforeMutation() {
        var project = plannedProject();

        assertThrows(ProjectRuleViolationException.class, () -> project.prepareLeaderRemoval(
                10L, null, new ProjectInternEligibility(21L, false), CREATED_AT.plusSeconds(60)));

        assertTrue(project.memberships().getFirst().isCurrent());
        assertEquals(20L, project.currentLeader().internUserId());
        assertEquals(1, project.leadershipTerms().stream().filter(ProjectLeadershipTermEntity::isCurrent).count());
    }

    /** [I2-PRJ-04] Member thường chỉ được đóng sau khi boundary Task đã chuẩn bị transfer sang Leader. */
    @Test
    void ordinaryMemberRemovalKeepsLeaderAndClosesOnlyTheDepartingMembership() {
        var project = plannedProject();
        project.addMember(10L, activeIntern(21L), CREATED_AT.plusSeconds(60));

        var removal = project.prepareMemberRemoval(10L, 21L, CREATED_AT.plusSeconds(120));
        project.completeMemberRemoval(10L, removal);

        assertTrue(project.memberships().getFirst().isCurrent());
        assertFalse(project.memberships().get(1).isCurrent());
        assertEquals(20L, project.currentLeader().internUserId());
        assertEquals(10L, project.memberships().get(1).removedByMentorUserId());
    }

    /** [I2-PRJ-04] Không cho đóng membership của current Leader bằng nút remove member thường. */
    @Test
    void ordinaryMemberRemovalRejectsTheCurrentLeader() {
        var project = plannedProject();

        assertThrows(ProjectRuleViolationException.class,
                () -> project.prepareMemberRemoval(10L, 20L, CREATED_AT.plusSeconds(60)));

        assertTrue(project.memberships().getFirst().isCurrent());
        assertTrue(project.leadershipTerms().getFirst().isCurrent());
    }

    /** [I2-PRJ-05] Hoàn tất đóng term Leader cuối, mọi membership hiện tại và khóa aggregate. */
    @Test
    void ownerCompletesActiveProjectAfterEveryTaskIsDone() {
        var project = plannedProject();
        project.addMember(10L, activeIntern(21L), CREATED_AT.plusSeconds(60));
        project.activate(10L, Set.of(20L, 21L), true, CREATED_AT.plusSeconds(120));

        project.complete(10L, true, CREATED_AT.plusSeconds(180));

        assertEquals(ProjectStatus.COMPLETED, project.status());
        assertEquals(CREATED_AT.plusSeconds(180), project.completedAt());
        assertTrue(project.memberships().stream().noneMatch(ProjectMembershipEntity::isCurrent));
        assertTrue(project.leadershipTerms().stream().noneMatch(ProjectLeadershipTermEntity::isCurrent));
        assertThrows(ProjectRuleViolationException.class,
                () -> project.addMember(10L, activeIntern(22L), CREATED_AT.plusSeconds(240)));
        assertThrows(ProjectRuleViolationException.class,
                () -> project.prepareLeaderChange(10L, null, activeIntern(21L), CREATED_AT.plusSeconds(240)));
    }

    /** [I2-PRJ-05] Chặn hoàn tất trước ACTIVE hoặc khi còn Task chưa DONE mà không đóng interval. */
    @Test
    void completionRequiresActiveProjectAndDoneTasks() {
        var project = plannedProject();

        assertThrows(ProjectRuleViolationException.class,
                () -> project.complete(10L, true, CREATED_AT.plusSeconds(60)));

        project.activate(10L, Set.of(20L), true, CREATED_AT.plusSeconds(60));
        assertThrows(ProjectRuleViolationException.class,
                () -> project.complete(10L, false, CREATED_AT.plusSeconds(120)));

        assertEquals(ProjectStatus.ACTIVE, project.status());
        assertTrue(project.memberships().getFirst().isCurrent());
        assertTrue(project.leadershipTerms().getFirst().isCurrent());
    }

    /** [I1-PRJ-04] Chỉ kích hoạt khi owner, member, Leader và assignee guard đều hợp lệ. */
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
