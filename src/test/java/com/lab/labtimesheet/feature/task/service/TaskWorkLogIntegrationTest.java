package com.lab.labtimesheet.feature.task.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.identity.service.AccountService;
import com.lab.labtimesheet.feature.project.model.dto.ProjectTaskContext;
import com.lab.labtimesheet.feature.project.service.ProjectService;
import com.lab.labtimesheet.feature.task.exception.TaskConflictException;
import com.lab.labtimesheet.feature.task.exception.TaskNotFoundException;
import com.lab.labtimesheet.feature.task.exception.TaskValidationException;
import com.lab.labtimesheet.feature.task.model.TaskStatus;
import com.lab.labtimesheet.feature.task.model.dto.CreateTaskCommand;
import com.lab.labtimesheet.feature.task.model.dto.RemainingEffortForecastInput;
import com.lab.labtimesheet.feature.task.model.dto.TaskView;
import com.lab.labtimesheet.feature.task.model.dto.TaskWorkLogView;
import com.lab.labtimesheet.feature.task.model.entity.TaskRemainingEffortForecast;
import com.lab.labtimesheet.feature.task.model.entity.TaskWorkLog;
import com.lab.labtimesheet.feature.task.repository.TaskRemainingEffortForecastRepository;
import com.lab.labtimesheet.feature.task.repository.TaskWorkLogRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * PostgreSQL behavior proof for Task work-log authorization, date boundaries, daily limits, and
 * account-lock serialization.
 *
 * <p>Fixtures use two Projects for one Intern so production can derive the authoritative
 * cross-Project retained membership intervals through the Project DTO boundary without importing
 * Project persistence into Task code. Direct fixture IDs are used only when asserting persisted
 * totals.</p>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class TaskWorkLogIntegrationTest {

    private static final LocalDate WORK_DATE = LocalDate.of(2026, 8, 14);
    private static final AtomicLong SEQUENCE = new AtomicLong();

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private TaskService taskService;

    @Autowired
    private AccountService accounts;

    @Autowired
    private ProjectService projectMutations;

    @Autowired
    private TaskTransferService taskTransfers;

    @Autowired
    private TaskWorkLogRepository workLogs;

    @Autowired
    private TaskRemainingEffortForecastRepository forecasts;

    @Autowired
    private TransactionTemplate transactions;

    private Fixture fixture;

    @BeforeEach
    void setUpFixture() {
        String suffix = Long.toString(SEQUENCE.incrementAndGet());
        long mentorId = insertUser("worklog-mentor-" + suffix + "@example.test", "MENTOR");
        long internId = insertIntern("worklog-intern-" + suffix + "@example.test");
        String otherEmail = "worklog-other-" + suffix + "@example.test";
        long otherInternId = insertIntern(otherEmail);
        long firstProjectId = insertProject(mentorId, "ACTIVE", suffix + "-one");
        long secondProjectId = insertProject(mentorId, "ACTIVE", suffix + "-two");
        long firstMembershipId = insertMembership(firstProjectId, internId, mentorId);
        long secondMembershipId = insertMembership(secondProjectId, internId, mentorId);
        long otherMembershipId = insertMembership(firstProjectId, otherInternId, mentorId);
        insertLeaderTerm(firstProjectId, firstMembershipId, mentorId);
        insertLeaderTerm(secondProjectId, secondMembershipId, mentorId);
        String email = "worklog-intern-" + suffix + "@example.test";
        long firstTaskId = insertTask(firstProjectId, firstMembershipId, firstMembershipId, "First task");
        long secondTaskId = insertTask(secondProjectId, secondMembershipId, secondMembershipId, "Second task");
        fixture = new Fixture(
                email,
                internId,
                firstProjectId,
                secondProjectId,
                firstMembershipId,
                secondMembershipId,
                firstTaskId,
                secondTaskId,
                otherEmail,
                otherMembershipId);
    }

    @Test
    void workLogRejectsDateOutsideInternshipAndCombinedDailyLimit() {
        assertThatThrownBy(() -> taskService.addWorkLog(
                        fixture.email(), fixture.firstProjectId(), fixture.firstTaskId(),
                        LocalDate.of(2027, 1, 1), 60, "Outside"))
                .isInstanceOf(TaskValidationException.class);

        TaskWorkLogView first = taskService.addWorkLog(
                fixture.email(), fixture.firstProjectId(), fixture.firstTaskId(),
                WORK_DATE, 900, "First");
        TaskWorkLogView second = taskService.addWorkLog(
                fixture.email(), fixture.secondProjectId(), fixture.secondTaskId(),
                WORK_DATE, 540, "Second");

        assertThat(first.minutes()).isEqualTo(900);
        assertThat(second.minutes()).isEqualTo(540);
        assertThat(workLogs.sumMinutesByMembershipIdsAndWorkDate(
                java.util.Set.of(fixture.firstMembershipId(), fixture.secondMembershipId()), WORK_DATE))
                .isEqualTo(1440L);
        assertThatThrownBy(() -> taskService.addWorkLog(
                fixture.email(), fixture.firstProjectId(), fixture.firstTaskId(),
                        WORK_DATE, 1, "Overflow"))
                .isInstanceOf(TaskValidationException.class);
    }

    @Test
    void workLogRejectsFutureProjectAndMembershipBoundaries() {
        assertThatThrownBy(() -> taskService.addWorkLog(
                        fixture.email(), fixture.firstProjectId(), fixture.firstTaskId(),
                        LocalDate.of(2026, 8, 15), 60, "Future"))
                .isInstanceOf(TaskValidationException.class);
        assertThatThrownBy(() -> taskService.addWorkLog(
                        fixture.email(), fixture.firstProjectId(), fixture.firstTaskId(),
                        LocalDate.of(2026, 7, 31), 60, "Before Project"))
                .isInstanceOf(TaskValidationException.class);
        assertThatThrownBy(() -> taskService.addWorkLog(
                        fixture.email(), fixture.firstProjectId(), fixture.firstTaskId(),
                        LocalDate.of(2026, 9, 1), 60, "After Project"))
                .isInstanceOf(TaskValidationException.class);

        jdbc.sql("""
                        update project_memberships
                        set joined_at = timestamp with time zone '2026-08-10 00:00:00+07'
                        where id = :membershipId
                        """)
                .param("membershipId", fixture.firstMembershipId())
                .update();
        assertThatThrownBy(() -> taskService.addWorkLog(
                        fixture.email(), fixture.firstProjectId(), fixture.firstTaskId(),
                        LocalDate.of(2026, 8, 9), 60, "Before membership"))
                .isInstanceOf(TaskValidationException.class);
    }

    @Test
    void workLogRejectsDateAfterRetainedMembershipClosure() {
        jdbc.sql("""
                        update project_memberships
                        set left_at = timestamp with time zone '2026-08-13 12:00:00+07',
                            removed_by_mentor_user_id = (
                                select mentor_user_id from projects where id = :projectId)
                        where id = :membershipId
                        """)
                .param("projectId", fixture.firstProjectId())
                .param("membershipId", fixture.firstMembershipId())
                .update();

        assertThatThrownBy(() -> taskService.addWorkLog(
                        fixture.email(), fixture.firstProjectId(), fixture.firstTaskId(),
                        WORK_DATE, 60, "After leave"))
                .isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    void retainedMembershipClosureDateRemainsInclusiveForCombinedTotal() {
        taskService.addWorkLog(
                fixture.email(), fixture.firstProjectId(), fixture.firstTaskId(),
                WORK_DATE, 60, "Before leave");

        jdbc.sql("""
                        update project_memberships
                        set left_at = timestamp with time zone '2026-08-14 12:00:00+07',
                            removed_by_mentor_user_id = (
                                select mentor_user_id from projects where id = :projectId)
                        where id = :membershipId
                        """)
                .param("projectId", fixture.firstProjectId())
                .param("membershipId", fixture.firstMembershipId())
                .update();

        TaskWorkLogView second = taskService.addWorkLog(
                fixture.email(), fixture.secondProjectId(), fixture.secondTaskId(),
                WORK_DATE, 1380, "Closure date");

        assertThat(second.minutes()).isEqualTo(1380);
        assertThat(workLogs.sumMinutesByMembershipIdsAndWorkDate(
                java.util.Set.of(fixture.firstMembershipId(), fixture.secondMembershipId()), WORK_DATE))
                .isEqualTo(1440L);
        assertThatThrownBy(() -> taskService.addWorkLog(
                        fixture.email(), fixture.secondProjectId(), fixture.secondTaskId(),
                        WORK_DATE, 1, "Overflow after closure date"))
                .isInstanceOf(TaskValidationException.class);
    }

    @Test
    void authorCorrectionRetainsStoredIdentityAndRejectsAnotherMember() {
        TaskWorkLogView original = taskService.addWorkLog(
                fixture.email(), fixture.firstProjectId(), fixture.firstTaskId(),
                WORK_DATE, 900, "Original");

        TaskView reassigned = taskService.reassign(
                fixture.email(), fixture.firstProjectId(), fixture.firstTaskId(), null,
                fixture.otherMembershipId(), new RemainingEffortForecastInput(90, "Handover"));
        assertThat(reassigned.assigneeMembershipId()).isEqualTo(fixture.otherMembershipId());

        TaskWorkLogView corrected = taskService.correctWorkLog(
                fixture.email(), fixture.firstProjectId(), original.id(),
                600, "Corrected");

        assertThat(corrected.id()).isEqualTo(original.id());
        assertThat(corrected.membershipId()).isEqualTo(fixture.firstMembershipId());
        assertThat(corrected.workDate()).isEqualTo(WORK_DATE);
        assertThat(corrected.createdAt()).isEqualTo(original.createdAt());
        assertThat(corrected.minutes()).isEqualTo(600);
        assertThat(corrected.note()).isEqualTo("Corrected");
        assertThatThrownBy(() -> taskService.correctWorkLog(
                        fixture.otherEmail(), fixture.firstProjectId(), original.id(),
                        600, "Forbidden"))
                .isInstanceOf(TaskNotFoundException.class);
    }

    @Test
    void forecastCorrectionAppendsSuccessorWithCurrentHistoryAndDetailMetadata() {
        long predecessorId = createForecastedTransfer();

        var correction = taskService.correctForecast(
                fixture.email(), fixture.firstProjectId(), fixture.firstTaskId(),
                predecessorId, 80, "  Scope changed  ");

        assertThat(correction.correction()).isTrue();
        assertThat(correction.correctionReason()).isEqualTo("Scope changed");
        assertThat(correction.supersedesForecastId()).isEqualTo(predecessorId);
        assertThat(correction.actualMinutesSnapshot()).isEqualTo(60L);
        assertThat(correction.forecastTotalMinutes()).isEqualTo(140L);
        assertThat(forecasts.findAllByProjectIdAndTaskIdOrderByAssignmentStartedAtAscCreatedAtAscIdAsc(
                fixture.firstProjectId(), fixture.firstTaskId())).hasSize(2);

        var details = taskService.details(
                fixture.email(), fixture.firstProjectId(), fixture.firstTaskId());
        assertThat(details.remainingEffortForecasts()).hasSize(2);
        assertThat(details.remainingEffortForecasts().get(0).id()).isEqualTo(predecessorId);
        assertThat(details.remainingEffortForecasts().get(0).superseded()).isTrue();
        assertThat(details.remainingEffortForecasts().get(0).remainingMinutes()).isEqualTo(90);
        assertThat(details.remainingEffortForecasts().get(1).id()).isEqualTo(correction.id());
        assertThat(details.remainingEffortForecasts().get(1).correctionReason())
                .isEqualTo("Scope changed");
        assertThat(details.remainingEffortForecasts().get(1).supersedesForecastId())
                .isEqualTo(predecessorId);
        assertThat(details.remainingEffortForecasts().get(1).forecastTotalMinutes())
                .isEqualTo(140L);
    }

    @Test
    void forecastCorrectionRejectsSupersededPredecessorWithoutAppendingAnotherSuccessor() {
        long predecessorId = createForecastedTransfer();
        taskService.correctForecast(
                fixture.email(), fixture.firstProjectId(), fixture.firstTaskId(),
                predecessorId, 80, "First correction");

        assertThatThrownBy(() -> taskService.correctForecast(
                fixture.email(), fixture.firstProjectId(), fixture.firstTaskId(),
                predecessorId, 70, "Stale correction"))
                .isInstanceOf(TaskConflictException.class);
        assertThat(forecasts.findAllByProjectIdAndTaskIdOrderByAssignmentStartedAtAscCreatedAtAscIdAsc(
                fixture.firstProjectId(), fixture.firstTaskId())).hasSize(2);
    }

    @Test
    void historicalAssignmentForecastsRemainChronologicalButOnlyCurrentAssignmentIsCorrectable() {
        long thirdInternId = insertIntern("worklog-chain-" + fixture.firstProjectId() + "@example.test");
        long mentorId = jdbc.sql("select mentor_user_id from projects where id = :projectId")
                .param("projectId", fixture.firstProjectId())
                .query(Long.class)
                .single();
        long thirdMembershipId = insertMembership(fixture.firstProjectId(), thirdInternId, mentorId);

        taskService.addWorkLog(
                fixture.email(), fixture.firstProjectId(), fixture.firstTaskId(),
                WORK_DATE, 60, "Original assignment effort");
        taskService.reassign(
                fixture.email(), fixture.firstProjectId(), fixture.firstTaskId(),
                null, fixture.otherMembershipId(), new RemainingEffortForecastInput(90, "First handover"));
        taskService.reassign(
                fixture.email(), fixture.firstProjectId(), fixture.firstTaskId(),
                null, thirdMembershipId, new RemainingEffortForecastInput(80, "Second handover"));

        List<TaskRemainingEffortForecast> history = forecasts
                .findAllByProjectIdAndTaskIdOrderByAssignmentStartedAtAscCreatedAtAscIdAsc(
                        fixture.firstProjectId(), fixture.firstTaskId());
        assertThat(history).hasSize(2);
        long historicalForecastId = history.get(0).getId();
        long currentForecastId = history.get(1).getId();
        var details = taskService.details(
                fixture.email(), fixture.firstProjectId(), fixture.firstTaskId());
        assertThat(details.remainingEffortForecasts()).extracting("id")
                .containsExactly(historicalForecastId, currentForecastId);
        assertThat(details.remainingEffortForecasts().get(0).currentAssignment()).isFalse();
        assertThat(details.remainingEffortForecasts().get(0).superseded()).isFalse();
        assertThat(details.remainingEffortForecasts().get(1).currentAssignment()).isTrue();
        assertThat(details.remainingEffortForecasts().get(1).superseded()).isFalse();

        assertThatThrownBy(() -> taskService.correctForecast(
                fixture.email(), fixture.firstProjectId(), fixture.firstTaskId(),
                historicalForecastId, 70, "Stale assignment correction"))
                .isInstanceOf(TaskConflictException.class);
        var correction = taskService.correctForecast(
                fixture.email(), fixture.firstProjectId(), fixture.firstTaskId(),
                currentForecastId, 70, "Current assignment correction");
        assertThat(correction.currentAssignment()).isTrue();
        assertThat(correction.supersedesForecastId()).isEqualTo(currentForecastId);
    }

    @Test
    void forecastCorrectionRejectsWrongProjectTaskContextWithoutAppending() {
        long predecessorId = createForecastedTransfer();

        assertThatThrownBy(() -> taskService.correctForecast(
                fixture.email(), fixture.secondProjectId(), fixture.firstTaskId(),
                predecessorId, 80, "Wrong Project"))
                .isInstanceOf(TaskNotFoundException.class);
        assertThat(forecasts.findAllByProjectIdAndTaskIdOrderByAssignmentStartedAtAscCreatedAtAscIdAsc(
                fixture.firstProjectId(), fixture.firstTaskId())).hasSize(1);
    }

    @Test
    void forecastCorrectionRejectsAfterIncomingWorkBeginsWithoutAppending() {
        long predecessorId = createForecastedTransfer();
        taskService.addWorkLog(
                fixture.otherEmail(), fixture.firstProjectId(), fixture.firstTaskId(),
                WORK_DATE, 60, "Incoming work");

        var details = taskService.details(
                fixture.email(), fixture.firstProjectId(), fixture.firstTaskId());
        assertThat(details.remainingEffortForecasts()).singleElement()
                .satisfies(forecast -> {
                    assertThat(forecast.currentAssignment()).isTrue();
                    assertThat(forecast.superseded()).isFalse();
                    assertThat(forecast.correctionOpen()).isFalse();
                });

        assertThatThrownBy(() -> taskService.correctForecast(
                fixture.email(), fixture.firstProjectId(), fixture.firstTaskId(),
                predecessorId, 80, "Late correction"))
                .isInstanceOf(TaskValidationException.class)
                .hasMessageContaining("closed");
        assertThat(forecasts.findAllByProjectIdAndTaskIdOrderByAssignmentStartedAtAscCreatedAtAscIdAsc(
                fixture.firstProjectId(), fixture.firstTaskId())).hasSize(1);
    }

    @Test
    void correctingPreAssignmentWorkRefreshesSnapshotWithoutClosingForecastWindow() {
        long predecessorId = createForecastedTransfer();
        TaskRemainingEffortForecast predecessor = forecasts.findById(predecessorId).orElseThrow();
        Instant assignmentStartedAt = predecessor.getAssignmentStartedAt();
        long historicalLogId = workLogs
                .findAllByTaskIdAndProjectIdOrderByWorkDateAscIdAsc(
                        fixture.firstTaskId(), fixture.firstProjectId())
                .getFirst()
                .getId();

        TaskWorkLogView corrected = taskService.correctWorkLog(
                fixture.email(), fixture.firstProjectId(), historicalLogId,
                120, "Corrected before handover");
        assertThat(corrected.minutes()).isEqualTo(120);
        assertThat(workLogs.sumMinutesByTaskIdAndProjectId(
                fixture.firstTaskId(), fixture.firstProjectId())).isEqualTo(120L);

        var correction = taskService.correctForecast(
                fixture.email(), fixture.firstProjectId(), fixture.firstTaskId(),
                predecessorId, 80, "Refresh after historical correction");
        assertThat(correction.incomingMembershipId()).isEqualTo(fixture.otherMembershipId());
        assertThat(correction.assignmentStartedAt()).isEqualTo(assignmentStartedAt);
        assertThat(correction.actualMinutesSnapshot()).isEqualTo(120L);
        assertThat(correction.forecastTotalMinutes()).isEqualTo(200L);
        var taskAfterCorrection = taskService.details(
                fixture.email(), fixture.firstProjectId(), fixture.firstTaskId()).task();
        assertThat(taskAfterCorrection.assigneeMembershipId()).isEqualTo(fixture.otherMembershipId());
        assertThat(taskAfterCorrection.assignedAt()).isEqualTo(assignmentStartedAt);
        assertThat(forecasts.findAllByProjectIdAndTaskIdOrderByAssignmentStartedAtAscCreatedAtAscIdAsc(
                fixture.firstProjectId(), fixture.firstTaskId())).hasSize(2);
    }

    @Test
    void correctingPreAssignmentWorkCannotReopenWindowAfterIncomingWorkExists() {
        long predecessorId = createForecastedTransfer();
        TaskRemainingEffortForecast predecessor = forecasts.findById(predecessorId).orElseThrow();
        Instant assignmentStartedAt = predecessor.getAssignmentStartedAt();
        long historicalLogId = workLogs
                .findAllByTaskIdAndProjectIdOrderByWorkDateAscIdAsc(
                        fixture.firstTaskId(), fixture.firstProjectId())
                .getFirst()
                .getId();
        taskService.addWorkLog(
                fixture.otherEmail(), fixture.firstProjectId(), fixture.firstTaskId(),
                WORK_DATE, 30, "Incoming work");

        TaskWorkLogView corrected = taskService.correctWorkLog(
                fixture.email(), fixture.firstProjectId(), historicalLogId,
                120, "Corrected after handover");
        assertThat(corrected.minutes()).isEqualTo(120);
        assertThat(workLogs.sumMinutesByTaskIdAndProjectId(
                fixture.firstTaskId(), fixture.firstProjectId())).isEqualTo(150L);
        var taskAfterHistoricalCorrection = taskService.details(
                fixture.email(), fixture.firstProjectId(), fixture.firstTaskId()).task();
        assertThat(taskAfterHistoricalCorrection.assigneeMembershipId())
                .isEqualTo(fixture.otherMembershipId());
        assertThat(taskAfterHistoricalCorrection.assignedAt()).isEqualTo(assignmentStartedAt);
        assertThatThrownBy(() -> taskService.correctForecast(
                fixture.email(), fixture.firstProjectId(), fixture.firstTaskId(),
                predecessorId, 80, "Late after historical correction"))
                .isInstanceOf(TaskValidationException.class)
                .hasMessageContaining("closed");
        List<TaskRemainingEffortForecast> history = forecasts
                .findAllByProjectIdAndTaskIdOrderByAssignmentStartedAtAscCreatedAtAscIdAsc(
                        fixture.firstProjectId(), fixture.firstTaskId());
        assertThat(history).hasSize(1);
        assertThat(history.getFirst().getAssignmentStartedAt()).isEqualTo(assignmentStartedAt);
        assertThat(history.getFirst().getActualMinutesSnapshot()).isEqualTo(60L);
    }

    @Test
    void forecastCorrectionRejectsIncomingNonLeaderWithoutAppending() {
        long predecessorId = createForecastedTransfer();

        assertThatThrownBy(() -> taskService.correctForecast(
                fixture.otherEmail(), fixture.firstProjectId(), fixture.firstTaskId(),
                predecessorId, 80, "Unauthorized correction"))
                .isInstanceOf(TaskNotFoundException.class);
        assertThat(forecasts.findAllByProjectIdAndTaskIdOrderByAssignmentStartedAtAscCreatedAtAscIdAsc(
                fixture.firstProjectId(), fixture.firstTaskId())).hasSize(1);
    }

    @Test
    void concurrentForecastCorrectionsAllowOneSuccessAndOneStaleConflict() throws Exception {
        long predecessorId = createForecastedTransfer();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Throwable> first = submitForecastCorrection(
                    executor, start, predecessorId, "Concurrent A");
            Future<Throwable> second = submitForecastCorrection(
                    executor, start, predecessorId, "Concurrent B");
            start.countDown();

            List<Throwable> failures = java.util.stream.Stream.of(
                            first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS))
                    .filter(java.util.Objects::nonNull)
                    .toList();
            assertThat(failures).singleElement().isInstanceOf(TaskConflictException.class);
            assertThat(forecasts.findAllByProjectIdAndTaskIdOrderByAssignmentStartedAtAscCreatedAtAscIdAsc(
                    fixture.firstProjectId(), fixture.firstTaskId())).hasSize(2);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void concurrentForecastAwareBatchTransfersAllowOneWinnerWithoutDuplicateHistory() throws Exception {
        long secondTaskId = insertTask(
                fixture.firstProjectId(), fixture.firstMembershipId(), fixture.firstMembershipId(),
                "Concurrent batch companion");
        taskService.addWorkLog(
                fixture.email(), fixture.firstProjectId(), fixture.firstTaskId(),
                WORK_DATE, 60, "First task effort");
        taskService.addWorkLog(
                fixture.email(), fixture.firstProjectId(), secondTaskId,
                WORK_DATE, 60, "Second task effort");
        ProjectTaskContext context = projectMutations.taskMutationContext(
                fixture.internId(), fixture.firstProjectId());
        Map<Long, Long> expectedVersions = Map.of(
                fixture.firstTaskId(), taskVersion(fixture.firstTaskId()),
                secondTaskId, taskVersion(secondTaskId));
        Map<Long, RemainingEffortForecastInput> forecastInputs = Map.of(
                fixture.firstTaskId(), new RemainingEffortForecastInput(90, "First handover"),
                secondTaskId, new RemainingEffortForecastInput(80, "Second handover"));
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Throwable> first = submitBatchTransfer(
                    executor, start, context, Set.of(fixture.firstTaskId(), secondTaskId),
                    expectedVersions, forecastInputs);
            Future<Throwable> second = submitBatchTransfer(
                    executor, start, context, Set.of(fixture.firstTaskId(), secondTaskId),
                    expectedVersions, forecastInputs);
            start.countDown();

            List<Throwable> failures = java.util.stream.Stream.of(
                            first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS))
                    .filter(java.util.Objects::nonNull)
                    .toList();
            assertThat(failures).singleElement().isInstanceOf(TaskConflictException.class);
            assertThat(jdbc.sql("select assignee_membership_id from tasks where id in (:first, :second)")
                    .param("first", fixture.firstTaskId())
                    .param("second", secondTaskId)
                    .query(Long.class).list())
                    .containsOnly(fixture.otherMembershipId());
            assertThat(forecasts.findAllByProjectIdAndTaskIdOrderByAssignmentStartedAtAscCreatedAtAscIdAsc(
                    fixture.firstProjectId(), fixture.firstTaskId())).hasSize(1);
            assertThat(forecasts.findAllByProjectIdAndTaskIdOrderByAssignmentStartedAtAscCreatedAtAscIdAsc(
                    fixture.firstProjectId(), secondTaskId)).hasSize(1);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void correctionRejectsDeletedTaskHistoryEvenWhileAuthorAndProjectRemainActive() {
        TaskWorkLogView original = taskService.addWorkLog(
                fixture.email(), fixture.firstProjectId(), fixture.firstTaskId(),
                WORK_DATE, 120, "Original");
        jdbc.sql("""
                        update tasks
                        set deleted_at = current_timestamp,
                            deleted_by_membership_id = :membershipId
                        where id = :taskId
                        """)
                .param("membershipId", fixture.firstMembershipId())
                .param("taskId", fixture.firstTaskId())
                .update();

        assertThatThrownBy(() -> taskService.correctWorkLog(
                        fixture.email(), fixture.firstProjectId(), original.id(),
                        60, "Rejected after deletion"))
                .isInstanceOf(TaskNotFoundException.class);
        assertThat(workLogs.findById(original.id()).orElseThrow().getMinutes()).isEqualTo(120);
    }

    @Test
    void correctionRejectsFormerMemberWithoutChangingTheLog() {
        TaskWorkLogView original = taskService.addWorkLog(
                fixture.email(), fixture.firstProjectId(), fixture.firstTaskId(),
                WORK_DATE, 120, "Original");
        jdbc.sql("""
                        update project_memberships
                        set left_at = current_timestamp,
                            removed_by_mentor_user_id = (
                                select mentor_user_id from projects where id = :projectId)
                        where id = :membershipId
                        """)
                .param("projectId", fixture.firstProjectId())
                .param("membershipId", fixture.firstMembershipId())
                .update();

        assertThatThrownBy(() -> taskService.correctWorkLog(
                        fixture.email(), fixture.firstProjectId(), original.id(),
                        60, "Rejected after removal"))
                .isInstanceOf(TaskNotFoundException.class);
        assertThat(workLogs.findById(original.id()).orElseThrow().getMinutes()).isEqualTo(120);
    }

    @Test
    void correctionRejectsCompletedProjectWithoutChangingTheLog() {
        TaskWorkLogView original = taskService.addWorkLog(
                fixture.email(), fixture.firstProjectId(), fixture.firstTaskId(),
                WORK_DATE, 120, "Original");
        jdbc.sql("update projects set status = 'COMPLETED', completed_at = current_timestamp where id = :projectId")
                .param("projectId", fixture.firstProjectId())
                .update();

        assertThatThrownBy(() -> taskService.correctWorkLog(
                        fixture.email(), fixture.firstProjectId(), original.id(),
                        60, "Rejected after completion"))
                .isInstanceOf(TaskNotFoundException.class);
        assertThat(workLogs.findById(original.id()).orElseThrow().getMinutes()).isEqualTo(120);
    }

    @Test
    void workLogRejectsInactiveAndTerminalInternLifecycle() {
        jdbc.sql("update app_users set account_status = 'LOCKED', locked_at = current_timestamp where id = :id")
                .param("id", fixture.internId())
                .update();
        assertThatThrownBy(() -> taskService.addWorkLog(
                fixture.email(), fixture.firstProjectId(), fixture.firstTaskId(),
                        WORK_DATE, 60, "Locked"))
                .isInstanceOf(com.lab.labtimesheet.feature.task.exception.TaskNotFoundException.class);

        jdbc.sql("update app_users set account_status = 'ACTIVE', locked_at = null where id = :id")
                .param("id", fixture.internId())
                .update();
        jdbc.sql("""
                        update intern_profiles
                        set internship_status = 'COMPLETED', completed_at = current_timestamp
                        where user_id = :id
                        """)
                .param("id", fixture.internId())
                .update();
        assertThatThrownBy(() -> taskService.addWorkLog(
                fixture.email(), fixture.firstProjectId(), fixture.firstTaskId(),
                        WORK_DATE, 60, "Terminal"))
                .isInstanceOf(TaskValidationException.class);
    }

    @Test
    void profileLockBlocksWorkLogUntilOuterTransactionCommits() throws Exception {
        CountDownLatch lockHeld = new CountDownLatch(1);
        CountDownLatch releaseLock = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> outer = executor.submit(() -> transactions.executeWithoutResult(status -> {
                accounts.lockedInternWorkWindow(fixture.internId(), WORK_DATE);
                lockHeld.countDown();
                await(releaseLock);
            }));
            assertThat(lockHeld.await(5, TimeUnit.SECONDS)).isTrue();

            Future<TaskWorkLogView> worker = executor.submit(() -> taskService.addWorkLog(
                    fixture.email(), fixture.firstProjectId(), fixture.firstTaskId(),
                    WORK_DATE, 60, "After lock"));
            assertThatThrownBy(() -> worker.get(250, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            releaseLock.countDown();
            outer.get(10, TimeUnit.SECONDS);
            assertThat(worker.get(10, TimeUnit.SECONDS).minutes()).isEqualTo(60);
        } finally {
            releaseLock.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void accountFirstProjectMutationDoesNotDeadlockWithWorkLog() throws Exception {
        CountDownLatch accountLockHeld = new CountDownLatch(1);
        CountDownLatch allowProjectLock = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> accountFirst = executor.submit(() -> transactions.executeWithoutResult(status -> {
                accounts.lockedInternWorkWindow(fixture.internId(), WORK_DATE);
                accountLockHeld.countDown();
                await(allowProjectLock);
                projectMutations.taskMutationContext(fixture.internId(), fixture.firstProjectId());
            }));
            assertThat(accountLockHeld.await(5, TimeUnit.SECONDS)).isTrue();

            Future<TaskWorkLogView> worker = executor.submit(() -> taskService.addWorkLog(
                    fixture.email(), fixture.firstProjectId(), fixture.firstTaskId(),
                    WORK_DATE, 60, "Inverse order"));
            assertThatThrownBy(() -> worker.get(250, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);

            allowProjectLock.countDown();
            accountFirst.get(10, TimeUnit.SECONDS);
            assertThat(worker.get(10, TimeUnit.SECONDS).minutes()).isEqualTo(60);
        } finally {
            allowProjectLock.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void concurrentProjectsRejectOneOfExactly1441AttemptedMinutes() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Throwable> first = submitAllocation(
                    executor, start, fixture.firstProjectId(), fixture.firstTaskId(), 900);
            Future<Throwable> second = submitAllocation(
                    executor, start, fixture.secondProjectId(), fixture.secondTaskId(), 541);
            start.countDown();

            Throwable firstFailure = first.get(10, TimeUnit.SECONDS);
            Throwable secondFailure = second.get(10, TimeUnit.SECONDS);
            List<Throwable> failures = java.util.stream.Stream.of(firstFailure, secondFailure)
                    .filter(java.util.Objects::nonNull)
                    .toList();

            assertThat(failures).hasSize(1);
            assertThat(failures.get(0)).isInstanceOf(TaskValidationException.class);
            assertThat(workLogs.sumMinutesByMembershipIdsAndWorkDate(
                    java.util.Set.of(fixture.firstMembershipId(), fixture.secondMembershipId()), WORK_DATE))
                    .isIn(900L, 541L);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void twoTransactionsUsingOneObservedTaskVersionRejectTheStaleStatusWithoutPartialState()
            throws Exception {
        long expectedVersion = jdbc.sql("select version from tasks where id = :taskId")
                .param("taskId", fixture.firstTaskId())
                .query(Long.class)
                .single();
        long notificationsBefore = notificationCount();
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Throwable> first = submitStatus(
                    executor, start, TaskStatus.BLOCKED, expectedVersion);
            Future<Throwable> second = submitStatus(
                    executor, start, TaskStatus.DONE, expectedVersion);
            start.countDown();

            List<Throwable> failures = java.util.stream.Stream.of(
                            first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS))
                    .filter(java.util.Objects::nonNull)
                    .toList();

            assertThat(failures).hasSize(1);
            assertThat(failures.get(0)).isInstanceOf(TaskConflictException.class);
            assertThat(jdbc.sql("select version from tasks where id = :taskId")
                    .param("taskId", fixture.firstTaskId()).query(Long.class).single())
                    .isEqualTo(expectedVersion + 1);
            assertThat(jdbc.sql("select status from tasks where id = :taskId")
                    .param("taskId", fixture.firstTaskId()).query(String.class).single())
                    .isIn(TaskStatus.BLOCKED.name(), TaskStatus.DONE.name());
            assertThat(notificationCount()).isEqualTo(notificationsBefore);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private Future<Throwable> submitStatus(
            ExecutorService executor, CountDownLatch start, TaskStatus target, long expectedVersion) {
        return executor.submit(() -> {
            start.await(5, TimeUnit.SECONDS);
            try {
                taskService.changeStatus(
                        fixture.email(), fixture.firstProjectId(), fixture.firstTaskId(),
                        expectedVersion, target);
                return null;
            } catch (Throwable failure) {
                return unwrap(failure);
            }
        });
    }

    private Future<Throwable> submitAllocation(
            ExecutorService executor, CountDownLatch start, long projectId, long taskId, int minutes) {
        return executor.submit(() -> {
            start.await(5, TimeUnit.SECONDS);
            try {
                transactions.execute(status -> {
                    taskService.addWorkLog(
                            fixture.email(), projectId, taskId, WORK_DATE, minutes,
                            "Concurrent");
                    return null;
                });
                return null;
            } catch (Throwable failure) {
                return unwrap(failure);
            }
        });
    }

    private long createForecastedTransfer() {
        taskService.addWorkLog(
                fixture.email(), fixture.firstProjectId(), fixture.firstTaskId(),
                WORK_DATE, 60, "Initial effort");
        taskService.reassign(
                fixture.email(), fixture.firstProjectId(), fixture.firstTaskId(),
                null, fixture.otherMembershipId(), new RemainingEffortForecastInput(90, "Initial forecast"));
        return forecasts.findAllByProjectIdAndTaskIdOrderByAssignmentStartedAtAscCreatedAtAscIdAsc(
                        fixture.firstProjectId(), fixture.firstTaskId())
                .stream()
                .findFirst()
                .map(TaskRemainingEffortForecast::getId)
                .orElseThrow();
    }

    private Future<Throwable> submitForecastCorrection(
            ExecutorService executor, CountDownLatch start, long predecessorId, String reason) {
        return executor.submit(() -> {
            start.await(5, TimeUnit.SECONDS);
            try {
                taskService.correctForecast(
                        fixture.email(), fixture.firstProjectId(), fixture.firstTaskId(),
                        predecessorId, 80, reason);
                return null;
            } catch (Throwable failure) {
                return unwrap(failure);
            }
        });
    }

    private Future<Throwable> submitBatchTransfer(
            ExecutorService executor,
            CountDownLatch start,
            ProjectTaskContext context,
            Set<Long> taskIds,
            Map<Long, Long> expectedVersions,
            Map<Long, RemainingEffortForecastInput> forecastInputs) {
        return executor.submit(() -> {
            start.await(5, TimeUnit.SECONDS);
            try {
                taskTransfers.transferBatch(
                        context,
                        fixture.firstMembershipId(),
                        fixture.firstMembershipId(),
                        taskIds,
                        expectedVersions,
                        forecastInputs,
                        fixture.otherMembershipId());
                return null;
            } catch (Throwable failure) {
                return unwrap(failure);
            }
        });
    }

    private long taskVersion(long taskId) {
        return jdbc.sql("select version from tasks where id = :taskId")
                .param("taskId", taskId)
                .query(Long.class)
                .single();
    }

    private static Throwable unwrap(Throwable failure) {
        return failure.getCause() == null ? failure : failure.getCause();
    }

    private long notificationCount() {
        return jdbc.sql("select count(*) from notifications").query(Long.class).single();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Timed out waiting for test lock release");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for test lock release", exception);
        }
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
                .param("studentCode", "WORK-" + userId)
                .update();
        return userId;
    }

    private long insertProject(long mentorId, String status, String name) {
        return jdbc.sql("""
                        insert into projects
                            (mentor_user_id, name, status, start_date, end_date, activated_at)
                        values (:mentorId, :name, :status, date '2026-08-01', date '2026-08-31',
                                current_timestamp)
                        returning id
                        """)
                .param("mentorId", mentorId)
                .param("name", "Worklog " + name)
                .param("status", status)
                .query(Long.class)
                .single();
    }

    private long insertMembership(long projectId, long internId, long mentorId) {
        return jdbc.sql("""
                        insert into project_memberships
                            (project_id, intern_user_id, added_by_user_id, joined_at)
                        values (:projectId, :internId, :mentorId,
                                timestamp with time zone '2026-08-01 00:00:00+07')
                        returning id
                        """)
                .param("projectId", projectId)
                .param("internId", internId)
                .param("mentorId", mentorId)
                .query(Long.class)
                .single();
    }

    private void insertLeaderTerm(long projectId, long membershipId, long mentorId) {
        jdbc.sql("""
                        insert into project_leadership_terms
                            (project_id, membership_id, appointed_by_mentor_user_id)
                        values (:projectId, :membershipId, :mentorId)
                        """)
                .param("projectId", projectId)
                .param("membershipId", membershipId)
                .param("mentorId", mentorId)
                .update();
    }

    private long insertTask(long projectId, long membershipId, long assignedBy, String title) {
        return jdbc.sql("""
                        insert into tasks
                            (project_id, assignee_membership_id, title, created_by_membership_id,
                             assigned_by_membership_id, assigned_at, status, created_at, updated_at)
                        values (:projectId, :membershipId, :title, :membershipId,
                                :assignedBy, current_timestamp, 'IN_PROGRESS', current_timestamp, current_timestamp)
                        returning id
                        """)
                .param("projectId", projectId)
                .param("membershipId", membershipId)
                .param("title", title)
                .param("assignedBy", assignedBy)
                .query(Long.class)
                .single();
    }

    private record Fixture(
            String email,
            long internId,
            long firstProjectId,
            long secondProjectId,
            long firstMembershipId,
            long secondMembershipId,
            long firstTaskId,
            long secondTaskId,
            String otherEmail,
            long otherMembershipId) {
    }
}
