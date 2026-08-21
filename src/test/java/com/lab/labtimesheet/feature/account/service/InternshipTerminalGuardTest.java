package com.lab.labtimesheet.feature.account.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import com.lab.labtimesheet.feature.project.model.dto.ProjectInternshipGuard;
import com.lab.labtimesheet.feature.project.service.ProjectInternshipGuardService;
import com.lab.labtimesheet.feature.task.service.TaskQueryService;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class InternshipTerminalGuardTest {

    @Mock
    private ProjectInternshipGuardService projects;

    @Mock
    private TaskQueryService tasks;

    @InjectMocks
    private InternshipTerminalGuard guard;

    @Test
    void currentProjectLeadershipBlocksTerminalActionWithSafeReason() {
        given(projects.guardForInternship(42L)).willReturn(new ProjectInternshipGuard(true, Set.of(7L)));

        assertThatThrownBy(() -> guard.assertAllowed(42L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Internship completion or withdrawal is blocked while the Intern leads a Project");
    }

    @Test
    void unfinishedTasksBlockTerminalActionWithSafeReason() {
        given(projects.guardForInternship(42L)).willReturn(new ProjectInternshipGuard(false, Set.of(7L, 9L)));
        given(tasks.countUnfinishedTasksForMemberships(Set.of(7L, 9L))).willReturn(1L);

        assertThatThrownBy(() -> guard.assertAllowed(42L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Internship completion or withdrawal is blocked while unfinished Tasks remain");
    }

    @Test
    void noCurrentLeadershipOrUnfinishedTasksAllowsTerminalAction() {
        given(projects.guardForInternship(42L)).willReturn(new ProjectInternshipGuard(false, Set.of()));

        guard.assertAllowed(42L);

        assertThat(true).isTrue();
    }
}
