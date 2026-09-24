package com.lab.labtimesheet.feature.project.service;

import com.lab.labtimesheet.feature.internship.model.dto.InternshipLifecycleGuard;
import com.lab.labtimesheet.feature.internship.service.InternshipLifecycleReadiness;
import com.lab.labtimesheet.feature.project.exception.ProjectRuleViolationException;
import com.lab.labtimesheet.feature.project.model.ProjectStatus;
import com.lab.labtimesheet.feature.project.model.dto.ProjectMembershipIntervalView;
import com.lab.labtimesheet.feature.project.model.entity.ProjectMembershipEntity;
import com.lab.labtimesheet.feature.project.repository.ProjectRepository;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Computes Internship terminal-readiness facts from Project membership, leadership and Tasks. */
@Service
@RequiredArgsConstructor
public class ProjectInternshipLifecycleReadiness implements InternshipLifecycleReadiness {

    private final ProjectRepository projects;
    private final TaskTransferService taskTransfers;

    /** Returns the current Project and Task facts for explanatory account UI. */
    @Override
    @Transactional(readOnly = true)
    public InternshipLifecycleGuard preview(long internUserId) {
        var currentMemberships = currentMemberships(internUserId);
        boolean currentLeader = currentMemberships.stream().anyMatch(interval -> {
            // The account page maps IllegalArgumentException to its not-found response.
            var route = projects.findMutationRouteById(interval.projectId())
                    .orElseThrow(() -> new IllegalArgumentException("Project membership changed"));
            return route.currentLeaderUserId() != null && route.currentLeaderUserId() == internUserId;
        });
        long unfinishedTaskCount = currentMemberships.stream()
                .mapToLong(interval -> taskTransfers.unfinishedCount(
                        interval.projectId(), interval.membershipId()))
                .sum();
        return new InternshipLifecycleGuard(currentLeader, unfinishedTaskCount);
    }

    /** Locks current Projects in identifier order and returns stable terminal-readiness facts. */
    @Override
    @Transactional
    public InternshipLifecycleGuard lockForTerminalAction(long internUserId) {
        boolean currentLeader = false;
        long unfinishedTaskCount = 0;
        for (var interval : currentMemberships(internUserId)) {
            var project = projects.findLockedById(interval.projectId())
                    .orElseThrow(() -> new IllegalStateException("Project membership changed; retry the action"));
            ProjectMembershipEntity membership;
            try {
                membership = project.currentMember(internUserId);
            } catch (ProjectRuleViolationException failure) {
                throw new IllegalStateException("Project membership changed; retry the action", failure);
            }
            if (!Objects.equals(membership.id(), interval.membershipId())) {
                throw new IllegalStateException("Project membership changed; retry the action");
            }
            if (project.status() != ProjectStatus.COMPLETED
                    && Objects.equals(project.currentLeader().id(), membership.id())) {
                currentLeader = true;
            }
            unfinishedTaskCount += taskTransfers.unfinishedCount(interval.projectId(), interval.membershipId());
        }
        return new InternshipLifecycleGuard(currentLeader, unfinishedTaskCount);
    }

    private List<ProjectMembershipIntervalView> currentMemberships(long internUserId) {
        return projects.findMembershipIntervalsByInternUserId(internUserId).stream()
                .filter(interval -> interval.leftAt() == null)
                .toList();
    }
}
