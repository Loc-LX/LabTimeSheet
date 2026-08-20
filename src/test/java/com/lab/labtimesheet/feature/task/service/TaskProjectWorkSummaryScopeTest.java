package com.lab.labtimesheet.feature.task.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.lab.labtimesheet.feature.project.model.dto.ProjectActorView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectTaskContext;
import com.lab.labtimesheet.feature.project.model.dto.ProjectTaskMemberView;
import com.lab.labtimesheet.feature.project.service.ProjectQueryService;
import com.lab.labtimesheet.feature.task.model.TaskProgress;
import com.lab.labtimesheet.feature.task.repository.TaskRepository;
import com.lab.labtimesheet.feature.task.repository.TaskWorkLogRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaskProjectWorkSummaryScopeTest {

    @Mock
    private TaskRepository tasks;

    @Mock
    private TaskWorkLogRepository workLogs;

    @Mock
    private ProjectQueryService projects;

    @InjectMocks
    private TaskQueryService taskQueries;

    @Test
    void owningMentorReceivesAggregateAndPerMemberBreakdown() {
        long mentorUserId = 3L;
        given(projects.authenticatedActor("mentor@example.test"))
                .willReturn(new ProjectActorView(mentorUserId, "MENTOR"));
        given(projects.taskContext(mentorUserId, 10L)).willReturn(context(mentorUserId, 7L));
        given(tasks.countByProjectIdGroupedByStatus(10L)).willReturn(List.of());
        given(workLogs.sumMinutesByProjectId(10L)).willReturn(120L);
        given(workLogs.sumMinutesByProjectIdGroupedByMembership(10L))
                .willReturn(List.<Object[]>of(new Object[] {7L, 80L}, new Object[] {9L, 40L}));

        var summary = taskQueries.projectWorkSummary("mentor@example.test", 10L);

        assertThat(summary.perMemberVisible()).isTrue();
        assertThat(summary.totalMinutes()).isEqualTo(120L);
        assertThat(summary.perMemberHours()).hasSize(2);
    }

    @Test
    void currentLeaderReceivesPerMemberBreakdown() {
        long leaderUserId = 5L;
        given(projects.authenticatedActor("leader@example.test"))
                .willReturn(new ProjectActorView(leaderUserId, "INTERN"));
        given(projects.taskContext(leaderUserId, 10L)).willReturn(context(3L, 7L));
        given(tasks.countByProjectIdGroupedByStatus(10L)).willReturn(List.of());
        given(workLogs.sumMinutesByProjectId(10L)).willReturn(60L);
        given(workLogs.sumMinutesByProjectIdGroupedByMembership(10L))
                .willReturn(List.<Object[]>of(new Object[] {7L, 60L}));

        var summary = taskQueries.projectWorkSummary("leader@example.test", 10L);

        assertThat(summary.perMemberVisible()).isTrue();
        assertThat(summary.perMemberHours()).hasSize(1);
    }

    @Test
    void ordinaryMemberReceivesAggregateTotalsWithoutPerMemberDetail() {
        long memberUserId = 6L;
        given(projects.authenticatedActor("member@example.test"))
                .willReturn(new ProjectActorView(memberUserId, "INTERN"));
        given(projects.taskContext(memberUserId, 10L)).willReturn(context(3L, 7L));
        given(tasks.countByProjectIdGroupedByStatus(10L))
                .willReturn(List.<Object[]>of(
                        new Object[] {com.lab.labtimesheet.feature.task.model.TaskStatus.TODO, 1L},
                        new Object[] {com.lab.labtimesheet.feature.task.model.TaskStatus.DONE, 1L}));
        given(workLogs.sumMinutesByProjectId(10L)).willReturn(90L);

        var summary = taskQueries.projectWorkSummary("member@example.test", 10L);

        assertThat(summary.perMemberVisible()).isFalse();
        assertThat(summary.perMemberHours()).isEmpty();
        assertThat(summary.totalMinutes()).isEqualTo(90L);
        assertThat(summary.progress()).isEqualTo(new TaskProgress(1, 0, 0, 1));
        assertThat(summary.completionPercentage()).hasValue(50.0);
    }

    @Test
    void emptyProjectProducesNACompletenessWithZeroMinutes() {
        given(projects.authenticatedActor("mentor@example.test"))
                .willReturn(new ProjectActorView(3L, "MENTOR"));
        given(projects.taskContext(3L, 10L)).willReturn(context(3L, 7L));
        given(tasks.countByProjectIdGroupedByStatus(10L)).willReturn(List.of());
        given(workLogs.sumMinutesByProjectId(10L)).willReturn(0L);

        var summary = taskQueries.projectWorkSummary("mentor@example.test", 10L);

        assertThat(summary.completionPercentage()).isEmpty();
        assertThat(summary.totalMinutes()).isZero();
        assertThat(summary.progress().total()).isZero();
    }

    private static ProjectTaskContext context(long mentorUserId, long leaderMembershipId) {
        return new ProjectTaskContext(
                10L,
                mentorUserId,
                "ACTIVE",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 8, 31),
                leaderMembershipId,
                List.of(
                        new ProjectTaskMemberView(7L, 5L, "Leader"),
                        new ProjectTaskMemberView(9L, 6L, "Member")));
    }
}
