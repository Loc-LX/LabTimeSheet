package com.lab.labtimesheet.feature.project.service;

import com.lab.labtimesheet.feature.account.model.dto.AccountIdentity;
import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.project.exception.ProjectAccessDeniedException;
import com.lab.labtimesheet.feature.project.model.ProjectStatus;
import com.lab.labtimesheet.feature.project.model.dto.ProjectDetail;
import com.lab.labtimesheet.feature.project.model.dto.ProjectActorView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectDashboardSummary;
import com.lab.labtimesheet.feature.project.model.dto.ProjectExitRequestHistoryView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectExitReadinessView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectHistoryView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectInvitationHistoryView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectLeadershipTermView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectMemberView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectMembershipIntervalView;
import com.lab.labtimesheet.feature.project.model.dto.PendingProjectInvitationView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectSummary;
import com.lab.labtimesheet.feature.project.model.dto.ProjectTaskContext;
import com.lab.labtimesheet.feature.project.model.dto.ProjectTaskMemberView;
import com.lab.labtimesheet.feature.project.model.entity.ProjectEntity;
import com.lab.labtimesheet.feature.project.repository.ProjectExitRequestRepository;
import com.lab.labtimesheet.feature.project.repository.ProjectInvitationRepository;
import com.lab.labtimesheet.feature.project.repository.ProjectRepository;
import com.lab.labtimesheet.feature.task.service.TaskQueryService;
import com.lab.labtimesheet.feature.task.service.TaskTransferService;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provides authorization-aware, DTO-only Project reads to MVC and other features.
 *
 * <p>Admins inspect all Projects, Mentors inspect only owned Projects, and Interns inspect open
 * Projects only while currently enrolled. Completed Projects remain visible to historical
 * members but expose no current Leader or active-member context.
 */
@Service
@RequiredArgsConstructor
public class ProjectQueryService {

    private final ProjectRepository projects;
    private final ProjectExitRequestRepository exitRequests;
    private final ProjectInvitationRepository invitations;
    private final AccountService accounts;
    private final TaskQueryService taskQueries;
    private final TaskTransferService taskTransfers;

    /**
     * Resolves an active authenticated account to its stable user identifier.
     *
     * @param email authenticated email address
     * @return active user identifier
     * @throws ProjectAccessDeniedException when no active identity is available
     */
    @Transactional(readOnly = true)
    public long authenticatedUserId(String email) {
        return authenticatedActor(email).userId();
    }

    /**
     * Resolves the active authenticated actor needed for role-aware Project navigation.
     *
     * @param email authenticated email address
     * @return user identifier and immutable global role
     * @throws ProjectAccessDeniedException when no active identity is available
     */
    @Transactional(readOnly = true)
    public ProjectActorView authenticatedActor(String email) {
        try {
            var actor = accounts.requireIdentityByEmail(email);
            if (!"ACTIVE".equals(actor.status().name())) {
                throw new ProjectAccessDeniedException();
            }
            return new ProjectActorView(actor.id(), actor.role().name());
        } catch (IllegalArgumentException exception) {
            throw new ProjectAccessDeniedException();
        }
    }

    /**
     * Lists Projects visible under the actor's current role and Project relationship.
     * Historical Intern membership grants visibility only to completed Projects.
     *
     * @param actorUserId active actor user identifier
     * @return ordered authorized summaries
     */
    @Transactional(readOnly = true)
    public List<ProjectSummary> listVisible(long actorUserId) {
        var actor = activeActor(actorUserId);
        return visibleProjects(actor, actorUserId).stream().map(ProjectQueryService::summary).toList();
    }

    /**
     * Returns one authorized Project detail. Completed Projects have no current Leader and never
     * grant mutation capability, including to their owning Mentor.
     *
     * @param actorUserId active actor user identifier
     * @param projectId requested Project identifier
     * @return authorized detail
     * @throws ProjectAccessDeniedException for missing and unauthorized identifiers alike
     */
    @Transactional(readOnly = true)
    public ProjectDetail detail(long actorUserId, long projectId) {
        var project = visibleProject(actorUserId, projectId);
        var completed = project.status() == ProjectStatus.COMPLETED;
        return new ProjectDetail(
                project.id(),
                project.name(),
                project.description(),
                project.status().name(),
                project.startDate(),
                project.endDate(),
                displayName(project.mentorUserId()),
                completed ? null : displayName(project.currentLeader().internUserId()),
                !completed && project.mentorUserId() == actorUserId);
    }

    /**
     * Returns membership interval history for an authorized Project. Completed history marks no
     * membership as current Leader because completion closes the final leadership term.
     *
     * @param actorUserId active actor user identifier
     * @param projectId requested Project identifier
     * @return membership history in aggregate order
     */
    @Transactional(readOnly = true)
    public List<ProjectMemberView> members(long actorUserId, long projectId) {
        var project = visibleProject(actorUserId, projectId);
        Long leaderUserId = project.status() == ProjectStatus.COMPLETED
                ? null
                : project.currentLeader().internUserId();
        return project.memberships().stream()
                .map(membership -> new ProjectMemberView(
                        membership.id(),
                        membership.internUserId(),
                        displayName(membership.internUserId()),
                        membership.joinedAt(),
                        membership.leftAt(),
                        membership.isCurrent()
                                && leaderUserId != null
                                && membership.internUserId() == leaderUserId,
                        membership.addedByUserId(),
                        membership.removedByMentorUserId()))
                .toList();
    }

    /**
     * Returns every retained membership interval belonging to the authenticated Intern across
     * Projects. This is the DTO-only source for cross-Project Task work and history queries.
     *
     * <p>Only an active account with the global Intern role may use this boundary. Internship
     * completion does not erase retained intervals, so this read remains available to an active
     * completed Intern while mutation eligibility is enforced by the Account boundary.</p>
     *
     * @param actorUserId authenticated Intern account identifier
     * @return immutable interval projections ordered by Project then membership identifier
     * @throws ProjectAccessDeniedException when the actor is missing, inactive, or not an Intern
     */
    @Transactional(readOnly = true)
    public List<ProjectMembershipIntervalView> membershipIntervals(long actorUserId) {
        var actor = activeActor(actorUserId);
        if (!"INTERN".equals(actor.role().name())) {
            throw new ProjectAccessDeniedException();
        }
        return projects.findMembershipIntervalsByInternUserId(actorUserId);
    }

    /**
     * Lists pending invitations addressed to the exact authenticated Intern.
     *
     * <p>This route does not require existing Project visibility because an invitee is not yet a
     * member. Active-Intern authorization occurs before reading addressed rows; response mutations
     * repeat target, Project, term, eligibility, and pending-state checks under lock.</p>
     *
     * @param actorUserId authenticated active Intern account
     * @return newest-first actionable invitations addressed to that Intern
     * @throws ProjectAccessDeniedException when the actor is inactive or not an Intern
     */
    @Transactional(readOnly = true)
    public List<PendingProjectInvitationView> pendingInvitations(long actorUserId) {
        var actor = activeActor(actorUserId);
        if (!"INTERN".equals(actor.role().name())) {
            throw new ProjectAccessDeniedException();
        }
        return invitations.findByInvitedInternUserIdAndStatusOrderByCreatedAtDescIdDesc(
                        actorUserId, com.lab.labtimesheet.feature.project.model.InvitationStatus.PENDING)
                .stream()
                .map(invitation -> {
                    var project = projects.findById(invitation.projectId())
                            .orElseThrow(ProjectAccessDeniedException::new);
                    return new PendingProjectInvitationView(
                            invitation.id(),
                            project.id(),
                            project.name(),
                            displayName(invitation.issuingLeadershipTerm().internUserId()),
                            invitation.createdAt());
                })
                .toList();
    }

    /**
     * Returns retained leadership terms for an authorized Project, newest first.
     *
     * @param actorUserId active actor user identifier
     * @param projectId requested Project identifier
     * @return leadership history, including closed terms
     */
    @Transactional(readOnly = true)
    public List<ProjectLeadershipTermView> leadership(long actorUserId, long projectId) {
        return visibleProject(actorUserId, projectId).leadershipTerms().stream()
                .sorted((left, right) -> right.startedAt().compareTo(left.startedAt()))
                .map(term -> new ProjectLeadershipTermView(
                        term.id(),
                        displayName(term.internUserId()),
                        term.startedAt(),
                        term.endedAt(),
                        term.appointedByMentorUserId(),
                        term.endedByMentorUserId()))
                .toList();
    }

    /**
     * Returns one authorized Project History snapshot from retained Project and Task facts.
     *
     * <p>Open visibility is limited to Admin, the owning Mentor, and current members. A former
     * member becomes eligible only after Project completion through the same authorization boundary
     * used by detail and membership reads. Task rows are obtained through the Task DTO service;
     * this method never imports Task persistence.</p>
     *
     * @param actorUserId active authenticated viewer
     * @param projectId requested Project identifier
     * @return immutable retained history snapshot
     * @throws ProjectAccessDeniedException when the viewer is outside the exact AUTH-006 scope
     */
    @Transactional(readOnly = true)
    public ProjectHistoryView history(long actorUserId, long projectId) {
        var project = visibleProject(actorUserId, projectId);
        var memberships = project.memberships().stream()
                .map(membership -> new ProjectMemberView(
                        membership.id(),
                        membership.internUserId(),
                        displayName(membership.internUserId()),
                        membership.joinedAt(),
                        membership.leftAt(),
                        project.status() != ProjectStatus.COMPLETED
                                && membership.isCurrent()
                                && project.currentLeader().id().equals(membership.id()),
                        membership.addedByUserId(),
                        membership.removedByMentorUserId()))
                .toList();
        var leadership = project.leadershipTerms().stream()
                .sorted((left, right) -> right.startedAt().compareTo(left.startedAt()))
                .map(term -> new ProjectLeadershipTermView(
                        term.id(),
                        displayName(term.internUserId()),
                        term.startedAt(),
                        term.endedAt(),
                        term.appointedByMentorUserId(),
                        term.endedByMentorUserId()))
                .toList();
        var invitationHistory = invitations.findByProject_IdOrderByCreatedAtAscIdAsc(projectId).stream()
                .map(invitation -> new ProjectInvitationHistoryView(
                        invitation.id(),
                        invitation.invitedInternUserId(),
                        invitation.issuingLeadershipTerm().id(),
                        invitation.status(),
                        invitation.acceptedMembership() == null
                                ? null : invitation.acceptedMembership().id(),
                        invitation.resolvedAt(),
                        invitation.resolvedByUserId(),
                        invitation.resolutionCode(),
                        invitation.createdAt(),
                        invitation.updatedAt()))
                .toList();
        var exitHistory = exitRequests.findByProject_IdOrderByCreatedAtAscIdAsc(projectId).stream()
                .map(request -> new ProjectExitRequestHistoryView(
                        request.id(),
                        request.targetMembershipId(),
                        request.requesterMembershipId(),
                        request.requestType(),
                        request.reason(),
                        request.status(),
                        request.resolutionNote(),
                        request.resolvedAt(),
                        request.resolvedByUserId(),
                        request.createdAt(),
                        request.updatedAt()))
                .toList();
        return new ProjectHistoryView(
                project.id(),
                memberships,
                leadership,
                invitationHistory,
                exitHistory,
                taskQueries.history(projectId));
    }

    /**
     * Returns pending-exit readiness facts for an authorized Project viewer.
     *
     * <p>The snapshot exposes only retained request IDs, membership IDs, current-Leader status,
     * and the Task-owned unfinished count. It is informational and cannot replace the locked
     * approval boundary in {@link ProjectService#approveExit(long, long, String)}.</p>
     *
     * @param actorUserId active authenticated viewer
     * @param projectId requested Project identifier
     * @return pending requests in stable request order
     */
    @Transactional(readOnly = true)
    public List<ProjectExitReadinessView> exitReadiness(long actorUserId, long projectId) {
        var project = visibleProject(actorUserId, projectId);
        var currentLeaderId = project.status() == ProjectStatus.COMPLETED
                ? null : project.currentLeader().id();
        return exitRequests.findByProject_IdOrderByCreatedAtAscIdAsc(projectId).stream()
                .filter(request -> request.isPending())
                .map(request -> {
                    boolean leader = currentLeaderId != null
                            && currentLeaderId.equals(request.targetMembershipId());
                    long unfinished = taskTransfers.unfinishedCount(projectId, request.targetMembershipId());
                    return new ProjectExitReadinessView(
                            request.id(),
                            request.targetMembershipId(),
                            leader,
                            unfinished,
                            !leader && unfinished == 0);
                })
                .toList();
    }

    /**
     * Returns the DTO-only Project facts needed for Task reads. Open Projects include the current
     * Leader membership and active eligible members; completed Projects return a null Leader and
     * an empty active-member list while remaining visible to former members.
     *
     * @param actorUserId active actor user identifier
     * @param projectId requested Project identifier
     * @return authorized Task context
     */
    @Transactional(readOnly = true)
    public ProjectTaskContext taskContext(long actorUserId, long projectId) {
        var project = projects.findById(projectId).orElseThrow(ProjectAccessDeniedException::new);
        requireVisibleProject(actorUserId, project);
        return taskContext(actorUserId, project,
                exitRequests.findPendingTargetMembershipIdsByProjectId(projectId));
    }

    ProjectTaskContext taskContext(long actorUserId, ProjectEntity project) {
        return taskContext(actorUserId, project, Set.of());
    }

    /**
     * Maps a Project already locked by the mutation owner into the DTO consumed by Task.
     *
     * <p>The caller must acquire the Project write lock before reading the pending target IDs and
     * retain that lock through the outer transaction. This preserves one stable authorization
     * snapshot while Task locks its own rows; this mapper does not access Project persistence from
     * the Task feature.
     *
     * @param actorUserId authenticated actor
     * @param project locked Project aggregate
     * @param pendingExitMembershipIds pending target IDs read after the Project lock
     * @return immutable DTO-only Task context
     */
    ProjectTaskContext taskContext(
            long actorUserId, ProjectEntity project, Set<Long> pendingExitMembershipIds) {
        requireVisibleProject(actorUserId, project);
        if (project.status() == ProjectStatus.COMPLETED) {
            return new ProjectTaskContext(
                    project.id(),
                    project.mentorUserId(),
                    project.status().name(),
                    project.startDate(),
                    project.endDate(),
                    null,
                    List.of(),
                    Set.of());
        }
        var activeMembers = project.memberships().stream()
                .filter(membership -> membership.isCurrent() && isEligibleIntern(membership.internUserId()))
                .map(membership -> new ProjectTaskMemberView(
                        membership.id(),
                        membership.internUserId(),
                        displayName(membership.internUserId()),
                        membership.joinedAt()))
                .toList();
        var currentLeader = project.currentLeader();
        var currentLeaderMembershipId = activeMembers.stream()
                .filter(member -> member.membershipId() == currentLeader.id())
                .map(ProjectTaskMemberView::membershipId)
                .findFirst()
                .orElse(null);
        return new ProjectTaskContext(
                project.id(),
                project.mentorUserId(),
                project.status().name(),
                project.startDate(),
                project.endDate(),
                currentLeaderMembershipId,
                activeMembers,
                pendingExitMembershipIds);
    }

    /**
     * Computes role-scoped dashboard counts from current active Project relationships.
     * Historical memberships never contribute to current Intern or Mentor metrics.
     *
     * @param actorUserId active actor user identifier
     * @return active Project count and, for Mentors, distinct eligible active-member count
     */
    @Transactional(readOnly = true)
    public ProjectDashboardSummary dashboardSummary(long actorUserId) {
        var actor = activeActor(actorUserId);
        var activeProjects = visibleProjects(actor, actorUserId).stream()
                .filter(project -> project.status() == ProjectStatus.ACTIVE)
                .filter(project -> !"INTERN".equals(actor.role().name())
                        || (accounts.isEligibleIntern(actorUserId) && project.hasCurrentMember(actorUserId)))
                .toList();
        var distinctActiveMembers = "MENTOR".equals(actor.role().name())
                ? activeProjects.stream()
                        .flatMap(project -> project.memberships().stream())
                        .filter(membership -> membership.isCurrent()
                                && accounts.isEligibleIntern(membership.internUserId()))
                        .map(membership -> membership.internUserId())
                        .distinct()
                        .count()
                : 0L;
        return new ProjectDashboardSummary(activeProjects.size(), distinctActiveMembers);
    }

    private ProjectEntity visibleProject(long actorUserId, long projectId) {
        var project = projects.findById(projectId).orElseThrow(ProjectAccessDeniedException::new);
        requireVisibleProject(actorUserId, project);
        return project;
    }

    private void requireVisibleProject(long actorUserId, ProjectEntity project) {
        var actor = activeActor(actorUserId);
        var visible = "ADMIN".equals(actor.role().name())
                || ("MENTOR".equals(actor.role().name()) && project.mentorUserId() == actorUserId)
                || ("INTERN".equals(actor.role().name())
                        && (project.hasCurrentMember(actorUserId)
                                || (project.status() == ProjectStatus.COMPLETED
                                        && project.hasEverHadMember(actorUserId))));
        if (!visible) {
            throw new ProjectAccessDeniedException();
        }
    }

    private List<ProjectEntity> visibleProjects(AccountIdentity actor, long actorUserId) {
        return switch (actor.role().name()) {
            case "ADMIN" -> projects.findAllByOrderByUpdatedAtDescIdDesc();
            case "MENTOR" -> projects.findByMentorUserIdOrderByUpdatedAtDescIdDesc(actorUserId);
            case "INTERN" -> projects.findVisibleToIntern(actorUserId);
            default -> throw new ProjectAccessDeniedException();
        };
    }

    private AccountIdentity activeActor(long actorUserId) {
        try {
            var actor = accounts.requireIdentityById(actorUserId);
            if (!"ACTIVE".equals(actor.status().name())) {
                throw new ProjectAccessDeniedException();
            }
            return actor;
        } catch (IllegalArgumentException exception) {
            throw new ProjectAccessDeniedException();
        }
    }

    private String displayName(long userId) {
        try {
            return accounts.requireIdentityById(userId).displayName();
        } catch (IllegalArgumentException exception) {
            throw new ProjectAccessDeniedException();
        }
    }

    private boolean isEligibleIntern(long userId) {
        return accounts.isEligibleIntern(userId);
    }

    private static ProjectSummary summary(ProjectEntity project) {
        return new ProjectSummary(
                project.id(),
                project.name(),
                project.status().name(),
                project.startDate(),
                project.endDate());
    }
}
