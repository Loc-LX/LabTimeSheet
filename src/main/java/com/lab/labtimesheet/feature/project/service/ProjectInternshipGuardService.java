package com.lab.labtimesheet.feature.project.service;

import com.lab.labtimesheet.feature.project.model.ProjectStatus;
import com.lab.labtimesheet.feature.project.model.dto.ProjectInternshipGuard;
import com.lab.labtimesheet.feature.project.repository.ProjectRepository;
import java.util.HashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exposes the Project-owned facts required by account terminal transitions without leaking
 * Project entities or repositories to the Account feature.
 */
@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class ProjectInternshipGuardService {

    private final ProjectRepository projects;

    /**
     * Collects current leadership and retained membership facts for one Intern.
     *
     * @param internUserId Intern account identifier
     * @return immutable guard facts
     */
    @Transactional(readOnly = true)
    public ProjectInternshipGuard guardForInternship(long internUserId) {
        boolean currentLeader = false;
        Set<Long> membershipIds = new HashSet<>();
        for (var project : projects.findAll()) {
            project.memberships().stream()
                    .filter(membership -> membership.internUserId() == internUserId)
                    .map(membership -> membership.id())
                    .filter(java.util.Objects::nonNull)
                    .forEach(membershipIds::add);
            if (project.status() != ProjectStatus.COMPLETED
                    && project.currentLeader().internUserId() == internUserId) {
                currentLeader = true;
            }
        }
        return new ProjectInternshipGuard(currentLeader, membershipIds);
    }
}
