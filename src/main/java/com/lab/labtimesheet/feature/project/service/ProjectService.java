package com.lab.labtimesheet.feature.project.service;

import com.lab.labtimesheet.feature.account.model.AccountStatus;
import com.lab.labtimesheet.feature.account.model.GlobalRole;
import com.lab.labtimesheet.feature.account.model.dto.LockedAccountMutationEligibility;
import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.project.exception.ProjectAccessDeniedException;
import com.lab.labtimesheet.feature.project.exception.ProjectRuleViolationException;
import com.lab.labtimesheet.feature.project.model.InvitationResolutionCode;
import com.lab.labtimesheet.feature.project.model.InvitationResponse;
import com.lab.labtimesheet.feature.project.model.InvitationStatus;
import com.lab.labtimesheet.feature.project.model.ProjectExitRequestStatus;
import com.lab.labtimesheet.feature.project.model.ProjectExitRequestType;
import com.lab.labtimesheet.feature.project.model.ProjectInternEligibility;
import com.lab.labtimesheet.feature.project.model.dto.ProjectCreateCommand;
import com.lab.labtimesheet.feature.project.model.dto.ProjectExitRequestRoute;
import com.lab.labtimesheet.feature.project.model.dto.ProjectInvitationRoute;
import com.lab.labtimesheet.feature.project.model.dto.ProjectMutationRoute;
import com.lab.labtimesheet.feature.project.model.dto.ProjectTaskContext;
import com.lab.labtimesheet.feature.project.model.entity.ProjectEntity;
import com.lab.labtimesheet.feature.project.model.entity.ProjectExitRequestEntity;
import com.lab.labtimesheet.feature.project.model.entity.ProjectInvitationEntity;
import com.lab.labtimesheet.feature.project.model.entity.ProjectMembershipEntity;
import com.lab.labtimesheet.feature.project.repository.ProjectExitRequestRepository;
import com.lab.labtimesheet.feature.project.repository.ProjectInvitationRepository;
import com.lab.labtimesheet.feature.project.repository.ProjectRepository;
import com.lab.labtimesheet.feature.task.service.TaskQueryService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Executes Project aggregate mutations under Spring-managed transactions.
 *
 * <p>Mutation methods first obtain any Account and Intern-profile lifecycle locks through the
 * Account service's immutable DTO boundary, in ascending account/profile order, and only then
 * lock the Project aggregate. Account and Task facts arrive through public feature services;
 * Project never imports their repositories or entities.
 */
@Service
@RequiredArgsConstructor
public class ProjectService {
    private final ProjectRepository projects;
    private final ProjectInvitationRepository invitations;
    private final ProjectExitRequestRepository exitRequests;
    private final AccountService accounts;
    private final ProjectQueryService queries;
    private final TaskQueryService taskQueries;
    private final Clock clock;

    /**
     * Atomically creates a planned Mentor-owned Project, eligible initial membership, and first
     * leadership term. {@code saveAndFlush} exposes database invariant violations before commit.
     *
     * @param actorUserId authenticated active Mentor creating and owning the Project
     * @param command validated creation values
     * @return generated Project identifier
     * @throws ProjectAccessDeniedException when the actor is not an active Mentor
     * @throws com.lab.labtimesheet.feature.project.exception.ProjectRuleViolationException when
     *         dates, name, or initial-Leader eligibility violate the aggregate rules
     */
    @Transactional
    public long create(long actorUserId, ProjectCreateCommand command) {
        var lockedAccounts = lockAccountsForTargetMutation(
                List.of(actorUserId, command.initialLeaderUserId()));
        requireActiveMentor(snapshotFor(lockedAccounts, actorUserId));
        var initialLeader = snapshotFor(lockedAccounts, command.initialLeaderUserId());
        requireEligibleInternForProjectTarget(initialLeader);
        var project = ProjectEntity.plan(
                actorUserId,
                command.name(),
                command.description(),
                command.startDate(),
                command.endDate(),
                projectInternEligibility(initialLeader),
                clock.instant());
        return projects.saveAndFlush(project).id();
    }

    /**
     * Adds one eligible Intern as a current member while holding the Project write lock.
     * The same Intern may belong to other Projects, but duplicate current membership in this
     * Project is rejected before flush.
     *
     * @param actorUserId authenticated owning Mentor
     * @param projectId Project to update
     * @param internUserId Intern selected for direct addition
     */
    @Transactional
    public void addMember(long actorUserId, long projectId, long internUserId) {
        addMembers(actorUserId, projectId, List.of(internUserId));
    }

    /**
     * Adds a complete selection of eligible nonmembers while holding one Project write lock.
     * Account and Intern-profile rows for the actor and selection are locked in Account-owned
     * ascending order before the Project lock. Every identifier is revalidated after owner
     * authorization and before the aggregate changes, so missing, duplicate, stale, ineligible,
     * or current-member selections leave membership unchanged.
     *
     * @param actorUserId authenticated owning Mentor
     * @param projectId Project to update
     * @param internUserIds distinct eligible Intern account identifiers
     * @throws com.lab.labtimesheet.feature.project.exception.ProjectRuleViolationException when
     *         the selection is null, empty, malformed, duplicate, stale, ineligible, or already
     *         contains a current member
     */
    @Transactional
    public void addMembers(long actorUserId, long projectId, List<Long> internUserIds) {
        if (internUserIds == null || internUserIds.isEmpty()) {
            throw new ProjectRuleViolationException("Select at least one Intern");
        }
        if (internUserIds.stream().anyMatch(userId -> userId == null || userId <= 0)
                || new HashSet<>(internUserIds).size() != internUserIds.size()) {
            throw new ProjectRuleViolationException("Intern selection is invalid");
        }

        var route = projectRoute(projectId);
        if (route.mentorUserId() != actorUserId) {
            throw new ProjectAccessDeniedException();
        }
        var lockedAccounts = lockAccountsForTargetMutation(concat(actorUserId, internUserIds));
        requireActiveMentor(snapshotFor(lockedAccounts, actorUserId));
        var project = lockedProject(projectId);
        project.authorizeOwner(actorUserId);
        var selectedInterns = internUserIds.stream()
                .map(userId -> snapshotFor(lockedAccounts, userId))
                .peek(this::requireEligibleInternForProjectTarget)
                .map(this::projectInternEligibility)
                .toList();
        if (selectedInterns.stream().anyMatch(intern -> project.hasCurrentMember(intern.userId()))) {
            throw new ProjectRuleViolationException("One or more selected Interns are no longer eligible");
        }

        var addedAt = clock.instant();
        selectedInterns.forEach(intern -> {
            project.addMember(actorUserId, intern, addedAt);
            supersedePendingInvitation(projectId, intern.userId(), actorUserId, addedAt);
        });
        projects.flush();
    }

    /**
     * Issues a non-expiring invitation from the authenticated current Leader's stored term.
     *
     * @param actorUserId authenticated current Leader
     * @param projectId mutable Project receiving the invitation
     * @param invitedInternUserId eligible Intern to invite
     * @return generated invitation identifier
     * @throws ProjectAccessDeniedException when the actor is not the current Leader
     * @throws ProjectRuleViolationException when the Project, target, or pending-pair rule fails
     */
    @Transactional
    public long issueInvitation(long actorUserId, long projectId, long invitedInternUserId) {
        var route = projectRoute(projectId);
        if (!Objects.equals(route.currentLeaderUserId(), actorUserId)) {
            throw new ProjectAccessDeniedException();
        }
        var lockedAccounts = lockAccountsForTargetMutation(List.of(actorUserId, invitedInternUserId));
        var actor = snapshotFor(lockedAccounts, actorUserId);
        var invitee = snapshotFor(lockedAccounts, invitedInternUserId);
        requireEligibleInternForProjectActor(actor);
        var project = lockedProject(projectId);
        if (project.currentLeader().internUserId() != actorUserId) {
            throw new ProjectAccessDeniedException();
        }
        requireOpenProject(project);
        requireEligibleInternForProjectTarget(invitee);
        if (project.hasCurrentMember(invitedInternUserId)) {
            throw new ProjectRuleViolationException("Intern is already a current Project member");
        }
        if (invitations.findLockedPending(projectId, invitedInternUserId).isPresent()) {
            throw new ProjectRuleViolationException("A pending invitation already exists");
        }
        var invitation = ProjectInvitationEntity.pending(
                project, invitee.userId(), project.currentLeadershipTerm(), clock.instant());
        return invitations.saveAndFlush(invitation).id();
    }

    /**
     * Revokes a pending invitation owned by the issuing Leader or owning Mentor.
     * The authenticated actor Account row is locked before the Project and invitation rows;
     * terminal state is inspected only after the actor's Project relationship is authorized.
     *
     * @param actorUserId authenticated current issuing Leader or owning Mentor
     * @param invitationId invitation identifier
     * @throws ProjectAccessDeniedException when the actor is outside the invitation context
     * @throws ProjectRuleViolationException when the invitation is already terminal
     */
    @Transactional
    public void revokeInvitation(long actorUserId, long invitationId) {
        var route = invitationRoute(invitationId);
        var actor = lockAccount(actorUserId);
        requireActiveAccount(actor);
        var project = lockedProject(route.projectId());
        var invitation = invitations.findLockedById(invitationId)
                .orElseThrow(ProjectAccessDeniedException::new);
        boolean owner = actor.role() == GlobalRole.MENTOR && project.mentorUserId() == actorUserId;
        boolean issuingLeader = actor.role() == GlobalRole.INTERN
                && invitation.issuingLeadershipTerm().isCurrent()
                && invitation.issuingLeadershipTerm().internUserId() == actorUserId;
        if (!owner && !issuingLeader) {
            throw new ProjectAccessDeniedException();
        }
        if (issuingLeader) {
            requireEligibleInternForProjectActor(actor);
        }
        if (!invitation.isPending()) {
            throw new ProjectRuleViolationException("Invitation is no longer pending");
        }
        invitation.resolve(
                InvitationStatus.REVOKED,
                owner ? InvitationResolutionCode.MENTOR_REVOKED : InvitationResolutionCode.INVITER_REVOKED,
                actorUserId,
                null,
                clock.instant());
        invitations.flush();
    }

    /**
     * Applies an authenticated response from only the invited Intern.
     *
     * <p>Account and Intern-profile rows for the authenticated actor and invited Intern are
     * locked in ascending order before the Project lock. Acceptance rechecks the current issuing
     * term, eligibility, and membership while the Project lock is held. Email links therefore
     * cannot act as bearer join tokens.
     *
     * @param actorUserId authenticated response actor
     * @param invitationId invitation identifier
     * @param response accept or decline choice
     */
    @Transactional
    public void respondToInvitation(long actorUserId, long invitationId, InvitationResponse response) {
        if (response == null) {
            throw new ProjectRuleViolationException("Invitation response is required");
        }
        var route = invitationRoute(invitationId);
        if (route.invitedInternUserId() != actorUserId) {
            throw new ProjectAccessDeniedException();
        }
        var lockedAccounts = lockAccounts(List.of(actorUserId, route.invitedInternUserId()));
        var actor = snapshotFor(lockedAccounts, actorUserId);
        var invitee = snapshotFor(lockedAccounts, route.invitedInternUserId());
        var project = lockedProject(route.projectId());
        var invitation = invitations.findLockedById(invitationId)
                .orElseThrow(ProjectAccessDeniedException::new);
        if (actor.role() != GlobalRole.INTERN || invitation.invitedInternUserId() != actorUserId) {
            throw new ProjectAccessDeniedException();
        }
        requireActiveAccount(actor);
        if (!eligibleInternForProjectMutation(invitee)) {
            if (!invitation.isPending()) {
                throw new ProjectRuleViolationException("Invitation is no longer pending");
            }
            invitation.resolve(
                    InvitationStatus.REVOKED,
                    InvitationResolutionCode.INVITEE_INELIGIBLE,
                    null,
                    null,
                    clock.instant());
            invitations.flush();
            return;
        }
        if (!invitation.isPending()) {
            throw new ProjectRuleViolationException("Invitation is no longer pending");
        }
        if (project.status() == com.lab.labtimesheet.feature.project.model.ProjectStatus.COMPLETED) {
            throw new ProjectRuleViolationException("Completed Projects are read-only");
        }
        if (!invitation.issuingLeadershipTerm().isCurrent()) {
            invitation.resolve(
                    InvitationStatus.REVOKED,
                    InvitationResolutionCode.LEADER_CHANGED,
                    null,
                    null,
                    clock.instant());
            invitations.flush();
            return;
        }
        if (project.hasCurrentMember(actorUserId)) {
            invitation.resolve(
                    InvitationStatus.REVOKED,
                    InvitationResolutionCode.INVITEE_INELIGIBLE,
                    null,
                    null,
                    clock.instant());
            invitations.flush();
            return;
        }
        if (response == InvitationResponse.DECLINE) {
            invitation.resolve(
                    InvitationStatus.DECLINED,
                    InvitationResolutionCode.INVITEE_DECLINED,
                    actorUserId,
                    null,
                    clock.instant());
            invitations.flush();
            return;
        }
        var membership = project.acceptMembership(actorUserId, clock.instant());
        projects.flush();
        invitation.resolve(
                InvitationStatus.ACCEPTED,
                InvitationResolutionCode.INVITEE_ACCEPTED,
                actorUserId,
                membership,
                clock.instant());
        invitations.flush();
    }

    /**
     * Creates a Leader-requested removal of another current Project member.
     * The current Leader Account and Intern profile remain locked through Project authorization
     * and request persistence; the target membership interval is not closed by this operation.
     *
     * @param actorUserId authenticated current Leader
     * @param projectId Project identifier
     * @param targetMembershipId current member requested for removal
     * @param reason nonblank retained reason
     * @return generated exit-request identifier
     */
    @Transactional
    public long requestMemberRemoval(
            long actorUserId, long projectId, long targetMembershipId, String reason) {
        var actor = lockAccount(actorUserId);
        var project = lockedProject(projectId);
        var leader = requireCurrentLeader(project, actorUserId, actor);
        requireOpenProject(project);
        var target = project.membership(targetMembershipId);
        if (!target.isCurrent() || target.id().equals(leader.id())) {
            throw new ProjectRuleViolationException("Removal target must be another current member");
        }
        var normalizedReason = requireReason(reason);
        ensureNoPendingExit(targetMembershipId);
        var request = ProjectExitRequestEntity.pending(
                project,
                target,
                leader,
                ProjectExitRequestType.LEADER_REMOVAL,
                normalizedReason,
                clock.instant());
        return exitRequests.saveAndFlush(request).id();
    }

    /**
     * Creates an authenticated member's own leave request without closing membership.
     * The requesting Account and Intern profile are locked before the Project, and completed or
     * otherwise inactive Interns are denied before any request state is inspected or changed.
     *
     * @param actorUserId authenticated current member
     * @param projectId Project identifier
     * @param reason nonblank retained reason
     * @return generated exit-request identifier
     */
    @Transactional
    public long requestOwnLeave(long actorUserId, long projectId, String reason) {
        var actor = lockAccount(actorUserId);
        var project = lockedProject(projectId);
        var requester = currentMembershipForUser(project, actorUserId, actor);
        requireOpenProject(project);
        var normalizedReason = requireReason(reason);
        ensureNoPendingExit(requester.id());
        var request = ProjectExitRequestEntity.pending(
                project,
                requester,
                requester,
                ProjectExitRequestType.MEMBER_LEAVE,
                normalizedReason,
                clock.instant());
        return exitRequests.saveAndFlush(request).id();
    }

    /**
     * Cancels a pending request only by its original requester.
     * The authenticated Account and Intern profile are locked before the Project and request;
     * requester authorization precedes the terminal-state check.
     *
     * @param actorUserId authenticated requester
     * @param requestId exit-request identifier
     */
    @Transactional
    public void cancelExit(long actorUserId, long requestId) {
        var route = exitRequestRoute(requestId);
        var actor = lockAccount(actorUserId);
        var project = lockedProject(route.projectId());
        var request = exitRequests.findLockedById(requestId)
                .orElseThrow(ProjectAccessDeniedException::new);
        ProjectMembershipEntity requester;
        try {
            requester = project.membership(request.requesterMembershipId());
        } catch (ProjectRuleViolationException exception) {
            throw new ProjectAccessDeniedException();
        }
        if (request.requesterMembershipId() <= 0
                || !requester.isCurrent()
                || requester.internUserId() != actorUserId) {
            throw new ProjectAccessDeniedException();
        }
        requireEligibleInternForProjectActor(actor);
        requireOpenProject(project);
        if (!request.isPending()) {
            throw new ProjectRuleViolationException("Exit request is no longer pending");
        }
        request.resolve(ProjectExitRequestStatus.CANCELLED, null, actorUserId, clock.instant());
        exitRequests.flush();
    }

    /**
     * Replaces the current Leader with an eligible current member in one transaction.
     * The closed term is flushed before its replacement so PostgreSQL's immediate exclusion rule
     * observes exactly one current term. Mentor and replacement Account/profile rows are locked
     * in ascending order before the Project; Task assignments are not changed.
     *
     * @param actorUserId authenticated owning Mentor
     * @param projectId Project whose Leader changes
     * @param internUserId active same-Project replacement Intern
     */
    @Transactional
    public void changeLeader(long actorUserId, long projectId, long internUserId) {
        var route = projectRoute(projectId);
        if (route.mentorUserId() != actorUserId) {
            throw new ProjectAccessDeniedException();
        }
        var lockedAccounts = lockAccountsForTargetMutation(List.of(actorUserId, internUserId));
        requireActiveMentor(snapshotFor(lockedAccounts, actorUserId));
        var project = lockedProject(projectId);
        project.authorizeOwner(actorUserId);
        requireEligibleInternForProjectTarget(snapshotFor(lockedAccounts, internUserId));
        long previousTermId = project.currentLeadershipTerm().id();
        var change = project.prepareLeaderChange(
                actorUserId,
                projectInternEligibility(snapshotFor(lockedAccounts, internUserId)),
                clock.instant());

        // PostgreSQL rejects overlapping terms immediately. Flush the old term's
        // end before inserting its replacement; the transaction remains atomic.
        projects.flush();
        project.completeLeaderChange(actorUserId, change);
        projects.flush();
        invitations.findLockedPendingByTerm(projectId, previousTermId).forEach(invitation -> invitation.resolve(
                InvitationStatus.REVOKED,
                InvitationResolutionCode.LEADER_CHANGED,
                null,
                null,
                clock.instant()));
        invitations.flush();
    }

    /**
     * Locks all current-member Accounts and Intern profiles in ascending Account order, then
     * locks the Project and re-evaluates visibility, lifecycle, current leadership, active
     * eligible memberships, and pending-exit target IDs for a Task mutation. Pending targets stay
     * in the active-member list so existing Task rights remain valid; the separate ID set lets
     * Task reject only new/self-assignment. The all-current-member pass is intentionally bounded
     * by the Project's current membership cardinality and avoids a second Account-to-Project
     * lock-order cycle. When called inside {@code TaskService}'s transaction, every lock and DTO
     * snapshot remains held through the outer commit or rollback.
     *
     * @param actorUserId authenticated Task actor
     * @param projectId owning Project identifier
     * @return DTO-only locked mutation context
     * @throws ProjectAccessDeniedException for missing or unauthorized Projects
     */
    @Transactional
    public ProjectTaskContext taskMutationContext(long actorUserId, long projectId) {
        var route = projectRoute(projectId);
        var snapshotMemberIds = projects.findCurrentInternUserIdsByProjectId(projectId).stream()
                .collect(Collectors.toUnmodifiableSet());
        if (route.mentorUserId() != actorUserId && !snapshotMemberIds.contains(actorUserId)) {
            throw new ProjectAccessDeniedException();
        }
        lockAccounts(concat(actorUserId, snapshotMemberIds));
        var project = lockedProject(projectId);
        if (!snapshotMemberIds.equals(currentInternUserIds(project))) {
            throw new ProjectAccessDeniedException();
        }
        var pendingExitMembershipIds = exitRequests.findLockedPendingByProjectId(projectId).stream()
                .map(ProjectExitRequestEntity::targetMembershipId)
                .collect(Collectors.toUnmodifiableSet());
        return queries.taskContext(actorUserId, project, pendingExitMembershipIds);
    }

    /**
     * Activates a planned Project while holding its write lock. Current Account eligibility and
     * Task-assignee validity are checked inside the same transaction after all current-member
     * Account/profile locks are acquired in ascending order; any failure leaves the Project
     * planned and preserves Tasks and interval history.
     *
     * @param actorUserId authenticated owning Mentor
     * @param projectId planned Project to activate
     */
    @Transactional
    public void activate(long actorUserId, long projectId) {
        var route = projectRoute(projectId);
        if (route.mentorUserId() != actorUserId) {
            throw new ProjectAccessDeniedException();
        }
        var snapshotMemberIds = projects.findCurrentInternUserIdsByProjectId(projectId).stream()
                .collect(Collectors.toUnmodifiableSet());
        var lockedAccounts = lockAccounts(concat(actorUserId, snapshotMemberIds));
        requireActiveMentor(snapshotFor(lockedAccounts, actorUserId));
        var project = lockedProject(projectId);
        project.authorizeOwner(actorUserId);
        if (!snapshotMemberIds.equals(currentInternUserIds(project))) {
            throw new ProjectRuleViolationException("Project membership changed; retry activation");
        }
        var activeMemberships = project.memberships().stream()
                .filter(membership -> membership.isCurrent()
                        && eligibleInternForProjectMutation(
                                snapshotFor(lockedAccounts, membership.internUserId())))
                .toList();
        var activeMembershipIds = activeMemberships.stream()
                .map(membership -> membership.id())
                .collect(Collectors.toUnmodifiableSet());
        Set<Long> activeInternUserIds = activeMemberships.stream()
                .map(membership -> membership.internUserId())
                .collect(Collectors.toUnmodifiableSet());
        var allTaskAssigneesAreCurrent = taskQueries.countCurrentTasksAssignedOutside(
                projectId, activeMembershipIds) == 0;

        project.activate(actorUserId, activeInternUserIds, allTaskAssigneesAreCurrent, clock.instant());
        projects.flush();
    }

    private ProjectEntity lockedProject(long projectId) {
        return projects.findLockedById(projectId).orElseThrow(ProjectAccessDeniedException::new);
    }

    private ProjectMutationRoute projectRoute(long projectId) {
        return projects.findMutationRouteById(projectId)
                .orElseThrow(ProjectAccessDeniedException::new);
    }

    private ProjectInvitationRoute invitationRoute(long invitationId) {
        return invitations.findRouteById(invitationId)
                .orElseThrow(ProjectAccessDeniedException::new);
    }

    private ProjectExitRequestRoute exitRequestRoute(long requestId) {
        return exitRequests.findRouteById(requestId)
                .orElseThrow(ProjectAccessDeniedException::new);
    }

    private ProjectMembershipEntity requireCurrentLeader(
            ProjectEntity project, long actorUserId, LockedAccountMutationEligibility actor) {
        ProjectMembershipEntity leader;
        try {
            leader = project.currentLeader();
        } catch (ProjectRuleViolationException exception) {
            throw new ProjectAccessDeniedException();
        }
        if (leader.internUserId() != actorUserId) {
            throw new ProjectAccessDeniedException();
        }
        requireEligibleInternForProjectActor(actor);
        return leader;
    }

    private ProjectMembershipEntity currentMembershipForUser(
            ProjectEntity project, long actorUserId, LockedAccountMutationEligibility actor) {
        ProjectMembershipEntity membership;
        try {
            membership = project.currentMember(actorUserId);
        } catch (ProjectRuleViolationException exception) {
            throw new ProjectAccessDeniedException();
        }
        requireEligibleInternForProjectActor(actor);
        return membership;
    }

    private void ensureNoPendingExit(long targetMembershipId) {
        if (exitRequests.findLockedPendingByTargetMembershipId(targetMembershipId).isPresent()) {
            throw new ProjectRuleViolationException("A pending exit request already targets this member");
        }
    }

    private void supersedePendingInvitation(
            long projectId, long invitedInternUserId, long mentorUserId, java.time.Instant at) {
        invitations.findLockedPending(projectId, invitedInternUserId).ifPresent(invitation -> invitation.resolve(
                InvitationStatus.SUPERSEDED,
                InvitationResolutionCode.MENTOR_DIRECT_ADD,
                mentorUserId,
                null,
                at));
    }

    private void requireOpenProject(ProjectEntity project) {
        if (project.status() == com.lab.labtimesheet.feature.project.model.ProjectStatus.COMPLETED) {
            throw new ProjectRuleViolationException("Completed Projects are read-only");
        }
    }

    private LockedAccountMutationEligibility lockAccount(long userId) {
        return snapshotFor(lockAccounts(List.of(userId)), userId);
    }

    private List<LockedAccountMutationEligibility> lockAccounts(Collection<Long> userIds) {
        try {
            return accounts.lockedAccountMutationEligibility(userIds);
        } catch (IllegalArgumentException exception) {
            throw new ProjectAccessDeniedException();
        }
    }

    private List<LockedAccountMutationEligibility> lockAccountsForTargetMutation(
            Collection<Long> userIds) {
        try {
            return accounts.lockedAccountMutationEligibility(userIds);
        } catch (IllegalArgumentException exception) {
            throw new ProjectRuleViolationException("Intern is not eligible for Project membership");
        }
    }

    private static void requireActiveAccount(LockedAccountMutationEligibility account) {
        if (account.accountStatus() != AccountStatus.ACTIVE) {
            throw new ProjectAccessDeniedException();
        }
    }

    private static void requireActiveMentor(LockedAccountMutationEligibility account) {
        if (account.role() != GlobalRole.MENTOR || account.accountStatus() != AccountStatus.ACTIVE) {
            throw new ProjectAccessDeniedException();
        }
    }

    private static void requireActiveInternForProjectActor(LockedAccountMutationEligibility account) {
        if (account.role() != GlobalRole.INTERN || account.accountStatus() != AccountStatus.ACTIVE) {
            throw new ProjectAccessDeniedException();
        }
    }

    private void requireEligibleInternForProjectActor(LockedAccountMutationEligibility account) {
        if (!eligibleInternForProjectMutation(account)) {
            throw new ProjectAccessDeniedException();
        }
    }

    private void requireEligibleInternForProjectTarget(LockedAccountMutationEligibility account) {
        if (!eligibleInternForProjectMutation(account)) {
            throw new ProjectRuleViolationException("Intern is not eligible for Project membership");
        }
    }

    private ProjectInternEligibility projectInternEligibility(
            LockedAccountMutationEligibility account) {
        return new ProjectInternEligibility(account.userId(), eligibleInternForProjectMutation(account));
    }

    private boolean eligibleInternForProjectMutation(LockedAccountMutationEligibility account) {
        return account.eligibleForProjectMutation()
                && accounts.isEligibleIntern(account.userId(), LocalDate.now(clock));
    }

    private static LockedAccountMutationEligibility snapshotFor(
            List<LockedAccountMutationEligibility> snapshots, long userId) {
        return snapshots.stream()
                .filter(snapshot -> snapshot.userId() == userId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Locked Account snapshot is missing"));
    }

    private static List<Long> concat(long actorUserId, Collection<Long> otherUserIds) {
        var ids = new java.util.ArrayList<Long>(otherUserIds.size() + 1);
        ids.add(actorUserId);
        ids.addAll(otherUserIds);
        return ids;
    }

    private static Set<Long> currentInternUserIds(ProjectEntity project) {
        return project.memberships().stream()
                .filter(ProjectMembershipEntity::isCurrent)
                .map(ProjectMembershipEntity::internUserId)
                .collect(Collectors.toUnmodifiableSet());
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ProjectRuleViolationException("A nonblank exit reason is required");
        }
        return reason.trim();
    }

}
