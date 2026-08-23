package com.lab.labtimesheet.feature.reporting.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.lab.labtimesheet.feature.reporting.model.dto.ProjectTaskReportSummary;
import com.lab.labtimesheet.feature.reporting.model.dto.ProjectTaskReportTask;
import com.lab.labtimesheet.feature.task.model.TaskStatus;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProjectTaskReportFormulasTest {

    private static final ProjectTaskReportService formulas = new ProjectTaskReportService();

    @Test
    void aggregatesStatusCountsMinutesAndBlockedTasks() {
        List<ProjectTaskReportTask> tasks = List.of(
                task(TaskStatus.DONE, 10),
                task(TaskStatus.DONE, 5),
                task(TaskStatus.IN_PROGRESS, 30),
                task(TaskStatus.BLOCKED, 0));

        ProjectTaskReportSummary summary = formulas.summarize(tasks);

        assertThat(summary.progress().total()).isEqualTo(4);
        assertThat(summary.progress().count(TaskStatus.DONE)).isEqualTo(2);
        assertThat(summary.progress().count(TaskStatus.IN_PROGRESS)).isEqualTo(1);
        assertThat(summary.progress().count(TaskStatus.BLOCKED)).isEqualTo(1);
        assertThat(summary.completionPercentage()).hasValue(50.0);
        assertThat(summary.totalMinutes()).isEqualTo(45);
        assertThat(summary.blockedCount()).isEqualTo(1);
    }

    @Test
    void reportsNAWhenProjectHasNoTasks() {
        ProjectTaskReportSummary summary = formulas.summarize(List.of());

        assertThat(summary.progress().total()).isZero();
        assertThat(summary.completionPercentage()).isEmpty();
        assertThat(summary.totalMinutes()).isZero();
        assertThat(summary.blockedCount()).isZero();
    }

    @Test
    void perMemberDetailIsVisibleOnlyToAdminOwningMentorOrCurrentLeader() {
        assertThat(formulas.allowPerMemberDetail(true, false, false)).isTrue();
        assertThat(formulas.allowPerMemberDetail(false, true, false)).isTrue();
        assertThat(formulas.allowPerMemberDetail(false, false, true)).isTrue();
        assertThat(formulas.allowPerMemberDetail(false, true, true)).isTrue();
        assertThat(formulas.allowPerMemberDetail(false, false, false)).isFalse();
    }

    private static ProjectTaskReportTask task(TaskStatus status, long loggedMinutes) {
        return new ProjectTaskReportTask(status, loggedMinutes);
    }
}