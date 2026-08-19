package com.lab.labtimesheet.feature.project.service;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.project.model.dto.ProjectTaskContext;
import com.lab.labtimesheet.feature.project.model.entity.ProjectEntity;
import com.lab.labtimesheet.feature.project.repository.ProjectRepository;
import com.lab.labtimesheet.feature.task.service.TaskQueryService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** [I1-PRJ-04, I2-PRJ-02] Giữ boundary Project trước khi feature Task kiểm tra quyền mutation. */
@ExtendWith(MockitoExtension.class)
class ProjectTaskMutationContextTest {

    @Mock
    private ProjectRepository projects;

    @Mock
    private AccountService accounts;

    @Mock
    private ProjectQueryService queries;

    @Mock
    private TaskQueryService taskQueries;

    @Mock
    private ProjectEntity project;

    @Test
    void loadsTheProjectForUpdateBeforeBuildingTheTaskMutationContext() {
        long actorUserId = 20L;
        long projectId = 30L;
        var expected = new ProjectTaskContext(
                projectId,
                10L,
                "ACTIVE",
                LocalDate.of(2026, 8, 15),
                LocalDate.of(2026, 9, 30),
                40L,
                List.of());
        var service = new ProjectService(projects, accounts, queries, taskQueries, Clock.systemUTC());
        when(projects.findLockedById(projectId)).thenReturn(Optional.of(project));
        when(queries.taskContext(actorUserId, project)).thenReturn(expected);

        var actual = service.taskMutationContext(actorUserId, projectId);

        assertSame(expected, actual);
        verify(projects).findLockedById(projectId);
        verify(projects, never()).findById(projectId);
        verify(queries).taskContext(actorUserId, project);
    }
}
