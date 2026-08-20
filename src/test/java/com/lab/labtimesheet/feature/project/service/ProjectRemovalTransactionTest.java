package com.lab.labtimesheet.feature.project.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.project.model.ProjectMemberRemoval;
import com.lab.labtimesheet.feature.project.model.entity.ProjectEntity;
import com.lab.labtimesheet.feature.project.model.entity.ProjectMembershipEntity;
import com.lab.labtimesheet.feature.project.repository.ProjectRepository;
import com.lab.labtimesheet.feature.task.service.TaskQueryService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** [I2-PRJ-04] Transfer lỗi phải dừng trước bước đóng membership trong service Project. */
@ExtendWith(MockitoExtension.class)
class ProjectRemovalTransactionTest {

    private static final Instant NOW = Instant.parse("2026-08-15T00:00:00Z");

    @Mock private ProjectRepository projects;
    @Mock private AccountService accounts;
    @Mock private ProjectQueryService queries;
    @Mock private TaskQueryService taskQueries;
    @Mock private ProjectEntity project;
    @Mock private ProjectMembershipEntity departing;
    @Mock private ProjectMembershipEntity leader;

    @Test
    void transferFailureDoesNotCompleteMemberRemoval() {
        var removal = new ProjectMemberRemoval(departing, leader, NOW);
        when(projects.findLockedById(30L)).thenReturn(Optional.of(project));
        when(project.id()).thenReturn(30L);
        when(departing.id()).thenReturn(70L);
        when(leader.id()).thenReturn(71L);
        when(project.prepareMemberRemoval(10L, 20L, NOW)).thenReturn(removal);
        doThrow(new IllegalStateException("Task transfer failed"))
                .when(taskQueries).transferUnfinishedTasks(30L, 70L, 71L, NOW);

        var service = new ProjectService(
                projects,
                accounts,
                queries,
                taskQueries,
                Clock.fixed(NOW, ZoneOffset.UTC));

        assertThrows(IllegalStateException.class, () -> service.removeMember(10L, 30L, 20L));

        verify(project).prepareMemberRemoval(10L, 20L, NOW);
        verify(project, never()).completeMemberRemoval(10L, removal);
        verify(projects, never()).flush();
    }
}
