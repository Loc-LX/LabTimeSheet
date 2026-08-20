package com.lab.labtimesheet.feature.task.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.task.exception.TaskNotFoundException;
import com.lab.labtimesheet.feature.task.exception.TaskValidationException;
import com.lab.labtimesheet.feature.task.model.TaskStatus;
import com.lab.labtimesheet.feature.task.model.dto.CreateTaskCommand;
import com.lab.labtimesheet.feature.task.model.dto.TaskAssigneeChoice;
import com.lab.labtimesheet.feature.task.model.dto.TaskCommentView;
import com.lab.labtimesheet.feature.task.model.dto.TaskDetails;
import com.lab.labtimesheet.feature.task.model.dto.TaskListView;
import com.lab.labtimesheet.feature.task.model.dto.TaskView;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class TaskCreationIntegrationTest {

    private static final LocalDate PROJECT_START = LocalDate.of(2026, 8, 1);
    private static final LocalDate PROJECT_END = LocalDate.of(2026, 8, 31);

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private TaskService taskService;

    @Autowired
    private TaskQueryService taskQueries;

    @Autowired
    private TaskDashboardService taskDashboard;

    private long projectId;
    private long leaderMembershipId;
    private long memberMembershipId;

    @BeforeEach
    void setUpProject() {
        long mentorId = insertUser("mentor@example.test", "MENTOR");
        long leaderId = insertIntern("leader@example.test");
        long memberId = insertIntern("member@example.test");
        projectId = insertProject(mentorId, "PLANNED");
        leaderMembershipId = insertMembership(projectId, leaderId, mentorId);
        memberMembershipId = insertMembership(projectId, memberId, mentorId);
        jdbc.sql("""
                        insert into project_leadership_terms
                            (project_id, membership_id, appointed_by_mentor_user_id)
                        values (:projectId, :membershipId, :mentorId)
                        """)
                .param("projectId", projectId)
                .param("membershipId", leaderMembershipId)
                .param("mentorId", mentorId)
                .update();
    }

    @Test
    void activeMemberCreatesOnlyASelfAssignedTaskWithEqualActors() {
        TaskView task = taskService.create(
                "member@example.test",
                new CreateTaskCommand(projectId, memberMembershipId, "  Draft results  ", "  notes  ", PROJECT_START));

        assertThat(task.status()).isEqualTo(TaskStatus.TODO);
        assertThat(task.title()).isEqualTo("Draft results");
        assertThat(task.description()).isEqualTo("notes");
        assertThat(task.creatorMembershipId()).isEqualTo(memberMembershipId);
        assertThat(task.assignerMembershipId()).isEqualTo(memberMembershipId);
        assertThat(task.assigneeMembershipId()).isEqualTo(memberMembershipId);
        assertThat(task.assigneeName()).isEqualTo("member@example.test");

        assertThatThrownBy(() -> taskService.create(
                        "member@example.test",
                        new CreateTaskCommand(projectId, leaderMembershipId, "Forbidden", null, null)))
                .isInstanceOf(TaskNotFoundException.class);
        assertThat(taskCount()).isEqualTo(1);
    }

    @Test
    void currentLeaderCreatesForAnotherActiveSameProjectMember() {
        TaskView task = taskService.create(
                "leader@example.test",
                new CreateTaskCommand(projectId, memberMembershipId, "Review results", null, PROJECT_END));

        assertThat(task.creatorMembershipId()).isEqualTo(leaderMembershipId);
        assertThat(task.assignerMembershipId()).isEqualTo(leaderMembershipId);
        assertThat(task.assigneeMembershipId()).isEqualTo(memberMembershipId);
    }

    @Test
    void rejectsCrossProjectAndInactiveAssigneesWithoutWriting() {
        long mentorId = userId("mentor@example.test");
        long outsiderId = insertIntern("outsider@example.test");
        long otherProjectId = insertProject(mentorId, "PLANNED");
        long otherMembershipId = insertMembership(otherProjectId, outsiderId, mentorId);
        jdbc.sql("update project_memberships set left_at = joined_at + interval '1 second', removed_by_mentor_user_id = :mentorId where id = :id")
                .param("mentorId", mentorId)
                .param("id", memberMembershipId)
                .update();

        assertThatThrownBy(() -> taskService.create(
                        "leader@example.test",
                        new CreateTaskCommand(projectId, otherMembershipId, "Cross project", null, null)))
                .isInstanceOf(TaskNotFoundException.class);
        assertThatThrownBy(() -> taskService.create(
                        "leader@example.test",
                        new CreateTaskCommand(projectId, memberMembershipId, "Inactive", null, null)))
                .isInstanceOf(TaskNotFoundException.class);
        assertThat(taskCount()).isZero();
    }

    @Test
    void acceptsProjectBoundaryDueDatesAndRejectsOutsideOrCurrentDayOff() {
        taskService.create(
                "leader@example.test",
                new CreateTaskCommand(projectId, memberMembershipId, "Start boundary", null, PROJECT_START));
        taskService.create(
                "leader@example.test",
                new CreateTaskCommand(projectId, memberMembershipId, "End boundary", null, PROJECT_END));
        insertDayOff(LocalDate.of(2026, 8, 15));

        assertThatThrownBy(() -> taskService.create(
                        "leader@example.test",
                        new CreateTaskCommand(projectId, memberMembershipId, "Before", null, PROJECT_START.minusDays(1))))
                .isInstanceOf(TaskValidationException.class);
        assertThatThrownBy(() -> taskService.create(
                        "leader@example.test",
                        new CreateTaskCommand(projectId, memberMembershipId, "After", null, PROJECT_END.plusDays(1))))
                .isInstanceOf(TaskValidationException.class);
        assertThatThrownBy(() -> taskService.create(
                        "leader@example.test",
                        new CreateTaskCommand(projectId, memberMembershipId, "Day off", null, LocalDate.of(2026, 8, 15))))
                .isInstanceOf(TaskValidationException.class);
        assertThat(taskCount()).isEqualTo(2);
    }

    @Test
    void onlyCurrentAssigneeChangesStatusOnAnActiveProject() {
        TaskView task = taskService.create(
                "member@example.test",
                new CreateTaskCommand(projectId, memberMembershipId, "Run experiment", null, null));

        assertThatThrownBy(() -> taskService.changeStatus(
                        "member@example.test", projectId, task.id(), TaskStatus.IN_PROGRESS))
                .isInstanceOf(TaskNotFoundException.class);
        activateProject();
        assertThatThrownBy(() -> taskService.changeStatus(
                        "leader@example.test", projectId, task.id(), TaskStatus.IN_PROGRESS))
                .isInstanceOf(TaskNotFoundException.class);

        TaskView inProgress = taskService.changeStatus(
                "member@example.test", projectId, task.id(), TaskStatus.IN_PROGRESS);

        assertThat(inProgress.status()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThatThrownBy(() -> taskService.changeStatus(
                        "member@example.test", projectId, task.id(), TaskStatus.TODO))
                .isInstanceOf(TaskValidationException.class);
    }

    @Test
    void activeMemberAndOwningMentorAppendCommentsUntilProjectCompletion() {
        TaskView task = taskService.create(
                "member@example.test",
                new CreateTaskCommand(projectId, memberMembershipId, "Discuss results", null, null));
        insertIntern("outsider@example.test");

        TaskCommentView memberComment = taskService.addComment(
                "member@example.test", projectId, task.id(), "  First note  ");
        TaskCommentView mentorComment = taskService.addComment(
                "mentor@example.test", projectId, task.id(), "Mentor note");

        assertThat(memberComment.body()).isEqualTo("First note");
        assertThat(mentorComment.authorUserId()).isEqualTo(userId("mentor@example.test"));
        assertThatThrownBy(() -> taskService.addComment(
                        "outsider@example.test", projectId, task.id(), "Forbidden"))
                .isInstanceOf(TaskNotFoundException.class);
        assertThatThrownBy(() -> taskService.addComment(
                        "member@example.test", projectId, task.id(), "  "))
                .isInstanceOf(TaskValidationException.class);

        completeProject();
        assertThatThrownBy(() -> taskService.addComment(
                        "mentor@example.test", projectId, task.id(), "Too late"))
                .isInstanceOf(TaskNotFoundException.class);
        assertThat(commentCount()).isEqualTo(2);
    }

    @Test
    void authorizedListsAndDetailsExcludeDeletedTasksAndReportEmptyAsNotApplicable() {
        TaskView todo = createMemberTask("Todo");
        TaskView active = createMemberTask("Active");
        TaskView blocked = createMemberTask("Blocked");
        TaskView done = createMemberTask("Done");
        TaskView deleted = createMemberTask("Deleted");
        setStatus(active.id(), TaskStatus.IN_PROGRESS);
        setStatus(blocked.id(), TaskStatus.BLOCKED);
        setStatus(done.id(), TaskStatus.DONE);
        softDelete(deleted.id());
        taskService.addComment("member@example.test", projectId, todo.id(), "Visible comment");

        TaskListView list = taskService.list("member@example.test", projectId);
        TaskDetails details = taskService.details("mentor@example.test", projectId, todo.id());

        assertThat(list.tasks()).extracting(TaskView::title)
                .containsExactly("Todo", "Active", "Blocked", "Done");
        assertThat(list.tasks()).extracting(TaskView::assigneeName)
                .containsOnly("member@example.test");
        assertThat(list.canCreate()).isTrue();
        assertThat(list.progress().total()).isEqualTo(4);
        assertThat(list.progress().count(TaskStatus.TODO)).isEqualTo(1);
        assertThat(list.progress().count(TaskStatus.IN_PROGRESS)).isEqualTo(1);
        assertThat(list.progress().count(TaskStatus.BLOCKED)).isEqualTo(1);
        assertThat(list.progress().count(TaskStatus.DONE)).isEqualTo(1);
        assertThat(list.progress().completionPercentage()).hasValue(25.0);
        assertThat(details.comments()).extracting(TaskCommentView::body).containsExactly("Visible comment");
        assertThat(details.task().assigneeName()).isEqualTo("member@example.test");
        assertThat(details.canChangeStatus()).isFalse();
        assertThat(details.canComment()).isTrue();

        long emptyProjectId = insertProject(userId("mentor@example.test"), "PLANNED");
        long emptyLeaderMembershipId = insertMembership(
                emptyProjectId,
                userId("leader@example.test"),
                userId("mentor@example.test"));
        jdbc.sql("""
                        insert into project_leadership_terms
                            (project_id, membership_id, appointed_by_mentor_user_id)
                        values (:projectId, :membershipId, :mentorId)
                        """)
                .param("projectId", emptyProjectId)
                .param("membershipId", emptyLeaderMembershipId)
                .param("mentorId", userId("mentor@example.test"))
                .update();
        assertThat(taskService.list("mentor@example.test", emptyProjectId).progress().completionPercentage())
                .isEmpty();
    }

    @Test
    void directAndCrossProjectTaskIdentifiersDoNotDiscloseRecords() {
        TaskView task = createMemberTask("Private task");
        long otherProjectId = insertProject(userId("mentor@example.test"), "PLANNED");

        assertThatThrownBy(() -> taskService.details(
                        "mentor@example.test", otherProjectId, task.id()))
                .isInstanceOf(TaskNotFoundException.class);
        assertThatThrownBy(() -> taskService.details(
                        "outsider@example.test", projectId, task.id()))
                .isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    void formerMemberReadsOnlyCompletedProjectTaskHistory() {
        TaskView task = createMemberTask("Historical task");
        closeMembership(memberMembershipId);

        assertThatThrownBy(() -> taskService.list("member@example.test", projectId))
                .isInstanceOf(TaskNotFoundException.class);
        assertThatThrownBy(() -> taskService.details("member@example.test", projectId, task.id()))
                .isInstanceOf(TaskNotFoundException.class);

        completeProject();

        assertThat(currentLeadershipCount()).isZero();
        assertThat(currentMembershipCount()).isZero();

        assertThat(taskService.list("member@example.test", projectId).tasks())
                .extracting(TaskView::title)
                .containsExactly("Historical task");
        assertThat(taskService.details("member@example.test", projectId, task.id()).task().title())
                .isEqualTo("Historical task");
    }

    @Test
    void viewCapabilitiesFollowCurrentMembershipAssignmentAndProjectLifecycle() {
        TaskView task = createMemberTask("Capability task");

        assertThat(taskService.list("member@example.test", projectId).canCreate()).isTrue();
        assertThat(taskService.list("mentor@example.test", projectId).canCreate()).isFalse();
        assertThat(taskService.details("member@example.test", projectId, task.id()))
                .satisfies(details -> {
                    assertThat(details.canChangeStatus()).isFalse();
                    assertThat(details.canComment()).isTrue();
                });

        activateProject();

        assertThat(taskService.details("member@example.test", projectId, task.id()))
                .satisfies(details -> {
                    assertThat(details.canChangeStatus()).isTrue();
                    assertThat(details.canComment()).isTrue();
                });
        assertThat(taskService.details("leader@example.test", projectId, task.id()))
                .satisfies(details -> {
                    assertThat(details.canChangeStatus()).isFalse();
                    assertThat(details.canReassign()).isTrue();
                });

        completeProject();

        assertThat(taskService.list("member@example.test", projectId).canCreate()).isFalse();
        assertThat(taskService.details("member@example.test", projectId, task.id()))
                .satisfies(details -> {
                    assertThat(details.canChangeStatus()).isFalse();
                    assertThat(details.canComment()).isFalse();
                });
    }

    /** [I2-PRJ-06] Thành viên cũ đọc được Task đã lưu nhưng không thể ghi thêm sau khi Project hoàn tất. */
    @Test
    void completedProjectHistoryRejectsFormerMemberTaskMutations() {
        TaskView task = createMemberTask("Read-only Task");
        completeProject();

        // I2-PRJ-06: các endpoint ghi phải bị chặn ở service, không phụ thuộc việc UI có ẩn form hay không.
        assertThatThrownBy(() -> taskService.create(
                        "member@example.test",
                        new CreateTaskCommand(projectId, memberMembershipId, "Rejected", null, null)))
                .isInstanceOf(TaskNotFoundException.class);
        assertThatThrownBy(() -> taskService.changeStatus(
                        "member@example.test", projectId, task.id(), TaskStatus.IN_PROGRESS))
                .isInstanceOf(TaskNotFoundException.class);
        assertThatThrownBy(() -> taskService.addComment(
                        "member@example.test", projectId, task.id(), "Rejected comment"))
                .isInstanceOf(TaskNotFoundException.class);

        assertThat(taskCount()).isEqualTo(1);
        assertThat(commentCount()).isZero();
        assertThat(jdbc.sql("select status from tasks where id = :id")
                .param("id", task.id())
                .query(String.class)
                .single()).isEqualTo(TaskStatus.DONE.name());
    }

    @Test
    void createFormChoicesAreSelfOnlyForMembersAndAllActiveMembersForLeader() {
        assertThat(taskService.assignmentChoices("member@example.test", projectId))
                .extracting(TaskAssigneeChoice::membershipId)
                .containsExactly(memberMembershipId);
        assertThat(taskService.assignmentChoices("leader@example.test", projectId))
                .extracting(TaskAssigneeChoice::membershipId)
                .containsExactly(leaderMembershipId, memberMembershipId);
    }

    @Test
    void projectActivationQueryCountsOnlyCurrentTasksOutsideActiveMemberships() {
        createMemberTask("Member task");
        TaskView leaderTask = taskService.create(
                "leader@example.test",
                new CreateTaskCommand(projectId, leaderMembershipId, "Leader task", null, null));

        assertThat(taskQueries.countCurrentTasksAssignedOutside(projectId, Set.of(memberMembershipId)))
                .isEqualTo(1L);

        softDelete(leaderTask.id());
        assertThat(taskQueries.countCurrentTasksAssignedOutside(projectId, Set.of(memberMembershipId)))
                .isZero();
    }

    @Test
    void internDashboardCountsAssignmentsAndOrdersFivePriorityTasks() {
        createMemberTask("Late");
        taskService.create("member@example.test", new CreateTaskCommand(
                projectId, memberMembershipId, "No due date", null, null));
        taskService.create("member@example.test", new CreateTaskCommand(
                projectId, memberMembershipId, "Earliest A", null, LocalDate.of(2026, 8, 10)));
        taskService.create("member@example.test", new CreateTaskCommand(
                projectId, memberMembershipId, "Earliest B", null, LocalDate.of(2026, 8, 10)));
        taskService.create("member@example.test", new CreateTaskCommand(
                projectId, memberMembershipId, "Middle", null, LocalDate.of(2026, 8, 11)));
        taskService.create("member@example.test", new CreateTaskCommand(
                projectId, memberMembershipId, "Next", null, LocalDate.of(2026, 8, 13)));
        setDueDateForTitle("Late", LocalDate.of(2026, 8, 12));
        activateProject();

        var dashboard = taskDashboard.dashboard("member@example.test");

        assertThat(dashboard.assignedTaskCount()).isEqualTo(6L);
        assertThat(dashboard.priorityTasks())
                .extracting(task -> task.title())
                .containsExactly("Earliest A", "Earliest B", "Middle", "Late", "Next");
    }

    private long insertUser(String email, String role) {
        return jdbc.sql("""
                        insert into app_users
                            (email, display_name, password_hash, global_role, account_status, activated_at)
                        values (:email, :email, 'hash', :role, 'ACTIVE', current_timestamp)
                        returning id
                        """)
                .param("email", email)
                .param("role", role)
                .query(Long.class)
                .single();
    }

    private long insertIntern(String email) {
        long userId = insertUser(email, "INTERN");
        jdbc.sql("""
                        insert into intern_profiles
                            (user_id, student_code, internship_start_date, internship_end_date,
                             internship_status, activated_at)
                        values (:userId, :studentCode, date '2026-01-01', date '2026-12-31',
                                'ACTIVE', current_timestamp)
                        """)
                .param("userId", userId)
                .param("studentCode", "S" + userId)
                .update();
        return userId;
    }

    private long insertProject(long mentorId, String status) {
        return jdbc.sql("""
                        insert into projects
                            (mentor_user_id, name, status, start_date, end_date, activated_at)
                        values (:mentorId, 'Project', :status, :startDate, :endDate,
                                case when :status = 'ACTIVE' then current_timestamp else null end)
                        returning id
                        """)
                .param("mentorId", mentorId)
                .param("status", status)
                .param("startDate", PROJECT_START)
                .param("endDate", PROJECT_END)
                .query(Long.class)
                .single();
    }

    private long insertMembership(long targetProjectId, long internId, long mentorId) {
        return jdbc.sql("""
                        insert into project_memberships (project_id, intern_user_id, added_by_user_id)
                        values (:projectId, :internId, :mentorId)
                        returning id
                        """)
                .param("projectId", targetProjectId)
                .param("internId", internId)
                .param("mentorId", mentorId)
                .query(Long.class)
                .single();
    }

    private void insertDayOff(LocalDate date) {
        long mentorId = userId("mentor@example.test");
        jdbc.sql("""
                        insert into global_calendar_events
                            (calendar_date, name, source, is_day_off, created_by_user_id, updated_by_user_id)
                        values (:date, 'Day off', 'CUSTOM', true, :userId, :userId)
                        """)
                .param("date", date)
                .param("userId", mentorId)
                .update();
    }

    private long userId(String email) {
        return jdbc.sql("select id from app_users where email = :email")
                .param("email", email)
                .query(Long.class)
                .single();
    }

    private long taskCount() {
        return jdbc.sql("select count(*) from tasks").query(Long.class).single();
    }

    private long commentCount() {
        return jdbc.sql("select count(*) from task_comments").query(Long.class).single();
    }

    private long currentLeadershipCount() {
        return jdbc.sql("""
                        select count(*) from project_leadership_terms
                        where project_id = :projectId and ended_at is null
                        """)
                .param("projectId", projectId)
                .query(Long.class)
                .single();
    }

    private long currentMembershipCount() {
        return jdbc.sql("""
                        select count(*) from project_memberships
                        where project_id = :projectId and left_at is null
                        """)
                .param("projectId", projectId)
                .query(Long.class)
                .single();
    }

    private TaskView createMemberTask(String title) {
        return taskService.create(
                "member@example.test",
                new CreateTaskCommand(projectId, memberMembershipId, title, null, null));
    }

    private void activateProject() {
        jdbc.sql("update projects set status = 'ACTIVE', activated_at = current_timestamp where id = :id")
                .param("id", projectId)
                .update();
        entityManager.clear();
    }

    private void completeProject() {
        long mentorId = userId("mentor@example.test");
        jdbc.sql("""
                        update tasks
                        set status = 'DONE'
                        where project_id = :id and deleted_at is null
                        """)
                .param("id", projectId)
                .update();
        jdbc.sql("""
                        update project_leadership_terms
                        set ended_at = started_at + interval '1 second', ended_by_mentor_user_id = :mentorId
                        where project_id = :id and ended_at is null
                        """)
                .param("id", projectId)
                .param("mentorId", mentorId)
                .update();
        jdbc.sql("""
                        update project_memberships
                        set left_at = joined_at + interval '1 second', removed_by_mentor_user_id = :mentorId
                        where project_id = :id and left_at is null
                        """)
                .param("id", projectId)
                .param("mentorId", mentorId)
                .update();
        jdbc.sql("""
                        update projects
                        set status = 'COMPLETED', activated_at = current_timestamp,
                            completed_at = current_timestamp
                        where id = :id
                        """)
                .param("id", projectId)
                .update();
        entityManager.clear();
    }

    private void setStatus(long taskId, TaskStatus status) {
        jdbc.sql("update tasks set status = :status where id = :id")
                .param("status", status.name())
                .param("id", taskId)
                .update();
        entityManager.clear();
    }

    private void softDelete(long taskId) {
        jdbc.sql("""
                        update tasks
                        set deleted_at = current_timestamp, deleted_by_membership_id = :membershipId
                        where id = :id
                        """)
                .param("membershipId", memberMembershipId)
                .param("id", taskId)
                .update();
        entityManager.clear();
    }

    private void closeMembership(long membershipId) {
        jdbc.sql("""
                        update project_memberships
                        set left_at = joined_at + interval '1 second', removed_by_mentor_user_id = :mentorId
                        where id = :id
                        """)
                .param("mentorId", userId("mentor@example.test"))
                .param("id", membershipId)
                .update();
        entityManager.clear();
    }

    private void setDueDateForTitle(String title, LocalDate dueDate) {
        jdbc.sql("update tasks set due_date = :dueDate where title = :title")
                .param("dueDate", dueDate)
                .param("title", title)
                .update();
        entityManager.clear();
    }
}
