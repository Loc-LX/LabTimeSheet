package com.lab.labtimesheet.feature.task.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;

import com.lab.labtimesheet.feature.task.model.TaskStatus;
import com.lab.labtimesheet.feature.task.model.TaskVarianceState;
import com.lab.labtimesheet.feature.task.model.dto.TaskActualMinutesView;
import com.lab.labtimesheet.feature.task.model.dto.TaskWorkLogView;
import com.lab.labtimesheet.feature.task.model.entity.Task;
import com.lab.labtimesheet.feature.task.model.entity.TaskRemainingEffortForecast;
import com.lab.labtimesheet.feature.task.model.entity.TaskWorkLog;
import com.lab.labtimesheet.feature.task.repository.TaskCommentRepository;
import com.lab.labtimesheet.feature.task.repository.TaskRemainingEffortForecastRepository;
import com.lab.labtimesheet.feature.task.repository.TaskRepository;
import com.lab.labtimesheet.feature.task.repository.TaskWorkLogRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskQueryServiceTest {

    @Mock
    private TaskRepository tasks;

    @Mock
    private TaskCommentRepository comments;

    @Mock
    private TaskWorkLogRepository workLogs;

    @Mock
    private TaskRemainingEffortForecastRepository forecasts;

    @InjectMocks
    private TaskQueryService taskQueries;

    @Test
    void countsEveryCurrentTaskWhenProjectHasNoActiveMemberships() {
        given(tasks.countByProjectIdAndDeletedAtIsNull(42L)).willReturn(3L);

        assertThat(taskQueries.countCurrentTasksAssignedOutside(42L, Set.of())).isEqualTo(3L);

        verify(tasks).countByProjectIdAndDeletedAtIsNull(42L);
    }

    @Test
    void countsCurrentTasksWhoseAssigneeIsOutsideActiveMemberships() {
        given(tasks.countCurrentTasksAssignedOutside(42L, Set.of(7L, 9L))).willReturn(2L);

        assertThat(taskQueries.countCurrentTasksAssignedOutside(42L, Set.of(7L, 9L))).isEqualTo(2L);

        verify(tasks).countCurrentTasksAssignedOutside(42L, Set.of(7L, 9L));
    }

    @Test
    void dailyReportMapsSoftDeletedTaskLifetimeVarianceAndLatestApplicableForecastFromBatchedReads() {
        Task task = mock(Task.class);
        Instant assignment = Instant.parse("2026-08-20T02:00:00Z");
        given(task.getId()).willReturn(501L);
        given(task.getProjectId()).willReturn(42L);
        given(task.getAssigneeMembershipId()).willReturn(8L);
        given(task.getTitle()).willReturn("Prepare handover");
        given(task.getStatus()).willReturn(TaskStatus.DONE);
        given(task.getEstimatedMinutes()).willReturn(120);
        given(task.getAssignedAt()).willReturn(assignment);
        given(task.getCreatedAt()).willReturn(Instant.parse("2026-08-01T02:00:00Z"));
        given(task.getDeletedAt()).willReturn(Instant.parse("2026-08-25T02:00:00Z"));
        given(tasks.findAllByProjectIdAndIdInOrderById(42L, Set.of(501L))).willReturn(List.of(task));

        TaskWorkLog log = mock(TaskWorkLog.class);
        given(log.getId()).willReturn(101L);
        given(log.getProjectId()).willReturn(42L);
        given(log.getTaskId()).willReturn(501L);
        given(log.getMembershipId()).willReturn(7L);
        given(log.getWorkDate()).willReturn(LocalDate.of(2026, 8, 20));
        given(log.getMinutes()).willReturn(150);
        given(log.getNote()).willReturn("retained handover");
        given(log.getCreatedAt()).willReturn(Instant.parse("2026-08-20T08:00:00Z"));
        given(log.getUpdatedAt()).willReturn(Instant.parse("2026-08-20T08:00:00Z"));
        given(workLogs.findAllByProjectIdAndWorkDateOrderByTaskIdAscIdAsc(42L, LocalDate.of(2026, 8, 20)))
                .willReturn(List.of(log));
        given(workLogs.sumMinutesByProjectGroupedByTask(42L, Set.of(501L)))
                .willReturn(List.of(new TaskActualMinutesView(501L, 150L)));

        TaskRemainingEffortForecast predecessor = mock(TaskRemainingEffortForecast.class);
        given(predecessor.getId()).willReturn(601L);
        given(predecessor.getTaskId()).willReturn(501L);
        given(predecessor.getIncomingMembershipId()).willReturn(8L);
        given(predecessor.getAssignmentStartedAt()).willReturn(assignment);
        TaskRemainingEffortForecast latest = mock(TaskRemainingEffortForecast.class);
        given(latest.getId()).willReturn(602L);
        given(latest.getTaskId()).willReturn(501L);
        given(latest.getIncomingMembershipId()).willReturn(8L);
        given(latest.getAssignmentStartedAt()).willReturn(assignment);
        given(latest.getSupersedesForecastId()).willReturn(601L);
        given(latest.getRemainingMinutes()).willReturn(75);
        given(latest.getActualMinutesSnapshot()).willReturn(150L);
        given(forecasts.findAllByProjectIdAndTaskIdInOrderByTaskIdAscAssignmentStartedAtAscCreatedAtAscIdAsc(
                42L, Set.of(501L)))
                .willReturn(List.of(predecessor, latest));

        var result = taskQueries.dailyReport(42L, LocalDate.of(2026, 8, 20));

        assertThat(result).singleElement().satisfies(view -> {
            assertThat(view.deleted()).isTrue();
            assertThat(view.lifetimeActualMinutes()).isEqualTo(150L);
            assertThat(view.varianceState()).isEqualTo(TaskVarianceState.VALUE);
            assertThat(view.varianceMinutes()).isEqualTo(30L);
            assertThat(view.latestForecast().remainingMinutes()).isEqualTo(75);
            assertThat(view.latestForecast().actualMinutesSnapshot()).isEqualTo(150L);
        });
        verify(forecasts).findAllByProjectIdAndTaskIdInOrderByTaskIdAscAssignmentStartedAtAscCreatedAtAscIdAsc(
                42L, Set.of(501L));
        verify(workLogs).findAllByProjectIdAndWorkDateOrderByTaskIdAscIdAsc(42L, LocalDate.of(2026, 8, 20));
        verify(workLogs).sumMinutesByProjectGroupedByTask(42L, Set.of(501L));
    }

    @Test
    void dailyReportReadsSelectedDateLogsAndLifetimeAggregateWithoutPerTaskHistoryQueries() {
        LocalDate reportDate = LocalDate.of(2026, 8, 20);
        Instant assignment = Instant.parse("2026-08-20T02:00:00Z");
        Task task = mock(Task.class);
        given(task.getId()).willReturn(501L);
        given(task.getProjectId()).willReturn(42L);
        given(task.getAssigneeMembershipId()).willReturn(8L);
        given(task.getTitle()).willReturn("Prepare handover");
        given(task.getStatus()).willReturn(TaskStatus.IN_PROGRESS);
        given(task.getAssignedAt()).willReturn(assignment);
        given(task.getCreatedAt()).willReturn(Instant.parse("2026-08-01T02:00:00Z"));
        given(tasks.findAllByProjectIdAndIdInOrderById(42L, Set.of(501L))).willReturn(List.of(task));

        TaskWorkLog selectedLog = mock(TaskWorkLog.class);
        given(selectedLog.getId()).willReturn(101L);
        given(selectedLog.getProjectId()).willReturn(42L);
        given(selectedLog.getTaskId()).willReturn(501L);
        given(selectedLog.getMembershipId()).willReturn(7L);
        given(selectedLog.getWorkDate()).willReturn(reportDate);
        given(selectedLog.getMinutes()).willReturn(45);
        given(selectedLog.getCreatedAt()).willReturn(Instant.parse("2026-08-20T08:00:00Z"));
        given(selectedLog.getUpdatedAt()).willReturn(Instant.parse("2026-08-20T08:00:00Z"));
        given(workLogs.findAllByProjectIdAndWorkDateOrderByTaskIdAscIdAsc(42L, reportDate))
                .willReturn(List.of(selectedLog));
        given(workLogs.sumMinutesByProjectGroupedByTask(42L, Set.of(501L)))
                .willReturn(List.of(new TaskActualMinutesView(501L, 150L)));
        given(forecasts.findAllByProjectIdAndTaskIdInOrderByTaskIdAscAssignmentStartedAtAscCreatedAtAscIdAsc(
                42L, Set.of(501L)))
                .willReturn(List.of());

        var result = taskQueries.dailyReport(42L, reportDate);

        assertThat(result).singleElement().satisfies(view -> {
            assertThat(view.workLogs()).extracting(TaskWorkLogView::id).containsExactly(101L);
            assertThat(view.lifetimeActualMinutes()).isEqualTo(150L);
        });
        verify(workLogs).findAllByProjectIdAndWorkDateOrderByTaskIdAscIdAsc(42L, reportDate);
        verify(workLogs).sumMinutesByProjectGroupedByTask(42L, Set.of(501L));
        verify(workLogs, org.mockito.Mockito.never())
                .findAllByTaskIdAndProjectIdOrderByWorkDateAscIdAsc(501L, 42L);
        verify(tasks).findAllByProjectIdAndIdInOrderById(42L, Set.of(501L));
        verify(forecasts).findAllByProjectIdAndTaskIdInOrderByTaskIdAscAssignmentStartedAtAscCreatedAtAscIdAsc(
                42L, Set.of(501L));
        verify(tasks, org.mockito.Mockito.never()).findAllByProjectIdOrderById(42L);
    }

    @Test
    void dailyReportDoesNotReadTasksAggregatesOrForecastsWhenSelectedDateHasNoLogs() {
        LocalDate reportDate = LocalDate.of(2026, 8, 20);
        given(workLogs.findAllByProjectIdAndWorkDateOrderByTaskIdAscIdAsc(42L, reportDate))
                .willReturn(List.of());

        assertThat(taskQueries.dailyReport(42L, reportDate)).isEmpty();

        verify(workLogs).findAllByProjectIdAndWorkDateOrderByTaskIdAscIdAsc(42L, reportDate);
        verify(workLogs, org.mockito.Mockito.never())
                .sumMinutesByProjectGroupedByTask(
                        org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anySet());
        org.mockito.Mockito.verifyNoInteractions(tasks, forecasts);
    }
}
