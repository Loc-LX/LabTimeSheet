package com.lab.labtimesheet.feature.task.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.project.model.dto.ProjectTaskContext;
import com.lab.labtimesheet.feature.project.service.ProjectService;
import com.lab.labtimesheet.feature.task.exception.TaskNotFoundException;
import com.lab.labtimesheet.feature.task.exception.TaskValidationException;
import com.lab.labtimesheet.feature.task.model.TaskStatus;
import com.lab.labtimesheet.feature.task.model.dto.CreateTaskCommand;
import com.lab.labtimesheet.feature.task.model.dto.TaskAssigneeChoice;
import com.lab.labtimesheet.feature.task.model.dto.TaskCommentView;
import com.lab.labtimesheet.feature.task.model.dto.TaskDetails;
import com.lab.labtimesheet.feature.task.model.dto.TaskListView;
import com.lab.labtimesheet.feature.task.model.dto.TaskHistoryView;
import com.lab.labtimesheet.feature.task.model.dto.TaskProjectProgress;
import com.lab.labtimesheet.feature.task.model.dto.TaskView;
import com.lab.labtimesheet.feature.task.model.entity.TaskWorkLog;
import com.lab.labtimesheet.feature.task.repository.TaskWorkLogRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
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

    @Autowired
    private TaskWorkLogRepository taskWorkLogs;

    @Autowired
    private ProjectService projectMutations;

    @Autowired
    private TaskTransferService taskTransfers;

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
        assertThat(notificationCount()).isZero();
    }

    @Test
    void leaderAssignmentNotifiesOnlyNewAssigneeWithUnavailableEmail() {
        TaskView task = taskService.create(
                "leader@example.test",
                new CreateTaskCommand(projectId, memberMembershipId, "Notify assignee", null, null));

        assertThat(task.assigneeMembershipId()).isEqualTo(memberMembershipId);
        assertThat(notificationRecipientIds()).containsExactly(userId("member@example.test"));
        assertThat(notificationTypes()).containsExactly("TASK_ASSIGNED");
        assertThat(notificationEmailStatuses()).containsExactly("UNAVAILABLE");
        assertThat(notificationActionUrls()).containsExactly(
                "/projects/%d/tasks/%d".formatted(projectId, task.id()));
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
        assertThat(notificationRecipientIds()).containsExactly(userId("leader@example.test"));
        assertThat(notificationTypes()).containsExactly("TASK_STATUS_CHANGED");
        assertThat(notificationEmailStatuses()).containsExactly("NOT_REQUIRED");
        assertThat(notificationActionUrls()).containsExactly(
                "/projects/%d/tasks/%d".formatted(projectId, task.id()));
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

        TaskView leaderTask = taskService.create(
                "leader@example.test",
                new CreateTaskCommand(projectId, leaderMembershipId, "Leader task", null, null));
        taskService.addComment("member@example.test", projectId, leaderTask.id(), "Member on leader task");

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
        assertThat(commentCount()).isEqualTo(3);
        assertThat(notificationRecipientIds()).containsExactly(
                userId("leader@example.test"),
                userId("member@example.test"),
                userId("leader@example.test"),
                userId("leader@example.test"));
        assertThat(notificationTypes()).containsExactly(
                "TASK_COMMENTED", "TASK_COMMENTED", "TASK_COMMENTED", "TASK_COMMENTED");
        assertThat(notificationEmailStatuses()).containsExactly(
                "NOT_REQUIRED", "NOT_REQUIRED", "NOT_REQUIRED", "NOT_REQUIRED");
        assertThat(notificationActionUrls()).containsExactly(
                "/projects/%d/tasks/%d".formatted(projectId, task.id()),
                "/projects/%d/tasks/%d".formatted(projectId, task.id()),
                "/projects/%d/tasks/%d".formatted(projectId, task.id()),
                "/projects/%d/tasks/%d".formatted(projectId, leaderTask.id()));
    }

    @Test
    void authorizedMentorCanCommentOnDoneTaskRetainingClosedAssigneeHistory() {
        TaskView task = createMemberTask("Closed assignee history");
        setStatus(task.id(), TaskStatus.DONE);
        closeMembership(memberMembershipId);

        TaskCommentView comment = taskService.addComment(
                "mentor@example.test", projectId, task.id(), "Mentor note after removal");

        assertThat(comment.body()).isEqualTo("Mentor note after removal");
        assertThat(commentCount()).isEqualTo(1);
        assertThat(notificationRecipientIds()).containsExactly(userId("leader@example.test"));
        assertThat(notificationTypes()).containsExactly("TASK_COMMENTED");
        assertThat(notificationEmailStatuses()).containsExactly("NOT_REQUIRED");
        assertThat(notificationActionUrls()).containsExactly(
                "/projects/%d/tasks/%d".formatted(projectId, task.id()));
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
    void creatorMayEditOnlyWhileStillCurrentAssigneeAndLeaderMayEditAnyUnfinishedTask() {
        TaskView task = createMemberTask("Original");
        TaskDetails initialDetails = taskService.details("member@example.test", projectId, task.id());
        assertThat(initialDetails.canEdit()).isTrue();
        assertThat(initialDetails.canDelete()).isTrue();

        TaskView edited = taskService.edit(
                "member@example.test", projectId, task.id(), "Updated", "Details", PROJECT_END);

        assertThat(edited.title()).isEqualTo("Updated");
        assertThat(edited.description()).isEqualTo("Details");
        assertThat(edited.dueDate()).isEqualTo(PROJECT_END);

        TaskView reassignedAway = taskService.reassign(
                "leader@example.test", projectId, task.id(), leaderMembershipId);
        assertThat(reassignedAway.assignerMembershipId()).isEqualTo(leaderMembershipId);
        assertThat(reassignedAway.assignedAt()).isEqualTo(task.assignedAt());
        assertThatThrownBy(() -> taskService.edit(
                        "member@example.test", projectId, task.id(), "Denied", null, null))
                .isInstanceOf(TaskNotFoundException.class);
        TaskDetails afterAway = taskService.details("member@example.test", projectId, task.id());
        assertThat(afterAway.canEdit()).isFalse();
        assertThat(afterAway.canDelete()).isFalse();

        TaskView reassignedBack = taskService.reassign(
                "leader@example.test", projectId, task.id(), memberMembershipId);
        assertThat(reassignedBack.assigneeMembershipId()).isEqualTo(memberMembershipId);
        assertThat(reassignedBack.assignerMembershipId()).isEqualTo(leaderMembershipId);
        assertThat(reassignedBack.assignedAt()).isEqualTo(reassignedAway.assignedAt());
        TaskDetails afterBack = taskService.details("member@example.test", projectId, task.id());
        assertThat(afterBack.canEdit()).isTrue();
        assertThat(afterBack.canDelete()).isTrue();

        TaskView editedBack = taskService.edit(
                "member@example.test", projectId, task.id(), "Creator edit restored", null, null);
        assertThat(editedBack.title()).isEqualTo("Creator edit restored");
        taskService.softDelete("member@example.test", projectId, task.id());

        Instant persistedAssignmentAt = jdbc.sql("select assigned_at from tasks where id = :id")
                .param("id", task.id())
                .query(Instant.class)
                .single();
        TaskHistoryView retained = taskQueries.history(projectId).stream()
                .filter(history -> history.id() == task.id())
                .findFirst()
                .orElseThrow();
        assertThat(retained.assigneeMembershipId()).isEqualTo(memberMembershipId);
        assertThat(retained.assignerMembershipId()).isEqualTo(leaderMembershipId);
        assertThat(retained.assignedAt()).isEqualTo(reassignedBack.assignedAt());
        assertThat(retained.assignedAt()).isEqualTo(persistedAssignmentAt);
        assertThat(retained.deletedByMembershipId()).isEqualTo(memberMembershipId);
    }

    @Test
    void reassignmentNotifiesPreviousAndNewAssigneeExactlyOnce() {
        TaskView task = createMemberTask("Reassignment notice");

        taskService.reassign("leader@example.test", projectId, task.id(), leaderMembershipId);

        assertThat(notificationRecipientIds()).containsExactly(
                userId("member@example.test"), userId("leader@example.test"));
        assertThat(notificationTypes()).containsExactly("TASK_REASSIGNED", "TASK_REASSIGNED");
        assertThat(notificationEmailStatuses()).containsExactly("UNAVAILABLE", "UNAVAILABLE");
        assertThat(notificationActionUrls()).containsExactly(
                "/projects/%d/tasks/%d".formatted(projectId, task.id()),
                "/projects/%d/tasks/%d".formatted(projectId, task.id()));
    }

    @Test
    void softDeleteExcludesTaskFromCurrentViewsButRetainsHistoricalRow() {
        TaskView task = createMemberTask("Retain me");

        taskService.softDelete("member@example.test", projectId, task.id());

        assertThat(taskService.list("member@example.test", projectId).tasks()).isEmpty();
        assertThat(jdbc.sql("select count(*) from tasks where id = :id and deleted_at is not null")
                .param("id", task.id()).query(Long.class).single()).isEqualTo(1L);
        assertThatThrownBy(() -> taskService.details("member@example.test", projectId, task.id()))
                .isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    void unfinishedReassignmentPreservesCreatorStatusAndCreationAtAndDoneRequiresReopen() {
        TaskView task = createMemberTask("Transfer me");
        setStatus(task.id(), TaskStatus.IN_PROGRESS);
        Instant createdAt = task.createdAt();

        TaskView reassigned = taskService.reassign(
                "leader@example.test", projectId, task.id(), leaderMembershipId);

        assertThat(reassigned.assigneeMembershipId()).isEqualTo(leaderMembershipId);
        assertThat(reassigned.creatorMembershipId()).isEqualTo(memberMembershipId);
        assertThat(reassigned.status()).isEqualTo(TaskStatus.IN_PROGRESS);
        assertThat(reassigned.createdAt()).isEqualTo(createdAt);

        setStatus(task.id(), TaskStatus.DONE);
        assertThatThrownBy(() -> taskService.reassign(
                        "leader@example.test", projectId, task.id(), memberMembershipId))
                .isInstanceOf(TaskValidationException.class);
    }

    @Test
    void projectProgressAndHistoryReadPersistedWorkAndRetainedDeletedRows() {
        TaskView deleted = createMemberTask("Deleted effort");
        taskService.softDelete("member@example.test", projectId, deleted.id());
        TaskView done = createMemberTask("Done effort");
        setStatus(done.id(), TaskStatus.DONE);
        TaskView retainedCurrent = createMemberTask("Retained current effort");
        taskService.addComment("member@example.test", projectId, retainedCurrent.id(), "Retained note");

        taskWorkLogs.saveAndFlush(new TaskWorkLog(
                projectId,
                deleted.id(),
                memberMembershipId,
                LocalDate.of(2026, 8, 20),
                60,
                "Deleted effort",
                Instant.parse("2026-08-20T01:00:00Z")));
        taskWorkLogs.saveAndFlush(new TaskWorkLog(
                projectId,
                retainedCurrent.id(),
                memberMembershipId,
                LocalDate.of(2026, 8, 20),
                120,
                "Current effort",
                Instant.parse("2026-08-20T02:00:00Z")));

        TaskProjectProgress progress = taskQueries.projectProgress(projectId);
        assertThat(progress.todo()).isEqualTo(1L);
        assertThat(progress.done()).isEqualTo(1L);
        assertThat(progress.totalTasks()).isEqualTo(2L);
        assertThat(progress.totalMinutes()).isEqualTo(180L);
        assertThat(progress.completionPercentage()).hasValue(50.0);

        List<TaskHistoryView> history = taskQueries.history(projectId);
        assertThat(history).extracting(TaskHistoryView::title)
                .containsExactly("Deleted effort", "Done effort", "Retained current effort");
        assertThat(history.get(0).deletedAt()).isNotNull();
        assertThat(history.get(0).workLogs()).hasSize(1);
        assertThat(history.get(2).comments()).extracting(TaskCommentView::body)
                .containsExactly("Retained note");
    }

    @Test
    void projectProgressAggregateReturnsEmptyDenominatorForNoCurrentTasks() {
        TaskProjectProgress progress = taskQueries.projectProgress(projectId);

        assertThat(progress.todo()).isZero();
        assertThat(progress.inProgress()).isZero();
        assertThat(progress.blocked()).isZero();
        assertThat(progress.done()).isZero();
        assertThat(progress.totalMinutes()).isZero();
        assertThat(progress.completionPercentage()).isEmpty();
    }

    @Test
    void directTransferAllUnfinishedUsesOrderedIdProjectionAndLeavesDoneTasksUntouched() {
        TaskView first = createMemberTask("First unfinished");
        TaskView second = createMemberTask("Second unfinished");
        TaskView done = createMemberTask("Done retained");
        setStatus(done.id(), TaskStatus.DONE);

        ProjectTaskContext context = projectMutations.taskMutationContext(
                userId("leader@example.test"), projectId);
        TaskTransferResult result = taskTransfers.transferAllUnfinished(
                context, leaderMembershipId, memberMembershipId, leaderMembershipId);

        assertThat(result.transferredTaskCount()).isEqualTo(2L);
        assertThat(result.recipientMembershipId()).isEqualTo(leaderMembershipId);
        assertThat(jdbc.sql("""
                        select count(*) from tasks
                        where project_id = :projectId
                          and assignee_membership_id = :leaderMembershipId
                          and status <> 'DONE' and deleted_at is null
                        """)
                .param("projectId", projectId)
                .param("leaderMembershipId", leaderMembershipId)
                .query(Long.class)
                .single()).isEqualTo(2L);
        assertThat(jdbc.sql("select assignee_membership_id from tasks where id = :id")
                .param("id", done.id())
                .query(Long.class)
                .single()).isEqualTo(memberMembershipId);
        assertThat(jdbc.sql("select count(*) from tasks where id in (:first, :second)")
                .param("first", first.id())
                .param("second", second.id())
                .query(Long.class)
                .single()).isEqualTo(2L);
        assertThat(notificationRecipientIds()).containsExactly(
                userId("member@example.test"), userId("leader@example.test"),
                userId("member@example.test"), userId("leader@example.test"));
        assertThat(notificationTypes()).containsExactly(
                "TASK_REASSIGNED", "TASK_REASSIGNED", "TASK_REASSIGNED", "TASK_REASSIGNED");
        assertThat(notificationEmailStatuses()).containsExactly(
                "UNAVAILABLE", "UNAVAILABLE", "UNAVAILABLE", "UNAVAILABLE");
        assertThat(notificationActionUrls()).containsExactly(
                "/projects/%d/tasks/%d".formatted(projectId, first.id()),
                "/projects/%d/tasks/%d".formatted(projectId, first.id()),
                "/projects/%d/tasks/%d".formatted(projectId, second.id()),
                "/projects/%d/tasks/%d".formatted(projectId, second.id()));
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

    private long notificationCount() {
        return jdbc.sql("select count(*) from notifications").query(Long.class).single();
    }

    private List<Long> notificationRecipientIds() {
        return jdbc.sql("select recipient_user_id from notifications order by id")
                .query(Long.class)
                .list();
    }

    private List<String> notificationTypes() {
        return jdbc.sql("select notification_type from notifications order by id")
                .query(String.class)
                .list();
    }

    private List<String> notificationEmailStatuses() {
        return jdbc.sql("select email_status from notifications order by id")
                .query(String.class)
                .list();
    }

    private List<String> notificationActionUrls() {
        return jdbc.sql("select action_url from notifications order by id")
                .query(String.class)
                .list();
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
