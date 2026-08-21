package com.lab.labtimesheet.feature.project.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.lab.labtimesheet.feature.project.model.ProjectInternEligibility;
import com.lab.labtimesheet.feature.project.model.entity.ProjectEntity;
import com.lab.labtimesheet.feature.project.repository.ProjectRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class ProjectInternshipGuardServiceTest {

    private static final Instant CREATED_AT = Instant.parse("2026-08-20T00:00:00Z");

    @Mock
    private ProjectRepository projects;

    @InjectMocks
    private ProjectInternshipGuardService guards;

    @Test
    void currentLeaderAndAllMembershipsAreExposedAsAReadOnlyDto() {
        var project = ProjectEntity.plan(
                10L,
                "Platform Project",
                null,
                LocalDate.of(2026, 8, 20),
                LocalDate.of(2026, 12, 31),
                new ProjectInternEligibility(42L, true),
                CREATED_AT);
        ReflectionTestUtils.setField(project.memberships().getFirst(), "id", 17L);
        given(projects.findAll()).willReturn(List.of(project));

        var result = guards.guardForInternship(42L);

        assertThat(result.currentLeader()).isTrue();
        assertThat(result.membershipIds()).containsExactly(17L);
    }

    @Test
    void anInternWithNoProjectRelationshipIsNotBlocked() {
        given(projects.findAll()).willReturn(List.of());

        var result = guards.guardForInternship(42L);

        assertThat(result.currentLeader()).isFalse();
        assertThat(result.membershipIds()).isEmpty();
    }
}
