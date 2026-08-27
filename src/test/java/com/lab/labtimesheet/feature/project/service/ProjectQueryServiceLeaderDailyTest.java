package com.lab.labtimesheet.feature.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.lab.labtimesheet.feature.account.model.AccountStatus;
import com.lab.labtimesheet.feature.account.model.GlobalRole;
import com.lab.labtimesheet.feature.account.model.dto.AccountIdentity;
import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.project.exception.ProjectAccessDeniedException;
import com.lab.labtimesheet.feature.project.model.ProjectStatus;
import com.lab.labtimesheet.feature.project.model.dto.ProjectSummary;
import com.lab.labtimesheet.feature.project.model.entity.ProjectEntity;
import com.lab.labtimesheet.feature.project.repository.ProjectExitRequestRepository;
import com.lab.labtimesheet.feature.project.repository.ProjectInvitationRepository;
import com.lab.labtimesheet.feature.project.repository.ProjectRepository;
import com.lab.labtimesheet.feature.task.service.TaskQueryService;
import com.lab.labtimesheet.feature.task.service.TaskTransferService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Public Project producer contract for current-Leader Daily report navigation. */
class ProjectQueryServiceLeaderDailyTest {

    private final ProjectRepository projects = mock(ProjectRepository.class);
    private final ProjectExitRequestRepository exitRequests = mock(ProjectExitRequestRepository.class);
    private final ProjectInvitationRepository invitations = mock(ProjectInvitationRepository.class);
    private final AccountService accounts = mock(AccountService.class);
    private final TaskQueryService taskQueries = mock(TaskQueryService.class);
    private final TaskTransferService taskTransfers = mock(TaskTransferService.class);
    private ProjectQueryService queries;

    @BeforeEach
    void setUp() {
        queries = new ProjectQueryService(
                projects, exitRequests, invitations, accounts, taskQueries, taskTransfers);
    }

    @Test
    void listsCurrentLeaderProjectsInProducerSuppliedDeterministicOrder() {
        given(accounts.requireIdentityById(7L)).willReturn(identity(7L, GlobalRole.INTERN));
        ProjectEntity newest = project(11L, "Newest Portal");
        ProjectEntity older = project(10L, "Older Portal");
        given(projects.findCurrentLeaderProjectsByInternUserId(7L))
                .willReturn(List.of(newest, older));

        List<ProjectSummary> result = queries.listCurrentLeaderProjectsForDailyReport(7L);

        assertThat(result).extracting(ProjectSummary::id).containsExactly(11L, 10L);
        assertThat(result).extracting(ProjectSummary::name)
                .containsExactly("Newest Portal", "Older Portal");
        verify(projects).findCurrentLeaderProjectsByInternUserId(7L);
    }

    @Test
    void rejectsNonInternBeforeReadingCurrentLeaderProjects() {
        given(accounts.requireIdentityById(1L)).willReturn(identity(1L, GlobalRole.ADMIN));

        assertThatThrownBy(() -> queries.listCurrentLeaderProjectsForDailyReport(1L))
                .isInstanceOf(ProjectAccessDeniedException.class);

        verify(projects, never()).findCurrentLeaderProjectsByInternUserId(1L);
    }

    @Test
    void checksCurrentLeaderCapabilityWithoutLoadingProjectSummaries() {
        given(accounts.requireIdentityById(7L)).willReturn(identity(7L, GlobalRole.INTERN));
        given(projects.existsCurrentLeaderProjectByInternUserId(7L)).willReturn(true);

        assertThat(queries.hasCurrentLeaderProjectForDailyReport(7L)).isTrue();

        verify(projects).existsCurrentLeaderProjectByInternUserId(7L);
        verify(projects, never()).findCurrentLeaderProjectsByInternUserId(7L);
    }

    private static AccountIdentity identity(long id, GlobalRole role) {
        return new AccountIdentity(
                id, "user-" + id + "@example.test", "User " + id, role, AccountStatus.ACTIVE);
    }

    private static ProjectEntity project(long id, String name) {
        ProjectEntity project = mock(ProjectEntity.class);
        given(project.id()).willReturn(id);
        given(project.name()).willReturn(name);
        given(project.status()).willReturn(ProjectStatus.ACTIVE);
        given(project.startDate()).willReturn(LocalDate.of(2026, 8, 1));
        given(project.endDate()).willReturn(LocalDate.of(2026, 8, 31));
        return project;
    }
}
