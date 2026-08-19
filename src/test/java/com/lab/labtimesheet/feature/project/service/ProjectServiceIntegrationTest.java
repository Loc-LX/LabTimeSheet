package com.lab.labtimesheet.feature.project.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.project.exception.ProjectAccessDeniedException;
import com.lab.labtimesheet.feature.project.exception.ProjectRuleViolationException;
import com.lab.labtimesheet.feature.project.model.dto.ProjectCreateCommand;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ProjectServiceIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-08-14T00:00:00Z");

    @Autowired
    private ProjectService projectService;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private ProjectQueryService projectPages;

    @PersistenceContext
    private EntityManager entityManager;

    /** [I1-PRJ-01] Tạo Project nguyên tử cùng membership và Leader đầu tiên. */
    @Test
    void createsProjectMembershipAndLeadershipInOneTransaction() {
        long mentorId = user("mentor-create@example.test", "MENTOR");
        long leaderId = intern("leader-create@example.test", "I001");

        long projectId = projectService.create(
                mentorId,
                new ProjectCreateCommand(
                        "Intern Portal Refresh",
                        "Refresh the portal",
                        LocalDate.of(2026, 8, 15),
                        LocalDate.of(2026, 9, 30),
                        leaderId));

        assertEquals("PLANNED", text("select status from projects where id = ?", projectId));
        assertEquals(1, count("select count(*) from project_memberships where project_id = ? and left_at is null", projectId));
        assertEquals(1, count("select count(*) from project_leadership_terms where project_id = ? and ended_at is null", projectId));
        assertEquals(leaderId, number("""
                select membership.intern_user_id
                from project_leadership_terms leadership
                join project_memberships membership on membership.id = leadership.membership_id
                where leadership.project_id = ? and leadership.ended_at is null
                """, projectId));

        long nonMentorId = intern("not-mentor@example.test", "I002");
        assertThrows(ProjectAccessDeniedException.class, () -> projectService.create(
                nonMentorId,
                new ProjectCreateCommand(
                        "Denied",
                        null,
                        LocalDate.of(2026, 8, 15),
                        LocalDate.of(2026, 8, 31),
                        leaderId)));
        assertEquals(0, count("select count(*) from projects where name = 'Denied'"));
    }

    /** [I1-PRJ-02] Thêm member và từ chối membership hiện tại bị trùng. */
    @Test
    void ownerAddsEligibleMemberAndDuplicateCurrentMembershipIsRejected() {
        long mentorId = user("mentor-add@example.test", "MENTOR");
        long leaderId = intern("leader-add@example.test", "I003");
        long memberId = intern("member-add@example.test", "I004");
        long projectId = createProject(mentorId, leaderId, "Membership");
        long otherProjectId = createProject(mentorId, memberId, "Concurrent membership");

        projectService.addMember(mentorId, projectId, memberId);

        assertEquals(1, count("""
                select count(*) from project_memberships
                where project_id = ? and intern_user_id = ? and left_at is null
                """, projectId, memberId));
        assertEquals(1, count("""
                select count(*) from project_memberships
                where project_id = ? and intern_user_id = ? and left_at is null
                """, otherProjectId, memberId));
        assertThrows(ProjectRuleViolationException.class,
                () -> projectService.addMember(mentorId, projectId, memberId));
        assertThrows(ProjectAccessDeniedException.class,
                () -> projectService.addMember(
                        user("other-mentor@example.test", "MENTOR"), projectId, Long.MAX_VALUE));
    }

    /** [I1-PRJ-02] Thêm nhiều member trong cùng transaction có khóa Project. */
    @Test
    void ownerAddsSeveralEligibleMembersInOneLockedTransaction() {
        long mentorId = user("mentor-batch-add@example.test", "MENTOR");
        long leaderId = intern("leader-batch-add@example.test", "I017");
        long firstMemberId = intern("first-batch-add@example.test", "I018");
        long secondMemberId = intern("second-batch-add@example.test", "I019");
        long projectId = createProject(mentorId, leaderId, "Batch membership");

        projectService.addMembers(mentorId, projectId, List.of(firstMemberId, secondMemberId));

        assertEquals(3, count("""
                select count(*) from project_memberships
                where project_id = ? and left_at is null
                """, projectId));
    }

    /** [I1-PRJ-02] Lựa chọn member lỗi không được tạo dữ liệu một phần. */
    @Test
    void memberBatchRejectsMissingDuplicateCurrentAndStaleSelectionsWithoutPartialMutation() {
        long mentorId = user("mentor-batch-guard@example.test", "MENTOR");
        long leaderId = intern("leader-batch-guard@example.test", "I020");
        long eligibleId = intern("eligible-batch-guard@example.test", "I021");
        long staleId = intern("stale-batch-guard@example.test", "I022");
        long projectId = createProject(mentorId, leaderId, "Batch guard");
        jdbc.update("update intern_profiles set internship_end_date = date '2026-08-13' where user_id = ?", staleId);
        entityManager.clear();

        assertThrows(ProjectRuleViolationException.class,
                () -> projectService.addMembers(mentorId, projectId, null));
        assertThrows(ProjectRuleViolationException.class,
                () -> projectService.addMembers(mentorId, projectId, List.of()));
        assertThrows(ProjectRuleViolationException.class,
                () -> projectService.addMembers(mentorId, projectId, List.of(eligibleId, eligibleId)));
        assertThrows(ProjectRuleViolationException.class,
                () -> projectService.addMembers(mentorId, projectId, List.of(Long.MAX_VALUE)));
        assertThrows(ProjectRuleViolationException.class,
                () -> projectService.addMembers(mentorId, projectId, List.of(mentorId)));
        assertThrows(ProjectRuleViolationException.class,
                () -> projectService.addMembers(mentorId, projectId, List.of(leaderId)));
        assertThrows(ProjectRuleViolationException.class,
                () -> projectService.addMembers(mentorId, projectId, List.of(eligibleId, staleId)));

        assertEquals(0, count("""
                select count(*) from project_memberships
                where project_id = ? and intern_user_id in (?, ?) and left_at is null
                """, projectId, eligibleId, staleId));
    }

    /**
     * [I1-PRJ-03, I2-PRJ-01, I2-PRJ-02] Đổi Leader chỉ đổi nhiệm kỳ; membership cũ và toàn bộ thông tin phân công Task
     * phải được giữ nguyên để Leader cũ tiếp tục làm việc trên Task đã giao cho mình.
     */
    @Test
    void leaderChangeClosesOneTermAndDoesNotMoveTaskAssignments() {
        long mentorId = user("mentor-leader@example.test", "MENTOR");
        long firstLeaderId = intern("leader-one@example.test", "I005");
        long nextLeaderId = intern("leader-two@example.test", "I006");
        long projectId = createProject(mentorId, firstLeaderId, "Leadership");
        projectService.addMember(mentorId, projectId, nextLeaderId);
        long firstMembershipId = membershipId(projectId, firstLeaderId);
        jdbc.update("""
                insert into tasks (
                    project_id, assignee_membership_id, title,
                    created_by_membership_id, assigned_by_membership_id)
                values (?, ?, 'Keep assignee', ?, ?)
                """, projectId, firstMembershipId, firstMembershipId, firstMembershipId);
        var taskBeforeChange = taskAssignment(projectId);

        long expectedTermId = number("""
                select id from project_leadership_terms
                where project_id = ? and ended_at is null
                """, projectId);
        projectService.changeLeader(mentorId, projectId, expectedTermId, nextLeaderId);

        assertEquals(1, count("select count(*) from project_leadership_terms where project_id = ? and ended_at is null", projectId));
        assertEquals(1, count("select count(*) from project_leadership_terms where project_id = ? and ended_at is not null", projectId));
        assertEquals(nextLeaderId, number("""
                select membership.intern_user_id
                from project_leadership_terms leadership
                join project_memberships membership on membership.id = leadership.membership_id
                where leadership.project_id = ? and leadership.ended_at is null
                """, projectId));
        assertEquals(1, count("""
                select count(*) from project_memberships
                where project_id = ? and intern_user_id = ? and left_at is null
                """, projectId, firstLeaderId));
        assertEquals(taskBeforeChange, taskAssignment(projectId));
    }

    /** [I2-PRJ-01] Form cũ không được ghi đè nhiệm kỳ hiện tại đã thay đổi. */
    @Test
    void staleLeaderChangeCannotReplaceTheCurrentTermAfterAnotherChangeCommits() {
        long mentorId = user("mentor-stale-leader@example.test", "MENTOR");
        long firstLeaderId = intern("stale-leader-one@example.test", "I901");
        long nextLeaderId = intern("stale-leader-two@example.test", "I902");
        long projectId = createProject(mentorId, firstLeaderId, "Stale leadership");
        projectService.addMember(mentorId, projectId, nextLeaderId);

        long termLoadedByTheFirstForm = number("""
                select id from project_leadership_terms
                where project_id = ? and ended_at is null
                """, projectId);

        projectService.changeLeader(mentorId, projectId, termLoadedByTheFirstForm, nextLeaderId);

        assertThrows(ProjectRuleViolationException.class, () -> projectService.changeLeader(
                mentorId, projectId, termLoadedByTheFirstForm, firstLeaderId));
        assertEquals(nextLeaderId, number("""
                select membership.intern_user_id
                from project_leadership_terms leadership
                join project_memberships membership on membership.id = leadership.membership_id
                where leadership.project_id = ? and leadership.ended_at is null
                """, projectId));
        assertEquals(1, count("select count(*) from project_leadership_terms where project_id = ? and ended_at is null", projectId));
        assertEquals(2, count("select count(*) from project_leadership_terms where project_id = ?", projectId));
    }

    /** [I2-PRJ-01] Hai handoff cạnh tranh cùng token chỉ có một request thắng. */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentLeaderChangesWithTheSameTermAllowOneWinner() throws Exception {
        long mentorId = user("mentor-concurrent-leader@example.test", "MENTOR");
        long firstLeaderId = intern("concurrent-leader-one@example.test", "I903");
        long firstReplacementId = intern("concurrent-leader-two@example.test", "I904");
        long secondReplacementId = intern("concurrent-leader-three@example.test", "I905");
        long projectId = createProject(mentorId, firstLeaderId, "Concurrent leadership");
        projectService.addMembers(mentorId, projectId, List.of(firstReplacementId, secondReplacementId));
        long termId = number("""
                select id from project_leadership_terms
                where project_id = ? and ended_at is null
                """, projectId);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try {
            var first = executor.submit(() -> changeLeaderAfterStart(
                    ready, start, mentorId, projectId, termId, firstReplacementId));
            var second = executor.submit(() -> changeLeaderAfterStart(
                    ready, start, mentorId, projectId, termId, secondReplacementId));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();

            int successes = 0;
            int staleFailures = 0;
            for (var result : List.of(first, second)) {
                try {
                    result.get();
                    successes++;
                } catch (ExecutionException exception) {
                    assertTrue(exception.getCause() instanceof ProjectRuleViolationException);
                    staleFailures++;
                }
            }
            assertEquals(1, successes);
            assertEquals(1, staleFailures);
            assertEquals(1, count(
                    "select count(*) from project_leadership_terms where project_id = ? and ended_at is null",
                    projectId));
            assertEquals(2, count(
                    "select count(*) from project_leadership_terms where project_id = ?", projectId));
        } finally {
            executor.shutdownNow();
            deleteConcurrentLeadershipFixture(
                    projectId, mentorId, firstLeaderId, firstReplacementId, secondReplacementId);
        }
    }

    private Void changeLeaderAfterStart(
            CountDownLatch ready,
            CountDownLatch start,
            long mentorId,
            long projectId,
            long expectedTermId,
            long replacementId) throws InterruptedException {
        ready.countDown();
        start.await();
        projectService.changeLeader(mentorId, projectId, expectedTermId, replacementId);
        return null;
    }

    /** Xóa fixture đã commit vì test concurrency này chủ động tắt rollback. */
    private void deleteConcurrentLeadershipFixture(
            long projectId,
            long mentorId,
            long firstLeaderId,
            long firstReplacementId,
            long secondReplacementId) {
        jdbc.update("delete from project_leadership_terms where project_id = ?", projectId);
        jdbc.update("delete from project_memberships where project_id = ?", projectId);
        jdbc.update("delete from projects where id = ?", projectId);
        jdbc.update("delete from intern_profiles where user_id in (?, ?, ?, ?)",
                firstLeaderId, firstReplacementId, secondReplacementId, mentorId);
        jdbc.update("delete from app_users where id in (?, ?, ?, ?)",
                mentorId, firstLeaderId, firstReplacementId, secondReplacementId);
    }

    /** [I1-PRJ-05, I2-PRJ-06] Query Project kiểm tra quyền sở hữu, membership và ID đoán ngẫu nhiên. */
    @Test
    void listAndDetailQueriesEnforceRoleOwnershipAndMembershipWithoutIdDisclosure() {
        long adminId = user("admin-view@example.test", "ADMIN");
        long mentorId = user("mentor-view@example.test", "MENTOR");
        long otherMentorId = user("other-mentor-view@example.test", "MENTOR");
        long leaderId = intern("leader-view@example.test", "I009");
        long memberId = intern("member-view@example.test", "I010");
        long unrelatedId = intern("unrelated-view@example.test", "I011");
        long projectId = createProject(mentorId, leaderId, "Visible project");
        projectService.addMember(mentorId, projectId, memberId);

        assertEquals(List.of(projectId), projectPages.listVisible(adminId).stream().map(summary -> summary.id()).toList());
        assertEquals(List.of(projectId), projectPages.listVisible(mentorId).stream().map(summary -> summary.id()).toList());
        assertEquals(List.of(), projectPages.listVisible(otherMentorId));
        assertEquals(List.of(projectId), projectPages.listVisible(memberId).stream().map(summary -> summary.id()).toList());
        assertEquals(List.of(), projectPages.listVisible(unrelatedId));
        assertEquals(projectId, projectPages.detail(memberId, projectId).id());
        assertEquals("INTERN", projectPages.authenticatedActor("member-view@example.test").role());
        var taskContext = projectService.taskMutationContext(memberId, projectId);
        assertEquals(mentorId, taskContext.mentorUserId());
        assertEquals("PLANNED", taskContext.status());
        assertEquals(2, taskContext.activeMembers().size());
        assertEquals(membershipId(projectId, leaderId), taskContext.currentLeaderMembershipId());
        assertEquals(taskContext, projectPages.taskContext(memberId, projectId));
        assertThrows(ProjectAccessDeniedException.class,
                () -> projectService.taskMutationContext(otherMentorId, projectId));
        jdbc.update("""
                update projects set status = 'ACTIVE', activated_at = ?, updated_at = ? where id = ?
                """, dbTime(NOW.plusSeconds(30)), dbTime(NOW.plusSeconds(30)), projectId);
        entityManager.clear();
        assertEquals(1, projectPages.dashboardSummary(adminId).activeProjectCount());
        assertEquals(1, projectPages.dashboardSummary(mentorId).activeProjectCount());
        assertEquals(2, projectPages.dashboardSummary(mentorId).distinctActiveMemberCount());
        assertEquals(1, projectPages.dashboardSummary(memberId).activeProjectCount());
        assertThrows(ProjectAccessDeniedException.class, () -> projectPages.detail(otherMentorId, projectId));
        assertThrows(ProjectAccessDeniedException.class, () -> projectPages.detail(unrelatedId, projectId));
        assertThrows(ProjectAccessDeniedException.class, () -> projectPages.detail(unrelatedId, Long.MAX_VALUE));

        jdbc.update("""
                update project_memberships
                set left_at = ?, removed_by_mentor_user_id = ?
                where project_id = ? and intern_user_id = ?
                """, dbTime(NOW.plusSeconds(60)), mentorId, projectId, memberId);
        entityManager.clear();

        assertEquals(List.of(), projectPages.listVisible(memberId));
        assertThrows(ProjectAccessDeniedException.class, () -> projectPages.detail(memberId, projectId));
        assertThrows(ProjectAccessDeniedException.class,
                () -> projectService.taskMutationContext(memberId, projectId));
        assertEquals(0, projectPages.dashboardSummary(memberId).activeProjectCount());
        assertEquals(1, projectPages.dashboardSummary(mentorId).distinctActiveMemberCount());
    }

    /** [I2-PRJ-06] Thành viên cũ vẫn đọc được Project đã hoàn tất ở chế độ lịch sử. */
    @Test
    void completedProjectQueriesReturnHistoricalMembersWithoutRequiringACurrentLeader() {
        long adminId = user("admin-history@example.test", "ADMIN");
        long mentorId = user("mentor-history@example.test", "MENTOR");
        long leaderId = intern("leader-history@example.test", "I012");
        long memberId = intern("member-history@example.test", "I013");
        long projectId = createProject(mentorId, leaderId, "Completed history");
        projectService.addMember(mentorId, projectId, memberId);
        var activatedAt = dbTime(NOW.plusSeconds(30));
        var completedAt = dbTime(NOW.plusSeconds(60));
        jdbc.update("""
                update project_leadership_terms
                set ended_at = ?, ended_by_mentor_user_id = ?
                where project_id = ? and ended_at is null
                """, completedAt, mentorId, projectId);
        jdbc.update("""
                update project_memberships
                set left_at = ?, removed_by_mentor_user_id = ?, updated_at = ?
                where project_id = ? and left_at is null
                """, completedAt, mentorId, completedAt, projectId);
        jdbc.update("""
                update projects
                set status = 'COMPLETED', activated_at = ?, completed_at = ?, updated_at = ?
                where id = ?
                """, activatedAt, completedAt, completedAt, projectId);
        entityManager.clear();

        assertEquals(List.of(projectId), projectPages.listVisible(memberId).stream()
                .map(summary -> summary.id())
                .toList());
        var ownerDetail = projectPages.detail(mentorId, projectId);
        var adminDetail = projectPages.detail(adminId, projectId);
        var formerMemberDetail = projectPages.detail(memberId, projectId);
        assertNull(ownerDetail.leaderName());
        assertNull(adminDetail.leaderName());
        assertNull(formerMemberDetail.leaderName());
        assertFalse(ownerDetail.canManage());
        assertFalse(adminDetail.canManage());
        assertFalse(formerMemberDetail.canManage());
        var taskContext = projectPages.taskContext(memberId, projectId);
        assertEquals("COMPLETED", taskContext.status());
        assertNull(taskContext.currentLeaderMembershipId());
        assertEquals(List.of(), taskContext.activeMembers());
        var members = projectPages.members(memberId, projectId);
        assertEquals(2, members.size());
        assertTrue(members.stream().allMatch(member -> member.leftAt() != null));
        assertTrue(members.stream().noneMatch(member -> member.currentLeader()));
    }

    /** [I1-PRJ-04] Kích hoạt Project khi các guard member, Leader và Task assignee hợp lệ. */
    @Test
    void ownerActivatesAPlannedProjectWhenCurrentMemberAndTaskAssigneeGuardsPass() {
        long mentorId = user("mentor-activate@example.test", "MENTOR");
        long leaderId = intern("leader-activate@example.test", "I014");
        long projectId = createProject(mentorId, leaderId, "Ready to activate");

        projectService.activate(mentorId, projectId);

        assertEquals("ACTIVE", text("select status from projects where id = ?", projectId));
        assertEquals(1, count("select count(*) from projects where id = ? and activated_at is not null", projectId));
    }

    /** [I1-PRJ-04] Guard assignee không hợp lệ giữ Project ở Planned và không mất Task. */
    @Test
    void activationRejectsATaskAssignedToAFormerMemberWithoutPartialMutation() {
        long mentorId = user("mentor-guard@example.test", "MENTOR");
        long leaderId = intern("leader-guard@example.test", "I015");
        long formerMemberId = intern("former-assignee@example.test", "I016");
        long projectId = createProject(mentorId, leaderId, "Assignee guard");
        projectService.addMember(mentorId, projectId, formerMemberId);
        long leaderMembershipId = membershipId(projectId, leaderId);
        long formerMembershipId = membershipId(projectId, formerMemberId);
        jdbc.update("""
                insert into tasks (
                    project_id, assignee_membership_id, title,
                    created_by_membership_id, assigned_by_membership_id)
                values (?, ?, 'Former assignee', ?, ?)
                """, projectId, formerMembershipId, leaderMembershipId, leaderMembershipId);
        jdbc.update("""
                update project_memberships
                set left_at = ?, removed_by_mentor_user_id = ?, updated_at = ?
                where id = ?
                """, dbTime(NOW.plusSeconds(60)), mentorId, dbTime(NOW.plusSeconds(60)), formerMembershipId);
        entityManager.clear();

        assertThrows(ProjectRuleViolationException.class, () -> projectService.activate(mentorId, projectId));

        assertEquals("PLANNED", text("select status from projects where id = ?", projectId));
        assertEquals(1, count("select count(*) from tasks where project_id = ? and deleted_at is null", projectId));
    }

    private long createProject(long mentorId, long leaderId, String name) {
        return projectService.create(
                mentorId,
                new ProjectCreateCommand(
                        name,
                        null,
                        LocalDate.of(2026, 8, 15),
                        LocalDate.of(2026, 9, 30),
                        leaderId));
    }

    private long user(String email, String role) {
        return jdbc.queryForObject("""
                insert into app_users (
                    email, display_name, password_hash, global_role, account_status, activated_at)
                values (?, ?, '{noop}password-password', ?, 'ACTIVE', ?)
                returning id
                """, Long.class, email, email, role, dbTime(NOW));
    }

    private long intern(String email, String studentCode) {
        long userId = user(email, "INTERN");
        jdbc.update("""
                insert into intern_profiles (
                    user_id, student_code, internship_start_date, internship_end_date,
                    internship_status, activated_at)
                values (?, ?, date '2026-08-01', date '2026-12-31', 'ACTIVE', ?)
                """, userId, studentCode, dbTime(NOW));
        return userId;
    }

    private long membershipId(long projectId, long internUserId) {
        return number("""
                select id from project_memberships
                where project_id = ? and intern_user_id = ? and left_at is null
                """, projectId, internUserId);
    }

    private int count(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Integer.class, arguments);
    }

    private long number(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, Long.class, arguments);
    }

    private String text(String sql, Object... arguments) {
        return jdbc.queryForObject(sql, String.class, arguments);
    }

    /** Đọc các cột assignment để chứng minh I2-PRJ-02 không phát sinh chuyển Task ngầm. */
    private TaskAssignmentSnapshot taskAssignment(long projectId) {
        return jdbc.queryForObject("""
                select assignee_membership_id, created_by_membership_id,
                       assigned_by_membership_id, status, title
                from tasks
                where project_id = ?
                """, (resultSet, rowNumber) -> new TaskAssignmentSnapshot(
                resultSet.getLong("assignee_membership_id"),
                resultSet.getLong("created_by_membership_id"),
                resultSet.getLong("assigned_by_membership_id"),
                resultSet.getString("status"),
                resultSet.getString("title")), projectId);
    }

    /** Snapshot tối thiểu của Task dùng riêng để kiểm thử bàn giao Leader. */
    private record TaskAssignmentSnapshot(
            long assigneeMembershipId,
            long creatorMembershipId,
            long assignerMembershipId,
            String status,
            String title) {}

    private OffsetDateTime dbTime(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
