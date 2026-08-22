package com.lab.labtimesheet.feature.project.service;

import com.lab.labtimesheet.feature.account.model.AccountStatus;
import com.lab.labtimesheet.feature.account.model.GlobalRole;
import com.lab.labtimesheet.feature.account.model.dto.InternshipLifecycleGuard;
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
import com.lab.labtimesheet.feature.project.model.ProjectStatus;
import com.lab.labtimesheet.feature.project.model.dto.ProjectCreateCommand;
import com.lab.labtimesheet.feature.project.model.dto.ProjectInvitationNotificationRoute;
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
import com.lab.labtimesheet.feature.task.service.TaskTransferResult;
import com.lab.labtimesheet.feature.task.service.TaskTransferService;
import com.lab.labtimesheet.feature.notification.model.NotificationType;
import com.lab.labtimesheet.feature.notification.model.dto.NotificationAction;
import com.lab.labtimesheet.feature.notification.model.dto.NotificationEvent;
import com.lab.labtimesheet.feature.notification.model.dto.NotificationRecipient;
import com.lab.labtimesheet.feature.notification.service.NotificationService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
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
    private final TaskTransferService taskTransfers;
    private final NotificationService notifications;
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
        long projectId = projects.saveAndFlush(project).id();
        notifyMembershipChanged(projectId, "INITIAL_MEMBER_ADDED", List.of(initialLeader.userId()));
        notifyLeadershipChanged(projectId, "INITIAL_LEADER_ASSIGNED", List.of(initialLeader.userId()));
        return projectId;
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
     * Account and Intern-profile rows for the actor, selection, and any superseded invitation
     * recipients are locked in Account-owned ascending order before the Project lock. Every
     * identifier is revalidated after owner authorization and before the aggregate changes, so
     * missing, duplicate, stale, ineligible, or current-member selections leave membership
     * unchanged.
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
        var pendingInvitationRoutes =
                invitations.findPendingNotificationRoutesByProjectIdAndInvitedInternUserIds(
                        projectId, internUserIds);
        var accountIds = concat(actorUserId, internUserIds);
        if (route.currentLeaderUserId() != null) {
            accountIds.add(route.currentLeaderUserId());
        }
        appendInvitationNotificationUsers(accountIds, pendingInvitationRoutes);
        var lockedAccounts = lockAccountsForTargetMutation(accountIds);
        requireStablePendingInvitationRecipients(
                projectId,
                internUserIds,
                invitationNotificationUserIds(pendingInvitationRoutes));
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
        var addedMemberships = selectedInterns.stream()
                .map(intern -> project.addMember(actorUserId, intern, addedAt))
                .toList();
        addedMemberships.forEach(membership ->
                supersedePendingInvitation(projectId, membership.internUserId(), actorUserId, addedAt));
        projects.flush();
        addedMemberships.forEach(membership ->
                notifyMembershipChanged(projectId, "MEMBER_ADDED", List.of(membership.internUserId())));
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
        long invitationId = invitations.saveAndFlush(invitation).id();
        notifyInvitationCreated(invitation);
        return invitationId;
    }

    /**
     * Revokes a pending invitation owned by the issuing Leader or owning Mentor while the
     * Project remains open. Completed Projects are read-only even when a stale pending row is
     * encountered during a concurrent lifecycle boundary.
     * The authenticated actor, invitation target, issuing Leader, and owning Mentor Account/profile
     * rows are locked in ascending order before the Project and invitation rows; terminal state is
     * inspected only after the actor's Project relationship is authorized.
     *
     * @param actorUserId authenticated current issuing Leader or owning Mentor
     * @param invitationId invitation identifier
     * @throws ProjectAccessDeniedException when the actor is outside the invitation context
     * @throws ProjectRuleViolationException when the invitation is already terminal
     */
    @Transactional
    public void revokeInvitation(long actorUserId, long invitationId) {
        var route = invitationRoute(invitationId);
        var notificationRoute = invitationNotificationRoute(invitationId);
        var lockedAccounts = lockAccounts(List.of(
                actorUserId,
                notificationRoute.invitedInternUserId(),
                notificationRoute.issuingLeaderUserId(),
                notificationRoute.mentorUserId()));
        var actor = snapshotFor(lockedAccounts, actorUserId);
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
        requireOpenProject(project);
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
        notifyInvitationResolution(invitation, project.mentorUserId(), invitation.resolutionCode());
    }

    /**
     * Applies an authenticated response from only the invited Intern.
     *
     * <p>Account and Intern-profile rows for the authenticated actor, invited Intern, issuing
     * Leader, and owning Mentor are locked in ascending order before the Project lock. Acceptance
     * rechecks the current issuing term, eligibility, and membership while the Project lock is
     * held. Email links therefore cannot act as bearer join tokens.
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
        var notificationRoute = invitationNotificationRoute(invitationId);
        var lockedAccounts = lockAccounts(List.of(
                actorUserId,
                notificationRoute.invitedInternUserId(),
                notificationRoute.issuingLeaderUserId(),
                notificationRoute.mentorUserId()));
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
            notifyInvitationResolution(invitation, project.mentorUserId(), invitation.resolutionCode());
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
            notifyInvitationResolution(invitation, project.mentorUserId(), invitation.resolutionCode());
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
            notifyInvitationResolution(invitation, project.mentorUserId(), invitation.resolutionCode());
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
            notifyInvitationResponse(invitation, project.mentorUserId(), invitation.resolutionCode());
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
        notifyInvitationResponse(invitation, project.mentorUserId(), invitation.resolutionCode());
        notifyMembershipChanged(project.id(), "INVITATION_ACCEPTED", List.of(actorUserId));
    }

    /**
     * Creates a Leader-requested removal of another current Project member.
     * All current-member Account and Intern-profile rows remain locked in ascending Account order
     * through Project authorization and request persistence. This includes the target recipient
     * used by the same-transaction exit notification; the target membership interval is not
     * closed by this operation.
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
        var route = projectRoute(projectId);
        var snapshotMemberIds = projects.findCurrentInternUserIdsByProjectId(projectId);
        var accountIds = concat(actorUserId, snapshotMemberIds);
        accountIds.add(route.mentorUserId());
        var lockedAccounts = lockAccounts(accountIds);
        var actor = snapshotFor(lockedAccounts, actorUserId);
        var project = lockedProject(projectId);
        var leader = requireCurrentLeader(project, actorUserId, actor);
        requireOpenProject(project);
        var target = membershipInProject(project, targetMembershipId);
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
        long requestId = exitRequests.saveAndFlush(request).id();
        notifyExitRequested(request, project);
        return requestId;
    }

    /**
     * Creates an authenticated member's own leave request without closing membership.
     * All current-member Account and Intern-profile rows are locked before the Project so the
     * current Leader and Mentor notification recipients are already retained. Completed or
     * otherwise inactive Interns are denied before any request state is inspected or changed.
     *
     * @param actorUserId authenticated current member
     * @param projectId Project identifier
     * @param reason nonblank retained reason
     * @return generated exit-request identifier
     */
    @Transactional
    public long requestOwnLeave(long actorUserId, long projectId, String reason) {
        var route = projectRoute(projectId);
        var snapshotMemberIds = projects.findCurrentInternUserIdsByProjectId(projectId);
        var accountIds = concat(actorUserId, snapshotMemberIds);
        accountIds.add(route.mentorUserId());
        var lockedAccounts = lockAccounts(accountIds);
        var actor = snapshotFor(lockedAccounts, actorUserId);
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
        long requestId = exitRequests.saveAndFlush(request).id();
        notifyExitRequested(request, project);
        return requestId;
    }

    /**
     * Cancels a pending request only by its original requester.
     * All current-member Account and Intern-profile rows are locked before the Project and
     * request; requester authorization precedes the terminal-state check and the exit-notification
     * recipient rows cannot introduce a later Account lock.
     *
     * @param actorUserId authenticated requester
     * @param requestId exit-request identifier
     */
    @Transactional
    public void cancelExit(long actorUserId, long requestId) {
        var route = exitRequestRoute(requestId);
        var snapshotMemberIds = projects.findCurrentInternUserIdsByProjectId(route.projectId());
        var lockedAccounts = lockAccounts(concat(actorUserId, snapshotMemberIds));
        var actor = snapshotFor(lockedAccounts, actorUserId);
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
        notifyExitResolved(request, project, project.currentLeader().internUserId());
    }

    /**
     * Replaces the current Leader with an eligible current member in one transaction. The
     * current-Leader route snapshot is rechecked after locking the Project, so concurrent Mentor
     * changes based on the same prior term have one valid winner and a retryable conflict.
     * The closed term is flushed before its replacement so PostgreSQL's immediate exclusion rule
     * observes exactly one current term. The Mentor, all current-member recipients, replacement,
     * and every pending-invitation notification recipient are locked in ascending Account order
     * before the Project; Task assignments are not changed.
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
        var pendingInvitationRoutes = invitations.findPendingNotificationRoutesByProjectId(projectId);
        var accountIds = concat(actorUserId, List.of(internUserId));
        accountIds.addAll(projects.findCurrentInternUserIdsByProjectId(projectId));
        appendInvitationNotificationUsers(accountIds, pendingInvitationRoutes);
        var lockedAccounts = lockAccountsForTargetMutation(accountIds);
        requireStablePendingInvitationRecipients(
                projectId, null, invitationNotificationUserIds(pendingInvitationRoutes));
        requireActiveMentor(snapshotFor(lockedAccounts, actorUserId));
        var project = lockedProject(projectId);
        project.authorizeOwner(actorUserId);
        if (!Objects.equals(route.currentLeaderUserId(), project.currentLeader().internUserId())) {
            throw new ProjectRuleViolationException("Project leadership changed; retry the mutation");
        }
        requireEligibleInternForProjectTarget(snapshotFor(lockedAccounts, internUserId));
        var replacementMembership = currentMemberTarget(project, internUserId);
        if (exitRequests.findLockedPendingByTargetMembershipId(replacementMembership.id()).isPresent()) {
            throw new ProjectRuleViolationException("Leader replacement cannot have a pending exit");
        }
        long outgoingLeaderUserId = project.currentLeadershipTerm().internUserId();
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
        notifyLeadershipChanged(
                projectId,
                "LEADER_CHANGED",
                List.of(outgoingLeaderUserId, change.replacement().internUserId()));
        invitations.findLockedPendingByTerm(projectId, previousTermId).forEach(invitation -> {
            invitation.resolve(
                    InvitationStatus.REVOKED,
                    InvitationResolutionCode.LEADER_CHANGED,
                    null,
                    null,
                    clock.instant());
            notifyInvitationResolution(invitation, actorUserId, invitation.resolutionCode());
        });
        invitations.flush();
    }

    /**
     * Commits one Leader-selected unfinished Task transfer batch for a pending exit.
     *
     * <p>This compatibility overload resolves the pending request by its source membership. New
     * HTTP routes must use the request-bound overload so a stale or guessed request identifier
     * cannot transfer a different pending exit.</p>
     *
     * @param actorUserId authenticated current Leader
     * @param projectId owning open Project
     * @param sourceMembershipId pending-exit source membership
     * @param taskIds selected unfinished Task identifiers
     * @param recipientMembershipId eligible current recipient not pending exit
     * @return atomic Task transfer result
     */
    @Transactional
    public TaskTransferResult transferTasks(
            long actorUserId,
            long projectId,
            long sourceMembershipId,
            Set<Long> taskIds,
            long recipientMembershipId) {
        return transferTasksInternal(
                actorUserId,
                projectId,
                null,
                sourceMembershipId,
                taskIds,
                recipientMembershipId);
    }

    /**
     * Commits one Leader-selected unfinished Task transfer batch only for the supplied pending
     * exit request.
     *
     * <p>The request route is checked before the Project lock, then its row is locked and its
     * pending status, Project, and target membership are rechecked after the Project lock. Account,
     * Project, exit-request, and Task locks therefore cover the full mutation, while a stale or
     * mismatched request fails before any Task or retained history changes.</p>
     *
     * @param actorUserId authenticated current Leader
     * @param projectId owning open Project
     * @param requestId pending exit request from the route
     * @param sourceMembershipId pending-exit source membership
     * @param taskIds selected unfinished Task identifiers
     * @param recipientMembershipId eligible current recipient not pending exit
     * @return atomic Task transfer result
     * @throws ProjectAccessDeniedException when the request is outside the Project or source
     *         membership is not its target
     * @throws ProjectRuleViolationException when the request is terminal or the Project changed
     */
    @Transactional
    public TaskTransferResult transferTasks(
            long actorUserId,
            long projectId,
            long requestId,
            long sourceMembershipId,
            Set<Long> taskIds,
            long recipientMembershipId) {
        var route = exitRequestRoute(requestId);
        if (route.projectId() != projectId) {
            throw new ProjectAccessDeniedException();
        }
        return transferTasksInternal(
                actorUserId,
                projectId,
                requestId,
                sourceMembershipId,
                taskIds,
                recipientMembershipId);
    }

    private TaskTransferResult transferTasksInternal(
            long actorUserId,
            long projectId,
            Long requestId,
            long sourceMembershipId,
            Set<Long> taskIds,
            long recipientMembershipId) {
        var route = projectRoute(projectId);
        if (!Objects.equals(route.currentLeaderUserId(), actorUserId)) {
            throw new ProjectAccessDeniedException();
        }
        var snapshotMemberIds = projects.findCurrentInternUserIdsByProjectId(projectId).stream()
                .collect(Collectors.toUnmodifiableSet());
        var lockedAccounts = lockAccounts(concat(actorUserId, snapshotMemberIds));
        var project = lockedProject(projectId);
        if (!snapshotMemberIds.equals(currentInternUserIds(project))) {
            throw new ProjectRuleViolationException("Project membership changed; retry Task transfer");
        }
        requireOpenProject(project);
        var leader = requireCurrentLeader(project, actorUserId, snapshotFor(lockedAccounts, actorUserId));
        var pendingExitRequests = exitRequests.findLockedPendingByProjectId(projectId);
        var pendingExitMembershipIds = pendingExitRequests.stream()
                .map(ProjectExitRequestEntity::targetMembershipId)
                .collect(Collectors.toUnmodifiableSet());
        if (requestId == null) {
            if (!pendingExitMembershipIds.contains(sourceMembershipId)) {
                throw new ProjectAccessDeniedException();
            }
        } else {
            var request = lockedExitRequest(requestId, project.id());
            if (!request.isPending()) {
                throw new ProjectRuleViolationException("Exit request is no longer pending");
            }
            if (request.targetMembershipId() != sourceMembershipId
                    || !pendingExitMembershipIds.contains(sourceMembershipId)) {
                throw new ProjectAccessDeniedException();
            }
        }
        var context = queries.taskContext(actorUserId, project, pendingExitMembershipIds);
        return taskTransfers.transferBatch(
                context,
                leader.id(),
                sourceMembershipId,
                taskIds,
                recipientMembershipId);
    }

    /**
     * Approves a pending exit only when the target is no longer Leader and owns no unfinished
     * non-deleted Tasks. Membership closure and request resolution commit in the same transaction.
     *
     * @param actorMentorUserId authenticated owning Mentor
     * @param requestId pending exit request identifier
     * @param note optional retained Mentor decision note
     */
    @Transactional
    public void approveExit(long actorMentorUserId, long requestId, String note) {
        var route = exitRequestRoute(requestId);
        var locked = lockOwnedProject(actorMentorUserId, route.projectId(),
                List.of(route.requesterUserId()));
        var project = locked.project();
        requireOpenProject(project);
        var request = lockedExitRequest(requestId, project.id());
        if (!request.isPending()) {
            throw new ProjectRuleViolationException("Exit request is no longer pending");
        }
        var target = membershipInProject(project, request.targetMembershipId());
        if (!target.isCurrent()) {
            throw new ProjectRuleViolationException("Exit target is no longer current");
        }
        if (project.currentLeader().id().equals(target.id())) {
            throw new ProjectRuleViolationException("Replace the current Leader before approval");
        }
        if (taskTransfers.unfinishedCount(project.id(), target.id()) > 0) {
            throw new ProjectRuleViolationException("Transfer all unfinished Tasks before approval");
        }
        var at = clock.instant();
        target.close(at, actorMentorUserId);
        request.resolve(ProjectExitRequestStatus.APPROVED, normalizeDecisionNote(note), actorMentorUserId, at);
        projects.flush();
        exitRequests.flush();
        notifyMembershipChanged(
                project.id(),
                "MEMBER_REMOVED",
                List.of(target.internUserId()),
                locked.notificationRecipients());
        notifyExitResolved(
                request,
                project,
                project.currentLeader().internUserId(),
                locked.notificationRecipients());
    }

    /**
     * Rejects a pending exit without closing membership or undoing completed transfer batches.
     *
     * @param actorMentorUserId authenticated owning Mentor
     * @param requestId pending exit request identifier
     * @param note optional retained Mentor decision note
     */
    @Transactional
    public void rejectExit(long actorMentorUserId, long requestId, String note) {
        var route = exitRequestRoute(requestId);
        var locked = lockOwnedProject(actorMentorUserId, route.projectId(),
                List.of(route.requesterUserId()));
        var project = locked.project();
        requireOpenProject(project);
        var request = lockedExitRequest(requestId, project.id());
        if (!request.isPending()) {
            throw new ProjectRuleViolationException("Exit request is no longer pending");
        }
        var target = membershipInProject(project, request.targetMembershipId());
        if (!target.isCurrent()) {
            throw new ProjectRuleViolationException("Exit target is no longer current");
        }
        request.resolve(ProjectExitRequestStatus.REJECTED, normalizeDecisionNote(note), actorMentorUserId, clock.instant());
        exitRequests.flush();
        notifyExitResolved(
                request,
                project,
                project.currentLeader().internUserId(),
                locked.notificationRecipients());
    }

    /**
     * Directly removes a current member as an atomic Mentor shortcut. All unfinished Tasks move
     * to the current Leader; removing that Leader first appoints the supplied eligible replacement
     * and then moves Tasks to the replacement. Completed Tasks and retained attribution remain.
     *
     * @param actorMentorUserId authenticated owning Mentor
     * @param projectId open Project
     * @param targetMembershipId current membership to close
     * @param replacementLeaderUserId required only when removing the current Leader
     */
    @Transactional
    public void directRemoveMember(
            long actorMentorUserId,
            long projectId,
            long targetMembershipId,
            Long replacementLeaderUserId) {
        var locked = lockOwnedProject(actorMentorUserId, projectId);
        var project = locked.project();
        requireOpenProject(project);
        var target = membershipInProject(project, targetMembershipId);
        if (!target.isCurrent()) {
            throw new ProjectRuleViolationException("Removal target must be current");
        }
        var leader = project.currentLeader();
        if (Objects.equals(locked.initialLeaderUserId(), target.internUserId())
                && !leader.id().equals(target.id())) {
            throw new ProjectRuleViolationException("Project leadership changed; retry the removal");
        }
        if (leader.id().equals(target.id())) {
            if (replacementLeaderUserId == null || replacementLeaderUserId <= 0) {
                throw new ProjectRuleViolationException("Removing the current Leader requires a replacement");
            }
            var replacement = currentMemberTarget(project, replacementLeaderUserId);
            if (exitRequests.findLockedPendingByTargetMembershipId(replacement.id()).isPresent()) {
                throw new ProjectRuleViolationException("Leader replacement cannot have a pending exit");
            }
            long outgoingLeaderUserId = leader.internUserId();
            requireEligibleInternForProjectTarget(
                    snapshotFor(locked.accounts(), replacementLeaderUserId));
            long previousTermId = project.currentLeadershipTerm().id();
            var change = project.prepareLeaderChange(
                    actorMentorUserId,
                    projectInternEligibility(snapshotFor(locked.accounts(), replacementLeaderUserId)),
                    clock.instant());
            projects.flush();
            project.completeLeaderChange(actorMentorUserId, change);
            projects.flush();
            notifyLeadershipChanged(
                    projectId,
                    "LEADER_CHANGED",
                    List.of(outgoingLeaderUserId, change.replacement().internUserId()),
                    locked.notificationRecipients());
            invitations.findLockedPendingByTerm(projectId, previousTermId).forEach(invitation -> {
                invitation.resolve(
                        InvitationStatus.REVOKED,
                        InvitationResolutionCode.LEADER_CHANGED,
                        null,
                        null,
                        clock.instant());
                notifyInvitationResolution(
                        invitation,
                        actorMentorUserId,
                        invitation.resolutionCode(),
                        locked.notificationRecipients());
            });
            invitations.flush();
            leader = project.currentLeader();
        }
        if (taskTransfers.unfinishedCount(project.id(), target.id()) > 0) {
            var pendingExitMembershipIds = exitRequests.findLockedPendingByProjectId(projectId).stream()
                    .map(ProjectExitRequestEntity::targetMembershipId)
                    .collect(Collectors.toUnmodifiableSet());
            var context = queries.taskContext(actorMentorUserId, project, pendingExitMembershipIds);
            taskTransfers.transferAllUnfinished(context, leader.id(), target.id(), leader.id());
        }
        var at = clock.instant();
        target.close(at, actorMentorUserId);
        notifyMembershipChanged(
                project.id(),
                "MEMBER_REMOVED",
                List.of(target.internUserId()),
                locked.notificationRecipients());
        exitRequests.findLockedPendingByTargetMembershipId(target.id()).ifPresent(request -> {
            request.resolve(
                    ProjectExitRequestStatus.APPROVED,
                    "Direct Mentor removal",
                    actorMentorUserId,
                    at);
            notifyExitResolved(
                    request,
                    project,
                    project.currentLeader().internUserId(),
                    locked.notificationRecipients());
        });
        projects.flush();
        exitRequests.flush();
    }

    /**
     * Completes an active Project only when every non-deleted Task is DONE. Pending invitations
     * are revoked and pending exits superseded before current intervals close; all retained rows
     * remain available to authorized history readers.
     *
     * @param actorMentorUserId authenticated owning Mentor
     * @param projectId Project to complete
     */
    @Transactional
    public void complete(long actorMentorUserId, long projectId) {
        var locked = lockOwnedProject(actorMentorUserId, projectId);
        var project = locked.project();
        if (project.status() != ProjectStatus.ACTIVE) {
            throw new ProjectRuleViolationException("Only an active Project can be completed");
        }
        var progress = taskQueries.projectProgress(projectId);
        if (progress.done() != progress.totalTasks()) {
            throw new ProjectRuleViolationException("Every non-deleted Task must be DONE before completion");
        }
        var at = clock.instant();
        invitations.findLockedPendingByProjectId(projectId).forEach(invitation -> {
            invitation.resolve(
                    InvitationStatus.REVOKED,
                    InvitationResolutionCode.PROJECT_COMPLETED,
                    null,
                    null,
                    at);
            notifyInvitationResolution(
                    invitation,
                    actorMentorUserId,
                    invitation.resolutionCode(),
                    locked.notificationRecipients());
        });
        invitations.flush();
        long currentLeaderUserId = project.currentLeader().internUserId();
        var currentMemberUserIds = project.memberships().stream()
                .filter(ProjectMembershipEntity::isCurrent)
                .map(ProjectMembershipEntity::internUserId)
                .toList();
        exitRequests.findLockedPendingByProjectId(projectId).forEach(request -> {
            request.resolve(
                    ProjectExitRequestStatus.SUPERSEDED,
                    "Project completed",
                    null,
                    at);
            notifyExitResolved(
                    request,
                    project,
                    currentLeaderUserId,
                    locked.notificationRecipients());
        });
        exitRequests.flush();
        project.complete(actorMentorUserId, at);
        projects.flush();
        notifyMembershipChanged(
                project.id(),
                "PROJECT_COMPLETED",
                currentMemberUserIds,
                locked.notificationRecipients());
        notifyLeadershipChanged(
                project.id(),
                "LEADER_REMOVED",
                List.of(currentLeaderUserId),
                locked.notificationRecipients());
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
     * Completes an Intern after recomputing Project leadership and Task ownership under the shared lock order.
     *
     * <p>The active Admin and target Account/profile rows are locked first. Every current Project is then locked in
     * ascending identifier order before unfinished Tasks are counted. Because all Project and Task mutations use the
     * same Account-before-Project order, the guard remains stable until the Account-owned completion commits.</p>
     *
     * @param adminUserId active Admin performing the terminal action
     * @param internUserId Intern account being completed
     * @throws IllegalArgumentException when the actor or target account shape is invalid
     * @throws IllegalStateException when Project/Task readiness or the internship state rejects completion
     */
    @Transactional
    public void completeInternship(long adminUserId, long internUserId) {
        accounts.completeInternship(
                internUserId,
                adminUserId,
                lockedInternshipLifecycleGuard(adminUserId, internUserId));
    }

    /**
     * Withdraws an Intern after recomputing Project leadership and Task ownership under the shared lock order.
     *
     * <p>The readiness transaction is identical to completion. Account withdrawal then deactivates the Intern and
     * expires existing sessions before the surrounding transaction commits.</p>
     *
     * @param adminUserId active Admin performing the terminal action
     * @param internUserId Intern account being withdrawn
     * @throws IllegalArgumentException when the actor or target account shape is invalid
     * @throws IllegalStateException when Project/Task readiness or the internship state rejects withdrawal
     */
    @Transactional
    public void withdrawInternship(long adminUserId, long internUserId) {
        accounts.withdrawInternship(
                internUserId,
                adminUserId,
                lockedInternshipLifecycleGuard(adminUserId, internUserId));
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

    private ProjectInvitationNotificationRoute invitationNotificationRoute(long invitationId) {
        return invitations.findNotificationRouteById(invitationId)
                .orElseThrow(ProjectAccessDeniedException::new);
    }

    private ProjectExitRequestRoute exitRequestRoute(long requestId) {
        return exitRequests.findRouteById(requestId)
                .orElseThrow(ProjectAccessDeniedException::new);
    }

    private LockedOwnedProject lockOwnedProject(long actorMentorUserId, long projectId) {
        return lockOwnedProject(actorMentorUserId, projectId, List.of());
    }

    /**
     * Establishes the Account/profile-before-Project lock order for Mentor-owned mutations.
     *
     * <p>Additional scalar account IDs cover retained recipients that are not current members,
     * especially a historical exit requester resolved after leadership replacement. Pending exit
     * requester IDs are also read as scalars so completion and direct removal cannot introduce a
     * post-Project Account lock through their retained decision notifications. Immutable
     * notification recipient facts are resolved after the Account/profile locks and before the
     * Project lock, then reused by every notification emitted by the locked mutation.</p>
     *
     * @param actorMentorUserId authenticated owning Mentor
     * @param projectId Project identifier
     * @param additionalAccountIds immutable route recipients required by this mutation
     * @return locked Project, initial Leader snapshot, Account eligibility snapshots, and
     *         pre-resolved recipient facts
     */
    private LockedOwnedProject lockOwnedProject(
            long actorMentorUserId, long projectId, Collection<Long> additionalAccountIds) {
        var route = projectRoute(projectId);
        if (route.mentorUserId() != actorMentorUserId) {
            throw new ProjectAccessDeniedException();
        }
        var pendingInvitationRoutes = invitations.findPendingNotificationRoutesByProjectId(projectId);
        var snapshotMemberIds = projects.findCurrentInternUserIdsByProjectId(projectId).stream()
                .collect(Collectors.toUnmodifiableSet());
        var accountIds = concat(actorMentorUserId, snapshotMemberIds);
        accountIds.addAll(additionalAccountIds);
        accountIds.addAll(exitRequests.findPendingRequesterUserIdsByProjectId(projectId));
        appendInvitationNotificationUsers(accountIds, pendingInvitationRoutes);
        var lockedAccounts = lockAccounts(accountIds);
        var recipientFacts = notificationRecipients(accountIds).stream()
                .collect(Collectors.toUnmodifiableMap(NotificationRecipient::userId, recipient -> recipient));
        requireStablePendingInvitationRecipients(
                projectId, null, invitationNotificationUserIds(pendingInvitationRoutes));
        requireActiveMentor(snapshotFor(lockedAccounts, actorMentorUserId));
        var project = lockedProject(projectId);
        project.authorizeOwner(actorMentorUserId);
        if (!snapshotMemberIds.equals(currentInternUserIds(project))) {
            throw new ProjectRuleViolationException("Project membership changed; retry mutation");
        }
        return new LockedOwnedProject(
                project, route.currentLeaderUserId(), lockedAccounts, recipientFacts);
    }

    private ProjectExitRequestEntity lockedExitRequest(long requestId, long projectId) {
        var request = exitRequests.findLockedById(requestId)
                .orElseThrow(ProjectAccessDeniedException::new);
        if (request.projectId() != projectId) {
            throw new ProjectAccessDeniedException();
        }
        return request;
    }

    private ProjectMembershipEntity membershipInProject(ProjectEntity project, long membershipId) {
        try {
            return project.membership(membershipId);
        } catch (ProjectRuleViolationException exception) {
            throw new ProjectAccessDeniedException();
        }
    }

    private ProjectMembershipEntity currentMemberTarget(ProjectEntity project, long internUserId) {
        try {
            return project.currentMember(internUserId);
        } catch (ProjectRuleViolationException exception) {
            throw new ProjectAccessDeniedException();
        }
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
        invitations.findLockedPending(projectId, invitedInternUserId).ifPresent(invitation -> {
            invitation.resolve(
                    InvitationStatus.SUPERSEDED,
                    InvitationResolutionCode.MENTOR_DIRECT_ADD,
                    mentorUserId,
                    null,
                    at);
            notifyInvitationResolution(invitation, mentorUserId, invitation.resolutionCode());
        });
    }

    /**
     * Publishes the affected Interns for a retained Project membership interval change.
     *
     * <p>The caller remains inside the Project transaction, so the notification rows commit or
     * roll back with the membership mutation. An empty recipient collection is valid for no-op
     * closure paths, although current Project transitions always provide at least one account.</p>
     *
     * @param projectId owning Project identifier
     * @param transition retained membership transition
     * @param affectedUserIds Intern accounts whose membership interval changed
     */
    private void notifyMembershipChanged(
            long projectId, String transition, Collection<Long> affectedUserIds) {
        publishMembershipChanged(projectId, transition, notificationRecipients(affectedUserIds));
    }

    private void notifyMembershipChanged(
            long projectId,
            String transition,
            Collection<Long> affectedUserIds,
            Map<Long, NotificationRecipient> recipientFacts) {
        publishMembershipChanged(
                projectId, transition, notificationRecipients(affectedUserIds, recipientFacts));
    }

    private void publishMembershipChanged(
            long projectId, String transition, List<NotificationRecipient> recipients) {
        notifications.publish(
                new NotificationEvent(
                        NotificationType.MEMBERSHIP_CHANGED,
                        transition,
                        "Project membership updated",
                        "A Project membership changed for Project " + projectId + "."),
                new NotificationAction(projectActionUrl(projectId), false),
                recipients);
    }

    /**
     * Publishes the outgoing and incoming Leaders for a retained leadership-term mutation.
     *
     * <p>Initial appointment supplies only the incoming Leader; final Project closure supplies
     * only the outgoing Leader. The Platform service collapses any duplicate account IDs.</p>
     *
     * @param projectId owning Project identifier
     * @param transition retained leadership transition
     * @param affectedUserIds outgoing/incoming Leader accounts as applicable
     */
    private void notifyLeadershipChanged(
            long projectId, String transition, Collection<Long> affectedUserIds) {
        publishLeadershipChanged(projectId, transition, notificationRecipients(affectedUserIds));
    }

    private void notifyLeadershipChanged(
            long projectId,
            String transition,
            Collection<Long> affectedUserIds,
            Map<Long, NotificationRecipient> recipientFacts) {
        publishLeadershipChanged(
                projectId, transition, notificationRecipients(affectedUserIds, recipientFacts));
    }

    private void publishLeadershipChanged(
            long projectId, String transition, List<NotificationRecipient> recipients) {
        notifications.publish(
                new NotificationEvent(
                        NotificationType.LEADERSHIP_CHANGED,
                        transition,
                        "Project leadership updated",
                        "Project leadership changed for Project " + projectId + "."),
                new NotificationAction(projectActionUrl(projectId), false),
                recipients);
    }

    /**
     * Persists the invitation-created notification in the same Project transaction as the pending row.
     *
     * @param invitation newly persisted invitation
     */
    private void notifyInvitationCreated(ProjectInvitationEntity invitation) {
        notifications.publish(
                new NotificationEvent(
                        NotificationType.PROJECT_INVITATION_CREATED,
                        "CREATED",
                        "Project invitation",
                        "You have a new invitation for Project " + invitation.projectId() + "."),
                new NotificationAction(invitationActionUrl(invitation.projectId(), invitation.id()), false),
                notificationRecipients(List.of(invitation.invitedInternUserId())));
    }

    /**
     * Publishes an invitation response to the retained issuing Leader and owning Mentor.
     *
     * @param invitation resolved invitation
     * @param mentorUserId owning Mentor
     * @param resolutionCode retained response code
     */
    private void notifyInvitationResponse(
            ProjectInvitationEntity invitation,
            long mentorUserId,
            InvitationResolutionCode resolutionCode) {
        notifications.publish(
                invitationEvent(resolutionCode),
                new NotificationAction(invitationActionUrl(invitation.projectId(), invitation.id()), false),
                notificationRecipients(List.of(
                        invitation.issuingLeadershipTerm().internUserId(),
                        mentorUserId)));
    }

    /**
     * Publishes invitation revocation or supersession to invitee and retained Leader/Mentor.
     *
     * @param invitation resolved invitation
     * @param mentorUserId owning Mentor
     * @param resolutionCode retained terminal reason
     */
    private void notifyInvitationResolution(
            ProjectInvitationEntity invitation,
            long mentorUserId,
            InvitationResolutionCode resolutionCode) {
        publishInvitationResolution(
                invitation,
                resolutionCode,
                notificationRecipients(List.of(
                        invitation.invitedInternUserId(),
                        invitation.issuingLeadershipTerm().internUserId(),
                        mentorUserId)));
    }

    private void notifyInvitationResolution(
            ProjectInvitationEntity invitation,
            long mentorUserId,
            InvitationResolutionCode resolutionCode,
            Map<Long, NotificationRecipient> recipientFacts) {
        publishInvitationResolution(
                invitation,
                resolutionCode,
                notificationRecipients(
                        List.of(
                                invitation.invitedInternUserId(),
                                invitation.issuingLeadershipTerm().internUserId(),
                                mentorUserId),
                        recipientFacts));
    }

    private void publishInvitationResolution(
            ProjectInvitationEntity invitation,
            InvitationResolutionCode resolutionCode,
            List<NotificationRecipient> recipients) {
        notifications.publish(
                invitationEvent(resolutionCode),
                new NotificationAction(invitationActionUrl(invitation.projectId(), invitation.id()), false),
                recipients);
    }

    private NotificationEvent invitationEvent(InvitationResolutionCode resolutionCode) {
        return new NotificationEvent(
                NotificationType.PROJECT_INVITATION_RESOLVED,
                resolutionCode.name(),
                "Project invitation updated",
                "A Project invitation is now " + resolutionCode + ".");
    }

    /**
     * Persists a request notification with the request-type-specific NOT-010 recipients.
     *
     * @param request newly persisted pending exit request
     * @param project locked owning Project
     */
    private void notifyExitRequested(ProjectExitRequestEntity request, ProjectEntity project) {
        List<Long> recipients = request.requestType() == ProjectExitRequestType.LEADER_REMOVAL
                ? List.of(project.mentorUserId(), project.membership(request.targetMembershipId()).internUserId())
                : List.of(project.mentorUserId(), project.currentLeader().internUserId());
        notifications.publish(
                new NotificationEvent(
                        NotificationType.MEMBERSHIP_EXIT_REQUESTED,
                        request.requestType().name(),
                        "Membership exit request",
                        "A membership exit request was created for Project " + project.id() + "."),
                new NotificationAction(exitActionUrl(project.id(), request.id()), false),
                notificationRecipients(recipients));
    }

    /**
     * Persists a decision or cancellation event for requester, target, and current Leader, collapsing
     * repeated roles to one recipient account.
     *
     * @param request resolved exit request
     * @param project locked owning Project
     * @param currentLeaderUserId current Leader before terminal Project closure
     */
    private void notifyExitResolved(
            ProjectExitRequestEntity request,
            ProjectEntity project,
            long currentLeaderUserId) {
        publishExitResolved(
                request,
                project,
                notificationRecipients(List.of(
                        project.membership(request.requesterMembershipId()).internUserId(),
                        project.membership(request.targetMembershipId()).internUserId(),
                        currentLeaderUserId)));
    }

    private void notifyExitResolved(
            ProjectExitRequestEntity request,
            ProjectEntity project,
            long currentLeaderUserId,
            Map<Long, NotificationRecipient> recipientFacts) {
        publishExitResolved(
                request,
                project,
                notificationRecipients(
                        List.of(
                                project.membership(request.requesterMembershipId()).internUserId(),
                                project.membership(request.targetMembershipId()).internUserId(),
                                currentLeaderUserId),
                        recipientFacts));
    }

    private void publishExitResolved(
            ProjectExitRequestEntity request,
            ProjectEntity project,
            List<NotificationRecipient> recipients) {
        notifications.publish(
                new NotificationEvent(
                        NotificationType.MEMBERSHIP_EXIT_RESOLVED,
                        request.status().name(),
                        "Membership exit request updated",
                        "The membership exit request for Project " + project.id()
                                + " is now " + request.status() + "."),
                new NotificationAction(exitActionUrl(project.id(), request.id()), false),
                recipients);
    }

    private List<NotificationRecipient> notificationRecipients(Collection<Long> userIds) {
        return userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .map(accounts::requireIdentityById)
                .map(identity -> new NotificationRecipient(identity.id(), identity.email()))
                .toList();
    }

    private static List<NotificationRecipient> notificationRecipients(
            Collection<Long> userIds,
            Map<Long, NotificationRecipient> recipientFacts) {
        return userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .map(userId -> Objects.requireNonNull(
                        recipientFacts.get(userId),
                        "Notification recipient was not resolved before Project locking"))
                .toList();
    }

    private static String invitationActionUrl(long projectId, long invitationId) {
        return "/projects/" + projectId + "/invitations/" + invitationId;
    }

    private static String projectActionUrl(long projectId) {
        return "/projects/" + projectId;
    }

    private static String exitActionUrl(long projectId, long requestId) {
        return "/projects/" + projectId + "/exits/" + requestId;
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

    private InternshipLifecycleGuard lockedInternshipLifecycleGuard(long adminUserId, long internUserId) {
        List<LockedAccountMutationEligibility> lockedAccounts;
        try {
            lockedAccounts = accounts.lockedAccountMutationEligibility(List.of(adminUserId, internUserId));
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("Account action could not be completed", failure);
        }
        LockedAccountMutationEligibility admin = snapshotFor(lockedAccounts, adminUserId);
        if (admin.role() != GlobalRole.ADMIN || admin.accountStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("An active Admin is required");
        }
        LockedAccountMutationEligibility intern = snapshotFor(lockedAccounts, internUserId);
        if (intern.role() != GlobalRole.INTERN) {
            throw new IllegalArgumentException("An Intern account is required");
        }

        boolean currentLeader = false;
        long unfinishedTaskCount = 0;
        var currentMemberships = projects.findMembershipIntervalsByInternUserId(internUserId).stream()
                .filter(interval -> interval.leftAt() == null)
                .toList();
        for (var interval : currentMemberships) {
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

    private static void appendInvitationNotificationUsers(
            List<Long> accountIds, Collection<ProjectInvitationNotificationRoute> routes) {
        routes.forEach(route -> {
            accountIds.add(route.invitedInternUserId());
            accountIds.add(route.issuingLeaderUserId());
            accountIds.add(route.mentorUserId());
        });
    }

    private void requireStablePendingInvitationRecipients(
            long projectId,
            Collection<Long> selectedInternUserIds,
            Set<Long> expectedRecipientUserIds) {
        var currentRoutes = selectedInternUserIds == null
                ? invitations.findPendingNotificationRoutesByProjectId(projectId)
                : invitations.findPendingNotificationRoutesByProjectIdAndInvitedInternUserIds(
                        projectId, selectedInternUserIds);
        if (!invitationNotificationUserIds(currentRoutes).equals(expectedRecipientUserIds)) {
            throw new ProjectRuleViolationException("Invitation recipients changed; retry Project mutation");
        }
    }

    private static Set<Long> invitationNotificationUserIds(
            Collection<ProjectInvitationNotificationRoute> routes) {
        var userIds = new HashSet<Long>();
        routes.forEach(route -> {
            userIds.add(route.invitedInternUserId());
            userIds.add(route.issuingLeaderUserId());
            userIds.add(route.mentorUserId());
        });
        return Set.copyOf(userIds);
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

    private static String normalizeDecisionNote(String note) {
        return note == null || note.isBlank() ? null : note.trim();
    }

    private record LockedOwnedProject(
            ProjectEntity project,
            Long initialLeaderUserId,
            List<LockedAccountMutationEligibility> accounts,
            Map<Long, NotificationRecipient> notificationRecipients) {
    }

}
