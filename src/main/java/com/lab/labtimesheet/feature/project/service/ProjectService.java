package com.lab.labtimesheet.feature.project.service;

import com.lab.labtimesheet.feature.identity.model.AccountStatus;
import com.lab.labtimesheet.feature.identity.model.dto.InternshipLifecycleGuard;
import com.lab.labtimesheet.feature.identity.model.dto.LockedAccountMutationEligibility;
import com.lab.labtimesheet.feature.identity.service.AccountService;
import com.lab.labtimesheet.feature.notification.model.NotificationType;
import com.lab.labtimesheet.feature.notification.model.dto.NotificationAction;
import com.lab.labtimesheet.feature.notification.model.dto.NotificationEvent;
import com.lab.labtimesheet.feature.notification.model.dto.NotificationRecipient;
import com.lab.labtimesheet.feature.notification.service.NotificationService;
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
import com.lab.labtimesheet.feature.project.model.dto.ProjectExitRequestRoute;
import com.lab.labtimesheet.feature.project.model.dto.ProjectInvitationNotificationRoute;
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
import com.lab.labtimesheet.feature.project.model.dto.RemainingEffortForecastInput;
import com.lab.labtimesheet.feature.project.service.TaskQueryService;
import com.lab.labtimesheet.feature.project.service.TaskTransferResult;
import com.lab.labtimesheet.feature.project.service.TaskTransferService;
import com.lab.labtimesheet.platform.model.GlobalRole;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
 * <p>
 * Mutation methods first obtain any Account and Intern-profile lifecycle locks
 * through the
 * Account service's immutable DTO boundary, in ascending account/profile order,
 * and only then
 * lock the Project aggregate. Account and Task facts arrive through public
 * feature services;
 * Project never imports their repositories or entities.
 */
// Service GHI Project. Tìm cụm: Ctrl+F "===" (CREATE | ADD MEMBER | INVITATION
// | EXIT | TRANSFER | CHANGE LEADER | COMPLETE | ACTIVATE | DELETE).
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
     * Native aggregate cleanup is kept behind the Project service transaction
     * boundary.
     */
    // EntityManager dùng cho native DELETE khi xóa project draft
    // (deleteProjectRows).
    @PersistenceContext
    private EntityManager entityManager;

    /**
     * Atomically creates a planned Mentor-owned Project, eligible initial
     * membership, and first
     * leadership term. {@code saveAndFlush} exposes database invariant violations
     * before commit.
     *
     * <p>
     * When the MVC Controller invokes this Spring bean, the transaction interceptor
     * runs before
     * the first line and commits only after this method returns normally. A runtime
     * rule/access
     * exception or a persistence constraint failure marks the transaction for
     * rollback, so a
     * partially-created Project, membership, or leadership term is not left behind.
     * </p>
     *
     * @param actorUserId authenticated active Mentor creating and owning the
     *                    Project
     * @param command     validated creation values
     * @return generated Project identifier
     * @throws ProjectAccessDeniedException                                                 when
     *                                                                                      the
     *                                                                                      actor
     *                                                                                      is
     *                                                                                      not
     *                                                                                      an
     *                                                                                      active
     *                                                                                      Mentor
     * @throws com.lab.labtimesheet.feature.project.exception.ProjectRuleViolationException when
     *                                                                                      dates,
     *                                                                                      name,
     *                                                                                      or
     *                                                                                      initial-Leader
     *                                                                                      eligibility
     *                                                                                      violate
     *                                                                                      the
     *                                                                                      aggregate
     *                                                                                      rules
     */
    @Transactional
    // === CREATE PROJECT | Service ===
    // Chức năng: lock account → validate Mentor + Leader → dựng entity → INSERT
    // cascade → gửi notification.
    public long create(long actorUserId, ProjectCreateCommand command) {
        // PRJ-024: start date được phép ở quá khứ; ngày ghi work log vẫn do TSK-014 giới hạn.
        var lockedAccounts = lockAccountsForTargetMutation( // → AccountService: FOR UPDATE + snapshot
                List.of(actorUserId, command.initialLeaderUserId())); // lock Mentor + Leader được chọn
        requireActiveMentor(snapshotFor(lockedAccounts, actorUserId)); // Mentor phải ACTIVE
        var initialLeader = snapshotFor(lockedAccounts, command.initialLeaderUserId()); // lấy snapshot Leader
        requireEligibleInternForProjectTarget(initialLeader); // Intern đủ điều kiện thực tập
        var project = ProjectEntity.plan( // → Entity: dựng Project PLANNED + membership + leadership trong memory
                actorUserId,
                command.name(),
                command.description(),
                command.startDate(),
                command.endDate(),
                projectInternEligibility(initialLeader), // bọc userId + eligible flag
                clock.instant());
        long projectId = projects.saveAndFlush(project).id(); // → Repo: JPA cascade INSERT 3 bảng
        notifyMembershipChanged(projectId, "INITIAL_MEMBER_ADDED", List.of(initialLeader.userId())); // →
                                                                                                     // NotificationService
        notifyLeadershipChanged(projectId, "INITIAL_LEADER_ASSIGNED", List.of(initialLeader.userId())); // →
                                                                                                        // NotificationService
        return projectId;
    }

    /**
     * Adds one eligible Intern as a current member while holding the Project write
     * lock.
     * The same Intern may belong to other Projects, but duplicate current
     * membership in this
     * Project is rejected before flush.
     *
     * @param actorUserId  authenticated owning Mentor
     * @param projectId    Project to update
     * @param internUserId Intern selected for direct addition
     */
    // Mentor thêm một Intern vào Project (gọi luồng thêm nhiều người bên dưới).
    @Transactional
    public void addMember(long actorUserId, long projectId, long internUserId) {
        addMembers(actorUserId, projectId, List.of(internUserId));
    }

    /**
     * Adds a complete selection of eligible nonmembers while holding one Project
     * write lock.
     * Account and Intern-profile rows for the actor, selection, and any superseded
     * invitation
     * recipients are locked in Account-owned ascending order before the Project
     * lock. Every
     * identifier is revalidated after owner authorization and before the aggregate
     * changes, so
     * missing, duplicate, stale, ineligible, or current-member selections leave
     * membership
     * unchanged.
     *
     * @param actorUserId   authenticated owning Mentor
     * @param projectId     Project to update
     * @param internUserIds distinct eligible Intern account identifiers
     * @throws com.lab.labtimesheet.feature.project.exception.ProjectRuleViolationException when
     *                                                                                      the
     *                                                                                      selection
     *                                                                                      is
     *                                                                                      null,
     *                                                                                      empty,
     *                                                                                      malformed,
     *                                                                                      duplicate,
     *                                                                                      stale,
     *                                                                                      ineligible,
     *                                                                                      or
     *                                                                                      already
     *                                                                                      contains
     *                                                                                      a
     *                                                                                      current
     *                                                                                      member
     */
    // === ADD MEMBER | Service ===
    // Chức năng: Mentor thêm Intern đã chọn — lock account → lock project → INSERT
    // membership.
    @Transactional
    public void addMembers(long actorUserId, long projectId, List<Long> internUserIds) {
        // Validate form: phải chọn ít nhất 1 Intern, ID hợp lệ và không trùng.
        if (internUserIds == null || internUserIds.isEmpty()) {
            throw new ProjectRuleViolationException("Select at least one Intern");
        }
        if (internUserIds.stream().anyMatch(userId -> userId == null || userId <= 0)
                || new HashSet<>(internUserIds).size() != internUserIds.size()) {
            throw new ProjectRuleViolationException("Intern selection is invalid");
        }

        var route = projectRoute(projectId); // đọc mentorId + leaderId nhẹ, chưa lock project — → Repo
        if (route.mentorUserId() != actorUserId) { // chỉ Mentor sở hữu project mới được add
            throw new ProjectAccessDeniedException();
        }
        var pendingInvitationRoutes = invitations.findPendingNotificationRoutesByProjectIdAndInvitedInternUserIds( // →
                                                                                                                   // Repo
                projectId, internUserIds); // lời mời PENDING trùng Intern chọn (sẽ bị supersede sau)
        var accountIds = concat(actorUserId, internUserIds); // gộp Mentor + Intern để lock theo thứ tự ID tăng dần
        if (route.currentLeaderUserId() != null) {
            accountIds.add(route.currentLeaderUserId()); // Leader hiện tại cũng cần lock (nhận notification)
        }
        appendInvitationNotificationUsers(accountIds, pendingInvitationRoutes); // thêm intern/leader/mentor từ
                                                                                // invitation
        var lockedAccounts = lockAccountsForTargetMutation(accountIds); // → AccountService: FOR UPDATE + snapshot
        requireStablePendingInvitationRecipients(
                projectId,
                internUserIds,
                invitationNotificationUserIds(pendingInvitationRoutes)); // so recipient trước/sau lock — đổi thì retry
        requireActiveMentor(snapshotFor(lockedAccounts, actorUserId)); // Mentor phải còn ACTIVE
        var project = lockedProject(projectId); // → Repo: FOR UPDATE — giữ quyền sửa project
        project.authorizeOwner(actorUserId); // xác nhận lại owner trên aggregate sau lock
        var selectedInterns = internUserIds.stream()
                .map(userId -> snapshotFor(lockedAccounts, userId)) // lấy snapshot từng Intern đã lock
                .peek(this::requireEligibleInternForProjectTarget) // throw nếu không đủ điều kiện thực tập
                .map(this::projectInternEligibility) // bọc userId + eligible flag cho Entity.addMember
                .toList();
        if (selectedInterns.stream().anyMatch(intern -> project.hasCurrentMember(intern.userId()))) {
            throw new ProjectRuleViolationException("One or more selected Interns are no longer eligible");
        }

        var addedAt = clock.instant();
        var addedMemberships = selectedInterns.stream()
                .map(intern -> project.addMember(actorUserId, intern, addedAt)) // → Entity: tạo membership trong memory
                .toList();
        addedMemberships.forEach(
                membership -> supersedePendingInvitation(projectId, membership.internUserId(), actorUserId, addedAt)); // hủy
                                                                                                                       // lời
                                                                                                                       // mời
                                                                                                                       // PENDING
                                                                                                                       // trùng
        projects.flush(); // → Repo: INSERT project_memberships
        addedMemberships.forEach(
                membership -> notifyMembershipChanged(projectId, "MEMBER_ADDED", List.of(membership.internUserId()))); // →
                                                                                                                       // NotificationService
    }

    /**
     * Issues a non-expiring invitation from the authenticated current Leader's
     * stored term.
     *
     * @param actorUserId         authenticated current Leader
     * @param projectId           mutable Project receiving the invitation
     * @param invitedInternUserId eligible Intern to invite
     * @return generated invitation identifier
     * @throws ProjectAccessDeniedException  when the actor is not the current
     *                                       Leader
     * @throws ProjectRuleViolationException when the Project, target, or
     *                                       pending-pair rule fails
     */
    // Leader mời một Intern (gọi luồng mời nhiều người bên dưới).
    @Transactional
    public long issueInvitation(long actorUserId, long projectId, long invitedInternUserId) {
        return issueInvitations(actorUserId, projectId, List.of(invitedInternUserId)).getFirst();
    }

    /**
     * Issues one atomic batch of invitations from the authenticated current
     * Leader's term.
     * Every selected Intern is locked and revalidated before any invitation is
     * persisted, so a
     * stale or ineligible selection rolls back the whole batch.
     *
     * @param actorUserId          authenticated current Leader
     * @param projectId            mutable Project receiving invitations
     * @param invitedInternUserIds eligible Intern accounts to invite
     * @return generated invitation identifiers in submitted order
     */
    // === INVITATION | Service — Leader mời Intern ===
    // Chức năng: tạo row PENDING trong project_invitations + gửi notification.
    @Transactional
    public List<Long> issueInvitations(
            long actorUserId, long projectId, Collection<Long> invitedInternUserIds) {
        // Validate form: chọn ít nhất 1 Intern, ID hợp lệ và không trùng.
        if (invitedInternUserIds == null || invitedInternUserIds.isEmpty()
                || invitedInternUserIds.stream().anyMatch(Objects::isNull)
                || invitedInternUserIds.stream().anyMatch(id -> id <= 0)
                || invitedInternUserIds.stream().distinct().count() != invitedInternUserIds.size()) {
            throw new ProjectRuleViolationException("Choose one or more unique Interns.");
        }
        var route = projectRoute(projectId); // đọc mentorId + leaderId nhẹ — → Repo
        if (!Objects.equals(route.currentLeaderUserId(), actorUserId)) {
            throw new ProjectAccessDeniedException(); // chỉ Leader hiện tại được mời
        }
        List<Long> selectedIds = List.copyOf(invitedInternUserIds);
        List<Long> accountIds = new java.util.ArrayList<>();
        accountIds.add(actorUserId);
        accountIds.addAll(selectedIds); // gộp Leader + Intern được mời để lock
        var lockedAccounts = lockAccountsForTargetMutation(accountIds); // → AccountService: FOR UPDATE + snapshot
        var actor = snapshotFor(lockedAccounts, actorUserId);
        requireEligibleInternForProjectActor(actor); // Leader phải ACTIVE + eligible
        var project = lockedProject(projectId); // → Repo: FOR UPDATE
        if (project.currentLeader().internUserId() != actorUserId) { // xác nhận lại Leader sau lock
            throw new ProjectAccessDeniedException();
        }
        requireOpenProject(project); // project phải PLANNED hoặc ACTIVE
        List<Long> invitationIds = new java.util.ArrayList<>();
        for (long invitedInternUserId : selectedIds) {
            var invitee = snapshotFor(lockedAccounts, invitedInternUserId);
            requireEligibleInternForProjectTarget(invitee); // Intern đủ điều kiện thực tập
            if (project.hasCurrentMember(invitedInternUserId)) { // chặn mời member đã có
                throw new ProjectRuleViolationException("Intern is already a current Project member");
            }
            if (invitations.findLockedPending(projectId, invitedInternUserId).isPresent()) { // chặn trùng lời mời
                                                                                             // PENDING
                throw new ProjectRuleViolationException("A pending invitation already exists");
            }
            var invitation = ProjectInvitationEntity.pending( // → Entity: tạo row PENDING trong memory
                    project, invitee.userId(), project.currentLeadershipTerm(), clock.instant());
            invitationIds.add(invitations.saveAndFlush(invitation).id()); // → Repo: INSERT project_invitations
            notifyInvitationCreated(invitation); // → NotificationService: gửi thông báo lời mời
        }
        return List.copyOf(invitationIds);
    }

    /**
     * Revokes a pending invitation using the invitation's own Project route.
     *
     * <p>
     * This compatibility entry point remains for non-HTTP callers that do not carry
     * a route
     * Project. HTTP handlers must use the route-bound overload below.
     * </p>
     *
     * @param actorUserId  authenticated current issuing Leader or owning Mentor
     * @param invitationId invitation identifier
     */
    // Thu hồi lời mời (phiên bản tương thích: tự lấy Project từ mã lời mời).
    @Transactional
    public void revokeInvitation(long actorUserId, long invitationId) {
        var route = invitationRoute(invitationId);
        revokeInvitation(actorUserId, route.projectId(), invitationId);
    }

    /**
     * Revokes a pending invitation only when its nested identifier belongs to the
     * route Project.
     * Completed Projects are read-only even when a stale pending row is encountered
     * during a
     * concurrent lifecycle boundary. The authenticated actor, invitation target,
     * issuing Leader,
     * and owning Mentor Account/profile rows are locked in ascending order before
     * the Project and
     * invitation rows; terminal state is inspected only after route and actor
     * authorization.
     *
     * @param actorUserId  authenticated current issuing Leader or owning Mentor
     * @param projectId    Project encoded by the HTTP route
     * @param invitationId invitation identifier encoded by the nested route
     * @throws ProjectAccessDeniedException  when the actor or nested invitation is
     *                                       outside the
     *                                       route Project
     * @throws ProjectRuleViolationException when the invitation is already terminal
     */
    // === INVITATION | Service — thu hồi lời mời ===
    @Transactional
    public void revokeInvitation(long actorUserId, long projectId, long invitationId) {
        var route = invitationRoute(invitationId); // lấy projectId + invitedInternUserId — → Repo
        requireRouteProject(projectId, route.projectId()); // URL nested phải khớp project thật
        var notificationRoute = invitationNotificationRoute(invitationId); // intern + leader gửi + mentor
        var lockedAccounts = lockAccounts(List.of( // lock tất cả người liên quan trước project
                actorUserId,
                notificationRoute.invitedInternUserId(),
                notificationRoute.issuingLeaderUserId(),
                notificationRoute.mentorUserId()));
        var actor = snapshotFor(lockedAccounts, actorUserId);
        requireActiveAccount(actor); // actor phải ACTIVE
        var project = lockedProject(route.projectId()); // → Repo: FOR UPDATE
        var invitation = invitations.findLockedById(invitationId) // → Repo: lock row invitation
                .orElseThrow(ProjectAccessDeniedException::new);
        boolean owner = actor.role() == GlobalRole.MENTOR && project.mentorUserId() == actorUserId;
        boolean issuingLeader = actor.role() == GlobalRole.INTERN
                && invitation.issuingLeadershipTerm().isCurrent()
                && invitation.issuingLeadershipTerm().internUserId() == actorUserId;
        if (!owner && !issuingLeader) { // chỉ Mentor owner hoặc Leader đã gửi lời mời
            throw new ProjectAccessDeniedException();
        }
        if (issuingLeader) {
            requireEligibleInternForProjectActor(actor); // Leader phải eligible
        }
        requireOpenProject(project); // project chưa COMPLETED
        if (!invitation.isPending()) { // chỉ thu hồi lời mời đang PENDING
            throw new ProjectRuleViolationException("Invitation is no longer pending");
        }
        invitation.resolve( // → Entity: PENDING → REVOKED
                InvitationStatus.REVOKED,
                owner ? InvitationResolutionCode.MENTOR_REVOKED : InvitationResolutionCode.INVITER_REVOKED,
                actorUserId,
                null,
                clock.instant());
        invitations.flush(); // → Repo: UPDATE project_invitations
        notifyInvitationResolution(invitation, project.mentorUserId(), invitation.resolutionCode()); // →
                                                                                                     // NotificationService
    }

    /**
     * Applies an authenticated response from only the invited Intern.
     *
     * <p>
     * Account and Intern-profile rows for the authenticated actor, invited Intern,
     * issuing
     * Leader, and owning Mentor are locked in ascending order before the Project
     * lock. Acceptance
     * rechecks the current issuing term, eligibility, and membership while the
     * Project lock is
     * held. Email links therefore cannot act as bearer join tokens.
     *
     * @param actorUserId  authenticated response actor
     * @param invitationId invitation identifier
     * @param response     accept or decline choice
     */
    // Intern được mời chấp nhận hoặc từ chối lời mời. Accept → thêm membership;
    // Decline/Revoke → đổi trạng thái invitation.
    // === INVITATION | Service — Intern accept/decline ===
    @Transactional
    public void respondToInvitation(long actorUserId, long invitationId, InvitationResponse response) {
        if (response == null) {
            throw new ProjectRuleViolationException("Invitation response is required");
        }
        var route = invitationRoute(invitationId); // → Repo: lấy projectId + invitedInternUserId
        if (route.invitedInternUserId() != actorUserId) {
            throw new ProjectAccessDeniedException(); // chỉ Intern được mời mới trả lời
        }
        var notificationRoute = invitationNotificationRoute(invitationId); // intern + leader + mentor
        var lockedAccounts = lockAccounts(List.of( // lock tất cả người liên quan trước project
                actorUserId,
                notificationRoute.invitedInternUserId(),
                notificationRoute.issuingLeaderUserId(),
                notificationRoute.mentorUserId()));
        var actor = snapshotFor(lockedAccounts, actorUserId);
        var invitee = snapshotFor(lockedAccounts, route.invitedInternUserId());
        var project = lockedProject(route.projectId()); // → Repo: FOR UPDATE
        var invitation = invitations.findLockedById(invitationId) // → Repo: lock row invitation
                .orElseThrow(ProjectAccessDeniedException::new);
        if (actor.role() != GlobalRole.INTERN || invitation.invitedInternUserId() != actorUserId) {
            throw new ProjectAccessDeniedException();
        }
        requireActiveAccount(actor);
        if (!eligibleInternForProjectMutation(invitee)) { // Intern không còn eligible → auto revoke
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
        if (!invitation.issuingLeadershipTerm().isCurrent()) { // Leader đã đổi → auto revoke
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
        if (project.hasCurrentMember(actorUserId)) { // đã là member → auto revoke
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
        if (response == InvitationResponse.DECLINE) { // Intern từ chối
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
        // Accept: thêm membership rồi đánh dấu invitation ACCEPTED
        var membership = project.acceptMembership(actorUserId, clock.instant()); // → Entity: tạo membership
        projects.flush(); // → Repo: INSERT project_memberships
        invitation.resolve(
                InvitationStatus.ACCEPTED,
                InvitationResolutionCode.INVITEE_ACCEPTED,
                actorUserId,
                membership,
                clock.instant());
        invitations.flush(); // → Repo: UPDATE project_invitations
        notifyInvitationResponse(invitation, project.mentorUserId(), invitation.resolutionCode());
        notifyMembershipChanged(project.id(), "INVITATION_ACCEPTED", List.of(actorUserId)); // → NotificationService
    }

    /**
     * Creates a Leader-requested removal of another current Project member.
     * All current-member Account and Intern-profile rows remain locked in ascending
     * Account order
     * through Project authorization and request persistence. This includes the
     * target recipient
     * used by the same-transaction exit notification; the target membership
     * interval is not
     * closed by this operation.
     *
     * @param actorUserId        authenticated current Leader
     * @param projectId          Project identifier
     * @param targetMembershipId current member requested for removal
     * @param reason             nonblank retained reason
     * @return generated exit-request identifier
     */
    // === EXIT REQUEST | Service — Leader đề xuất loại member ===
    @Transactional
    public long requestMemberRemoval(
            long actorUserId, long projectId, long targetMembershipId, String reason) {
        var route = projectRoute(projectId); // đọc mentorId nhẹ — → Repo
        var snapshotMemberIds = projects.findCurrentInternUserIdsByProjectId(projectId); // snapshot member trước lock
        var accountIds = concat(actorUserId, snapshotMemberIds); // Leader + tất cả member hiện tại
        accountIds.add(route.mentorUserId()); // Mentor cũng cần lock (nhận notification)
        var lockedAccounts = lockAccounts(accountIds); // → AccountService: FOR UPDATE
        var actor = snapshotFor(lockedAccounts, actorUserId);
        var project = lockedProject(projectId); // → Repo: FOR UPDATE
        var leader = requireCurrentLeader(project, actorUserId, actor); // actor phải là Leader hiện tại
        requireOpenProject(project); // project chưa COMPLETED
        var target = membershipInProject(project, targetMembershipId); // tìm membership target trong aggregate
        if (!target.isCurrent() || target.id().equals(leader.id())) { // không được loại Leader hoặc member đã rời
            throw new ProjectRuleViolationException("Removal target must be another current member");
        }
        var normalizedReason = requireReason(reason); // lý do không được rỗng
        ensureNoPendingExit(targetMembershipId); // chặn trùng yêu cầu rời đang PENDING
        var request = ProjectExitRequestEntity.pending( // → Entity: tạo exit request LEADER_REMOVAL
                project,
                target,
                leader,
                ProjectExitRequestType.LEADER_REMOVAL,
                normalizedReason,
                clock.instant());
        long requestId = exitRequests.saveAndFlush(request).id(); // → Repo: INSERT exit_requests
        notifyExitRequested(request, project); // → NotificationService: thông báo Mentor
        return requestId;
    }

    /**
     * Creates an authenticated member's own leave request without closing
     * membership.
     * All current-member Account and Intern-profile rows are locked before the
     * Project so the
     * current Leader and Mentor notification recipients are already retained.
     * Completed or
     * otherwise inactive Interns are denied before any request state is inspected
     * or changed.
     *
     * @param actorUserId authenticated current member
     * @param projectId   Project identifier
     * @param reason      nonblank retained reason
     * @return generated exit-request identifier
     */
    // === EXIT REQUEST | Service — Intern xin rời ===
    @Transactional
    public long requestOwnLeave(long actorUserId, long projectId, String reason) {
        var route = projectRoute(projectId); // đọc mentorId nhẹ — → Repo
        var snapshotMemberIds = projects.findCurrentInternUserIdsByProjectId(projectId); // snapshot member trước lock
        var accountIds = concat(actorUserId, snapshotMemberIds); // actor + tất cả member hiện tại
        accountIds.add(route.mentorUserId()); // Mentor cũng cần lock (nhận notification)
        var lockedAccounts = lockAccounts(accountIds); // → AccountService: FOR UPDATE
        var actor = snapshotFor(lockedAccounts, actorUserId);
        var project = lockedProject(projectId); // → Repo: FOR UPDATE
        var requester = currentMembershipForUser(project, actorUserId, actor); // actor phải là member hiện tại
        requireOpenProject(project); // project chưa COMPLETED
        var normalizedReason = requireReason(reason); // lý do không được rỗng
        ensureNoPendingExit(requester.id()); // chặn trùng yêu cầu rời đang PENDING
        var request = ProjectExitRequestEntity.pending( // → Entity: tạo exit request MEMBER_LEAVE
                project,
                requester,
                requester,
                ProjectExitRequestType.MEMBER_LEAVE,
                normalizedReason,
                clock.instant());
        long requestId = exitRequests.saveAndFlush(request).id(); // → Repo: INSERT exit_requests
        notifyExitRequested(request, project); // → NotificationService: thông báo Mentor
        return requestId;
    }

    /**
     * Cancels a pending request using the request's own Project route.
     *
     * <p>
     * This compatibility entry point remains for non-HTTP callers that do not carry
     * a route
     * Project. HTTP handlers must use the route-bound overload below.
     * </p>
     *
     * @param actorUserId authenticated requester
     * @param requestId   exit-request identifier
     */
    // Tự lấy project từ mã yêu cầu rồi gọi hàm hủy chính bên dưới.
    @Transactional
    public void cancelExit(long actorUserId, long requestId) {
        var route = exitRequestRoute(requestId);
        cancelExit(actorUserId, route.projectId(), requestId);
    }

    /**
     * Cancels a pending request only by its original requester and only when the
     * nested request
     * belongs to the route Project. All current-member Account and Intern-profile
     * rows are locked
     * before the Project and request; requester authorization precedes the
     * terminal-state check
     * and exit-notification recipient rows cannot introduce a later Account lock.
     *
     * @param actorUserId authenticated requester
     * @param projectId   Project encoded by the HTTP route
     * @param requestId   exit-request identifier encoded by the nested route
     * @throws ProjectAccessDeniedException when the requester or nested request is
     *                                      outside the
     *                                      route Project
     */
    // === EXIT REQUEST | Service — hủy yêu cầu rời ===
    @Transactional
    public void cancelExit(long actorUserId, long projectId, long requestId) {
        var route = exitRequestRoute(requestId); // lấy projectId từ requestId — → Repo
        requireRouteProject(projectId, route.projectId()); // URL nested phải khớp project thật
        var snapshotMemberIds = projects.findCurrentInternUserIdsByProjectId(route.projectId());
        var lockedAccounts = lockAccounts(concat(actorUserId, snapshotMemberIds)); // lock requester + members
        var actor = snapshotFor(lockedAccounts, actorUserId);
        var project = lockedProject(route.projectId()); // → Repo: FOR UPDATE
        var request = exitRequests.findLockedById(requestId) // → Repo: lock row exit request
                .orElseThrow(ProjectAccessDeniedException::new);
        ProjectMembershipEntity requester;
        try {
            requester = project.membership(request.requesterMembershipId()); // tìm membership người tạo request
        } catch (ProjectRuleViolationException exception) {
            throw new ProjectAccessDeniedException();
        }
        if (request.requesterMembershipId() <= 0
                || !requester.isCurrent()
                || requester.internUserId() != actorUserId) { // chỉ người tạo request mới được hủy
            throw new ProjectAccessDeniedException();
        }
        requireEligibleInternForProjectActor(actor); // requester phải eligible
        requireOpenProject(project); // project chưa COMPLETED
        if (!request.isPending()) { // chỉ hủy request đang PENDING
            throw new ProjectRuleViolationException("Exit request is no longer pending");
        }
        request.resolve(ProjectExitRequestStatus.CANCELLED, null, actorUserId, clock.instant()); // → Entity
        exitRequests.flush(); // → Repo: UPDATE exit_requests
        notifyExitResolved(request, project, project.currentLeader().internUserId()); // → NotificationService
    }

    /**
     * Replaces the current Leader with an eligible current member in one
     * transaction. The
     * current-Leader route snapshot is rechecked after locking the Project, so
     * concurrent Mentor
     * changes based on the same prior term have one valid winner and a retryable
     * conflict.
     * The closed term is flushed before its replacement so PostgreSQL's immediate
     * exclusion rule
     * observes exactly one current term. The Mentor, all current-member recipients,
     * replacement,
     * and every pending-invitation notification recipient are locked in ascending
     * Account order
     * before the Project; Task assignments are not changed.
     *
     * @param actorUserId  authenticated owning Mentor
     * @param projectId    Project whose Leader changes
     * @param internUserId active same-Project replacement Intern
     */
    // === CHANGE LEADER | Service ===
    // Chức năng: kết thúc kỳ Leader cũ, mở kỳ mới, hủy lời mời của Leader cũ.
    @Transactional
    public void changeLeader(long actorUserId, long projectId, long internUserId) {
        var route = projectRoute(projectId); // đọc mentorId + leaderId nhẹ — → Repo
        if (route.mentorUserId() != actorUserId) { // chỉ Mentor sở hữu project
            throw new ProjectAccessDeniedException();
        }
        var pendingInvitationRoutes = invitations.findPendingNotificationRoutesByProjectId(projectId); // tất cả lời mời
                                                                                                       // PENDING
        var accountIds = concat(actorUserId, List.of(internUserId)); // Mentor + Leader mới
        accountIds.addAll(projects.findCurrentInternUserIdsByProjectId(projectId)); // + tất cả member hiện tại
        appendInvitationNotificationUsers(accountIds, pendingInvitationRoutes); // + người liên quan invitation
        var lockedAccounts = lockAccountsForTargetMutation(accountIds); // → AccountService: FOR UPDATE + snapshot
        requireStablePendingInvitationRecipients(
                projectId, null, invitationNotificationUserIds(pendingInvitationRoutes)); // so recipient trước/sau lock
        requireActiveMentor(snapshotFor(lockedAccounts, actorUserId)); // Mentor phải ACTIVE
        var project = lockedProject(projectId); // → Repo: FOR UPDATE
        project.authorizeOwner(actorUserId); // xác nhận owner sau lock
        if (!Objects.equals(route.currentLeaderUserId(), project.currentLeader().internUserId())) { // Leader đổi giữa
                                                                                                    // chừng → retry
            throw new ProjectRuleViolationException("Project leadership changed; retry the mutation");
        }
        requireEligibleInternForProjectTarget(snapshotFor(lockedAccounts, internUserId)); // Leader mới phải eligible
        var replacementMembership = currentMemberTarget(project, internUserId); // phải là member hiện tại
        if (exitRequests.findLockedPendingByTargetMembershipId(replacementMembership.id()).isPresent()) {
            throw new ProjectRuleViolationException("Leader replacement cannot have a pending exit");
        }
        long outgoingLeaderUserId = project.currentLeadershipTerm().internUserId();
        long previousTermId = project.currentLeadershipTerm().id();
        var change = project.prepareLeaderChange( // → Entity: đóng term cũ, chuẩn bị term mới trong memory
                actorUserId,
                projectInternEligibility(snapshotFor(lockedAccounts, internUserId)),
                clock.instant());

        projects.flush(); // → Repo: UPDATE leadership_terms (đóng term cũ)
        project.completeLeaderChange(actorUserId, change); // → Entity: mở term mới
        projects.flush(); // → Repo: INSERT leadership_term mới
        notifyLeadershipChanged( // → NotificationService
                projectId,
                "LEADER_CHANGED",
                List.of(outgoingLeaderUserId, change.replacement().internUserId()));
        invitations.findLockedPendingByTerm(projectId, previousTermId).forEach(invitation -> { // hủy lời mời của Leader
                                                                                               // cũ
            invitation.resolve(
                    InvitationStatus.REVOKED,
                    InvitationResolutionCode.LEADER_CHANGED,
                    null,
                    null,
                    clock.instant());
            notifyInvitationResolution(invitation, actorUserId, invitation.resolutionCode());
        });
        invitations.flush(); // → Repo: UPDATE project_invitations
    }

    /**
     * Commits one Leader-selected unfinished Task transfer batch for a pending
     * exit.
     *
     * <p>
     * This deliberate legacy overload resolves the pending request by its source
     * membership and
     * uses the Task producer's no-version compatibility path. New HTTP routes must
     * use the
     * request-bound overload with expected versions so a stale or guessed request
     * identifier
     * cannot transfer a different pending exit.
     * </p>
     *
     * @param actorUserId           authenticated current Leader
     * @param projectId             owning open Project
     * @param sourceMembershipId    pending-exit source membership
     * @param taskIds               selected unfinished Task identifiers
     * @param recipientMembershipId eligible current recipient not pending exit
     * @return atomic Task transfer result
     */
    // Leader chuyển công việc chưa xong của thành viên sắp rời sang người nhận khác
    // (phiên bản tương thích).
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
     * Commits one Leader-selected unfinished Task transfer batch only for the
     * supplied pending
     * exit request. This deliberate legacy overload uses the Task producer's
     * no-version
     * compatibility path; callers with a browser Task snapshot must use the
     * map-bearing overload.
     *
     * <p>
     * The request route is checked before the Project lock, then its row is locked
     * and its
     * pending status, Project, and target membership are rechecked after the
     * Project lock. Account,
     * Project, exit-request, and Task locks therefore cover the full mutation,
     * while a stale or
     * mismatched request fails before any Task or retained history changes.
     * </p>
     *
     * @param actorUserId           authenticated current Leader
     * @param projectId             owning open Project
     * @param requestId             pending exit request from the route
     * @param sourceMembershipId    pending-exit source membership
     * @param taskIds               selected unfinished Task identifiers
     * @param recipientMembershipId eligible current recipient not pending exit
     * @return atomic Task transfer result
     * @throws ProjectAccessDeniedException  when the request is outside the Project
     *                                       or source
     *                                       membership is not its target
     * @throws ProjectRuleViolationException when the request is terminal or the
     *                                       Project changed
     */
    // Leader chuyển công việc theo yêu cầu rời/chờ duyệt của một thành viên.
    // Luồng: xác nhận yêu cầu thuộc đúng Project → kiểm tra quyền Leader và người
    // nhận
    // → giao bước chuyển công việc cho module Task xử lý theo lô.
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

    /**
     * Commits one pending-exit Task batch with the client-observed version of every
     * selected Task.
     *
     * <p>
     * The request-bound route is checked before this service copies and validates
     * the required
     * version map. The map must be non-null, have exactly the selected Task IDs,
     * and contain
     * non-negative versions; Project locks and source/recipient checks then precede
     * the Task
     * producer call. A stale version raises the Task-owned conflict, rolling back
     * the whole
     * transaction before assignment or notification mutation. Deliberate legacy
     * callers use the
     * separate overload without a map.
     * </p>
     *
     * @param actorUserId           authenticated current Leader
     * @param projectId             owning open Project
     * @param requestId             pending exit request from the route
     * @param sourceMembershipId    pending-exit source membership
     * @param taskIds               selected unfinished Task identifiers
     * @param expectedTaskVersions  client-observed version for each selected Task
     * @param recipientMembershipId eligible current recipient not pending exit
     * @return atomic Task transfer result
     * @throws ProjectAccessDeniedException  when the request is outside the Project
     *                                       or source
     *                                       membership is not its target
     * @throws ProjectRuleViolationException when the request is terminal, the
     *                                       Project changed, or
     *                                       the required Task/version map is null
     *                                       or incomplete
     */
    // === TRANSFER TASKS | Service — chuyển task khi member sắp rời ===
    @Transactional
    public TaskTransferResult transferTasks(
            long actorUserId,
            long projectId,
            long requestId,
            long sourceMembershipId,
            Set<Long> taskIds,
            Map<Long, Long> expectedTaskVersions,
            long recipientMembershipId) {
        var route = exitRequestRoute(requestId);
        if (route.projectId() != projectId) {
            throw new ProjectAccessDeniedException();
        }
        Map<Long, Long> immutableExpectedTaskVersions = immutableTaskVersions(taskIds, expectedTaskVersions);
        return transferTasksInternal(
                actorUserId,
                projectId,
                requestId,
                sourceMembershipId,
                taskIds,
                immutableExpectedTaskVersions,
                Map.of(),
                recipientMembershipId);
    }

    /**
     * Commits a pending-exit Task batch with both browser Task versions and
     * worked-Task forecasts.
     *
     * <p>
     * This is the public Project workflow seam for a mixed batch. The Project
     * boundary checks
     * request ownership and version-map shape, then carries the immutable forecast
     * map into the
     * Task service. The Task transaction validates the worked/unworked cardinality
     * and flushes
     * forecast rows and assignments together, so a stale Task or invalid forecast
     * leaves every
     * selected Task, forecast, and notification unchanged.
     * </p>
     *
     * @param actorUserId           authenticated current Leader
     * @param projectId             owning open Project
     * @param requestId             pending exit request from the route
     * @param sourceMembershipId    pending-exit source membership
     * @param taskIds               selected unfinished Task identifiers
     * @param expectedTaskVersions  client-observed version for each selected Task
     * @param forecastInputs        one forecast input for each worked selected
     *                              Task, and none for
     *                              unworked selected Tasks
     * @param recipientMembershipId eligible current recipient not pending exit
     * @return atomic Task transfer result
     * @throws ProjectAccessDeniedException  when the request is outside the Project
     *                                       or source
     *                                       membership is not its target
     * @throws ProjectRuleViolationException when the route snapshot or input maps
     *                                       are invalid
     */
    // Leader chuyển công việc kèm dự báo nỗ lực còn lại cho công việc đã có tiến
    // độ.
    // Luồng: giống chuyển công việc có phiên bản, thêm bước nhập dự báo cho công
    // việc đã làm dở.
    @Transactional
    public TaskTransferResult transferTasks(
            long actorUserId,
            long projectId,
            long requestId,
            long sourceMembershipId,
            Set<Long> taskIds,
            Map<Long, Long> expectedTaskVersions,
            Map<Long, RemainingEffortForecastInput> forecastInputs,
            long recipientMembershipId) {
        var route = exitRequestRoute(requestId);
        if (route.projectId() != projectId) {
            throw new ProjectAccessDeniedException();
        }
        Map<Long, Long> immutableExpectedTaskVersions = immutableTaskVersions(taskIds, expectedTaskVersions);
        Map<Long, RemainingEffortForecastInput> immutableForecastInputs = immutableForecastInputs(taskIds,
                forecastInputs);
        return transferTasksInternal(
                actorUserId,
                projectId,
                requestId,
                sourceMembershipId,
                taskIds,
                immutableExpectedTaskVersions,
                immutableForecastInputs,
                recipientMembershipId);
    }

    // Chuyển công việc nội bộ (không kèm phiên bản từ màn hình).
    private TaskTransferResult transferTasksInternal(
            long actorUserId,
            long projectId,
            Long requestId,
            long sourceMembershipId,
            Set<Long> taskIds,
            long recipientMembershipId) {
        return transferTasksInternal(
                actorUserId,
                projectId,
                requestId,
                sourceMembershipId,
                taskIds,
                null,
                Map.of(),
                recipientMembershipId);
    }

    // Chuyển công việc nội bộ (có thể kèm phiên bản từ màn hình).
    private TaskTransferResult transferTasksInternal(
            long actorUserId,
            long projectId,
            Long requestId,
            long sourceMembershipId,
            Set<Long> taskIds,
            Map<Long, Long> expectedTaskVersions,
            long recipientMembershipId) {
        return transferTasksInternal(
                actorUserId,
                projectId,
                requestId,
                sourceMembershipId,
                taskIds,
                expectedTaskVersions,
                Map.of(),
                recipientMembershipId);
    }

    // Thực hiện chuyển công việc: Leader xác nhận yêu cầu rời, khóa dữ liệu thành
    // viên,
    // rồi nhờ module Task đổi người phụ trách theo lô (có hoặc không kèm dự báo nỗ
    // lực).
    private TaskTransferResult transferTasksInternal(
            long actorUserId,
            long projectId,
            Long requestId,
            long sourceMembershipId,
            Set<Long> taskIds,
            Map<Long, Long> expectedTaskVersions,
            Map<Long, RemainingEffortForecastInput> forecastInputs,
            long recipientMembershipId) {
        var route = projectRoute(projectId); // đọc leaderId nhẹ — → Repo
        if (!Objects.equals(route.currentLeaderUserId(), actorUserId)) { // chỉ Leader hiện tại
            throw new ProjectAccessDeniedException();
        }
        var snapshotMemberIds = projects.findCurrentInternUserIdsByProjectId(projectId).stream()
                .collect(Collectors.toUnmodifiableSet()); // snapshot member trước lock
        var lockedAccounts = lockAccounts(concat(actorUserId, snapshotMemberIds)); // lock Leader + members
        var project = lockedProject(projectId); // → Repo: FOR UPDATE
        if (!snapshotMemberIds.equals(currentInternUserIds(project))) { // membership đổi giữa chừng → retry
            throw new ProjectRuleViolationException("Project membership changed; retry Task transfer");
        }
        requireOpenProject(project); // project chưa COMPLETED
        var leader = requireCurrentLeader(project, actorUserId, snapshotFor(lockedAccounts, actorUserId));
        var pendingExitRequests = exitRequests.findLockedPendingByProjectId(projectId); // tất cả exit request PENDING
        var pendingExitMembershipIds = pendingExitRequests.stream()
                .map(ProjectExitRequestEntity::targetMembershipId)
                .collect(Collectors.toUnmodifiableSet());
        if (requestId == null) { // legacy: không có requestId trong URL
            if (!pendingExitMembershipIds.contains(sourceMembershipId)) {
                throw new ProjectAccessDeniedException();
            }
        } else {
            var request = lockedExitRequest(requestId, project.id()); // lock + xác nhận request thuộc project
            if (!request.isPending()) {
                throw new ProjectRuleViolationException("Exit request is no longer pending");
            }
            if (request.targetMembershipId() != sourceMembershipId
                    || !pendingExitMembershipIds.contains(sourceMembershipId)) {
                throw new ProjectAccessDeniedException(); // source phải khớp target của request
            }
        }
        var context = queries.taskContext(actorUserId, project, pendingExitMembershipIds); // → Query: DTO context cho
                                                                                           // Task
        if (expectedTaskVersions == null && forecastInputs.isEmpty()) {
            return taskTransfers.transferBatch( // → TaskTransferService: đổi assignee theo lô (không version)
                    context,
                    leader.id(),
                    sourceMembershipId,
                    taskIds,
                    recipientMembershipId);
        }
        return taskTransfers.transferBatch( // → TaskTransferService: có version + forecast
                context,
                leader.id(),
                sourceMembershipId,
                taskIds,
                expectedTaskVersions,
                forecastInputs,
                recipientMembershipId);
    }

    // Kiểm tra map phiên bản task client gửi — đủ, không trùng, không null.
    private static Map<Long, Long> immutableTaskVersions(
            Set<Long> taskIds, Map<Long, Long> expectedTaskVersions) {
        if (expectedTaskVersions == null
                || taskIds == null || taskIds.isEmpty()
                || expectedTaskVersions.size() != taskIds.size()
                || !expectedTaskVersions.keySet().equals(taskIds)
                || expectedTaskVersions.entrySet().stream()
                        .anyMatch(entry -> entry.getKey() == null || entry.getValue() == null
                                || entry.getKey() <= 0 || entry.getValue() < 0)) {
            // Từ chối nếu thiếu/thừa công việc hoặc phiên bản không hợp lệ.
            throw new ProjectRuleViolationException("Submit one version for every selected Task.");
        }
        return Map.copyOf(expectedTaskVersions);
    }

    // Kiểm tra map dự báo nỗ lực còn lại — chỉ cho task đã chọn và đã có tiến độ.
    private static Map<Long, RemainingEffortForecastInput> immutableForecastInputs(
            Set<Long> taskIds, Map<Long, RemainingEffortForecastInput> forecastInputs) {
        if (forecastInputs == null
                || forecastInputs.keySet().stream().anyMatch(Objects::isNull)
                || forecastInputs.keySet().stream().anyMatch(id -> taskIds == null || !taskIds.contains(id))
                || forecastInputs.entrySet().stream().anyMatch(entry -> entry.getValue() == null)) {
            throw new ProjectRuleViolationException("Submit forecasts only for selected Tasks.");
        }
        return Map.copyOf(forecastInputs);
    }

    /**
     * Approves a pending exit using the request's own Project route.
     *
     * <p>
     * This compatibility entry point remains for non-HTTP callers that do not carry
     * a route
     * Project. HTTP handlers must use the route-bound overload below.
     * </p>
     *
     * @param actorMentorUserId authenticated owning Mentor
     * @param requestId         pending exit request identifier
     * @param note              optional retained Mentor decision note
     */
    // Mentor duyệt yêu cầu rời hoặc bị loại (phiên bản tương thích: tự lấy Project
    // từ mã yêu cầu).
    @Transactional
    public void approveExit(long actorMentorUserId, long requestId, String note) {
        var route = exitRequestRoute(requestId);
        approveExit(actorMentorUserId, route.projectId(), requestId, note);
    }

    /**
     * Approves a pending exit only when its nested request belongs to the route
     * Project, the
     * target is no longer Leader, and the target owns no unfinished non-deleted
     * Tasks. Membership
     * closure and request resolution commit in the same transaction.
     *
     * @param actorMentorUserId authenticated owning Mentor
     * @param projectId         Project encoded by the HTTP route
     * @param requestId         pending exit request identifier encoded by the
     *                          nested route
     * @param note              optional retained Mentor decision note
     * @throws ProjectAccessDeniedException  when the nested request is outside the
     *                                       route Project
     * @throws ProjectRuleViolationException when the request or target is not
     *                                       approval-ready
     */
    // === EXIT APPROVAL | Service — Mentor duyệt yêu cầu rời ===
    @Transactional
    public void approveExit(long actorMentorUserId, long projectId, long requestId, String note) {
        var route = exitRequestRoute(requestId); // lấy projectId + requesterUserId — → Repo
        requireRouteProject(projectId, route.projectId()); // URL nested phải khớp project thật
        var locked = lockOwnedProject(actorMentorUserId, projectId, // lock account → lock project (Mentor-owned)
                List.of(route.requesterUserId()));
        var project = locked.project();
        requireOpenProject(project); // project chưa COMPLETED
        var request = lockedExitRequest(requestId, project.id()); // lock row exit request
        if (!request.isPending()) { // chỉ duyệt request đang PENDING
            throw new ProjectRuleViolationException("Exit request is no longer pending");
        }
        var target = membershipInProject(project, request.targetMembershipId()); // tìm membership bị loại/rời
        if (!target.isCurrent()) {
            throw new ProjectRuleViolationException("Exit target is no longer current");
        }
        if (project.currentLeader().id().equals(target.id())) { // phải đổi Leader trước khi duyệt loại Leader
            throw new ProjectRuleViolationException("Replace the current Leader before approval");
        }
        if (taskTransfers.unfinishedCount(project.id(), target.id()) > 0) { // phải chuyển hết task chưa xong
            throw new ProjectRuleViolationException("Transfer all unfinished Tasks before approval");
        }
        var at = clock.instant();
        target.close(at, actorMentorUserId); // → Entity: đóng membership — Intern rời project
        request.resolve(ProjectExitRequestStatus.APPROVED, normalizeDecisionNote(note), actorMentorUserId, at);
        projects.flush(); // → Repo: UPDATE project_memberships
        exitRequests.flush(); // → Repo: UPDATE exit_requests
        notifyMembershipChanged( // → NotificationService
                project.id(),
                "MEMBER_REMOVED",
                List.of(target.internUserId()),
                locked.notificationRecipients());
        notifyExitResolved( // → NotificationService: thông báo kết quả duyệt
                request,
                project,
                project.currentLeader().internUserId(),
                locked.notificationRecipients());
    }

    /**
     * Rejects a pending exit using the request's own Project route.
     *
     * <p>
     * This compatibility entry point remains for non-HTTP callers that do not carry
     * a route
     * Project. HTTP handlers must use the route-bound overload below.
     * </p>
     *
     * @param actorMentorUserId authenticated owning Mentor
     * @param requestId         pending exit request identifier
     * @param note              optional retained Mentor decision note
     */
    // Mentor từ chối yêu cầu rời hoặc bị loại (phiên bản tương thích: tự lấy
    // Project từ mã yêu cầu).
    @Transactional
    public void rejectExit(long actorMentorUserId, long requestId, String note) {
        var route = exitRequestRoute(requestId);
        rejectExit(actorMentorUserId, route.projectId(), requestId, note);
    }

    /**
     * Rejects a pending exit only when its nested request belongs to the route
     * Project. Rejection
     * retains membership and does not undo completed transfer batches.
     *
     * @param actorMentorUserId authenticated owning Mentor
     * @param projectId         Project encoded by the HTTP route
     * @param requestId         pending exit request identifier encoded by the
     *                          nested route
     * @param note              optional retained Mentor decision note
     * @throws ProjectAccessDeniedException  when the nested request is outside the
     *                                       route Project
     * @throws ProjectRuleViolationException when the request is no longer pending
     */
    // === EXIT APPROVAL | Service — Mentor từ chối ===
    @Transactional
    public void rejectExit(long actorMentorUserId, long projectId, long requestId, String note) {
        var route = exitRequestRoute(requestId); // lấy projectId + requesterUserId — → Repo
        requireRouteProject(projectId, route.projectId()); // URL nested phải khớp project thật
        var locked = lockOwnedProject(actorMentorUserId, projectId, // lock account → lock project (Mentor-owned)
                List.of(route.requesterUserId()));
        var project = locked.project();
        requireOpenProject(project); // project chưa COMPLETED
        var request = lockedExitRequest(requestId, project.id()); // lock row exit request
        if (!request.isPending()) { // chỉ từ chối request đang PENDING
            throw new ProjectRuleViolationException("Exit request is no longer pending");
        }
        var target = membershipInProject(project, request.targetMembershipId());
        if (!target.isCurrent()) {
            throw new ProjectRuleViolationException("Exit target is no longer current");
        }
        request.resolve(ProjectExitRequestStatus.REJECTED, normalizeDecisionNote(note), actorMentorUserId,
                clock.instant()); // → Entity: giữ membership
        exitRequests.flush(); // → Repo: UPDATE exit_requests
        notifyExitResolved( // → NotificationService: thông báo kết quả từ chối
                request,
                project,
                project.currentLeader().internUserId(),
                locked.notificationRecipients());
    }

    /**
     * Directly removes a current member as an atomic Mentor shortcut. All
     * unfinished Tasks move
     * to the current Leader; removing that Leader first appoints the supplied
     * eligible replacement
     * and then moves Tasks to the replacement. Completed Tasks and retained
     * attribution remain.
     *
     * @param actorMentorUserId       authenticated owning Mentor
     * @param projectId               open Project
     * @param targetMembershipId      current membership to close
     * @param replacementLeaderUserId required only when removing the current Leader
     */
    // === DIRECT REMOVE | Service — Mentor loại member ngay ===
    @Transactional
    public void directRemoveMember(
            long actorMentorUserId,
            long projectId,
            long targetMembershipId,
            Long replacementLeaderUserId) {
        var locked = lockOwnedProject(actorMentorUserId, projectId); // lock account → lock project (Mentor-owned)
        var project = locked.project();
        requireOpenProject(project); // project chưa COMPLETED
        var target = membershipInProject(project, targetMembershipId); // tìm membership cần loại
        if (!target.isCurrent()) {
            throw new ProjectRuleViolationException("Removal target must be current");
        }
        var leader = project.currentLeader();
        if (Objects.equals(locked.initialLeaderUserId(), target.internUserId())
                && !leader.id().equals(target.id())) { // Leader đổi giữa chừng → retry
            throw new ProjectRuleViolationException("Project leadership changed; retry the removal");
        }
        if (taskTransfers.workedUnfinishedCount(project.id(), target.id()) > 0) { // task đã làm dở phải forecast trước
            throw new ProjectRuleViolationException(
                    "Transfer worked unfinished Tasks with a forecast before removing this member");
        }
        if (leader.id().equals(target.id())) { // đang loại Leader → phải chỉ định Leader thay thế
            if (replacementLeaderUserId == null || replacementLeaderUserId <= 0) {
                throw new ProjectRuleViolationException("Removing the current Leader requires a replacement");
            }
            var replacement = currentMemberTarget(project, replacementLeaderUserId); // Leader mới phải là member hiện
                                                                                     // tại
            if (exitRequests.findLockedPendingByTargetMembershipId(replacement.id()).isPresent()) {
                throw new ProjectRuleViolationException("Leader replacement cannot have a pending exit");
            }
            long outgoingLeaderUserId = leader.internUserId();
            requireEligibleInternForProjectTarget(
                    snapshotFor(locked.accounts(), replacementLeaderUserId));
            long previousTermId = project.currentLeadershipTerm().id();
            var change = project.prepareLeaderChange( // → Entity: đổi Leader trước khi loại
                    actorMentorUserId,
                    projectInternEligibility(snapshotFor(locked.accounts(), replacementLeaderUserId)),
                    clock.instant());
            projects.flush(); // → Repo: UPDATE leadership_terms
            project.completeLeaderChange(actorMentorUserId, change);
            projects.flush(); // → Repo: INSERT leadership_term mới
            notifyLeadershipChanged(
                    projectId,
                    "LEADER_CHANGED",
                    List.of(outgoingLeaderUserId, change.replacement().internUserId()),
                    locked.notificationRecipients());
            invitations.findLockedPendingByTerm(projectId, previousTermId).forEach(invitation -> { // hủy lời mời Leader
                                                                                                   // cũ
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
            leader = project.currentLeader(); // cập nhật leader sau khi đổi
        }
        if (taskTransfers.unfinishedCount(project.id(), target.id()) > 0) { // chuyển task chưa xong sang Leader
            var pendingExitMembershipIds = exitRequests.findLockedPendingByProjectId(projectId).stream()
                    .map(ProjectExitRequestEntity::targetMembershipId)
                    .collect(Collectors.toUnmodifiableSet());
            var context = queries.taskContext(actorMentorUserId, project, pendingExitMembershipIds); // → Query
            taskTransfers.transferAllUnfinished(context, leader.id(), target.id(), leader.id()); // →
                                                                                                 // TaskTransferService
        }
        var at = clock.instant();
        target.close(at, actorMentorUserId); // → Entity: đóng membership
        notifyMembershipChanged(
                project.id(),
                "MEMBER_REMOVED",
                List.of(target.internUserId()),
                locked.notificationRecipients());
        exitRequests.findLockedPendingByTargetMembershipId(target.id()).ifPresent(request -> { // auto duyệt exit
                                                                                               // request nếu có
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
        projects.flush(); // → Repo: UPDATE project_memberships
        exitRequests.flush(); // → Repo: UPDATE exit_requests
    }

    /**
     * Completes an active Project only when every non-deleted Task is DONE. Pending
     * invitations
     * are revoked and pending exits superseded before current intervals close; all
     * retained rows
     * remain available to authorized history readers.
     *
     * @param actorMentorUserId authenticated owning Mentor
     * @param projectId         Project to complete
     */
    // === COMPLETE PROJECT | Service ===
    // Chức năng: mọi Task DONE → ACTIVE→COMPLETED, đóng invitation/exit đang chờ.
    @Transactional
    public void complete(long actorMentorUserId, long projectId) {
        var locked = lockOwnedProject(actorMentorUserId, projectId); // lock account → lock project (Mentor-owned)
        var project = locked.project();
        if (project.status() != ProjectStatus.ACTIVE) { // chỉ complete project đang ACTIVE
            throw new ProjectRuleViolationException("Only an active Project can be completed");
        }
        var progress = taskQueries.projectProgress(projectId); // → TaskQueryService: đếm task DONE/total
        if (progress.done() != progress.totalTasks()) { // mọi task phải DONE
            throw new ProjectRuleViolationException("Every non-deleted Task must be DONE before completion");
        }
        var at = clock.instant();
        invitations.findLockedPendingByProjectId(projectId).forEach(invitation -> { // hủy tất cả lời mời PENDING
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
        invitations.flush(); // → Repo: UPDATE project_invitations
        long currentLeaderUserId = project.currentLeader().internUserId();
        var currentMemberUserIds = project.memberships().stream()
                .filter(ProjectMembershipEntity::isCurrent)
                .map(ProjectMembershipEntity::internUserId)
                .toList();
        exitRequests.findLockedPendingByProjectId(projectId).forEach(request -> { // supersede tất cả exit request
                                                                                  // PENDING
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
        exitRequests.flush(); // → Repo: UPDATE exit_requests
        project.complete(actorMentorUserId, at); // → Entity: ACTIVE → COMPLETED, đóng membership + leadership
        projects.flush(); // → Repo: UPDATE projects + memberships + leadership_terms
        notifyMembershipChanged( // → NotificationService
                project.id(),
                "PROJECT_COMPLETED",
                currentMemberUserIds,
                locked.notificationRecipients());
        notifyLeadershipChanged( // → NotificationService
                project.id(),
                "LEADER_REMOVED",
                List.of(currentLeaderUserId),
                locked.notificationRecipients());
    }

    /**
     * Locks all current-member Accounts and Intern profiles in ascending Account
     * order, then
     * locks the Project and re-evaluates visibility, lifecycle, current leadership,
     * active
     * eligible memberships, and pending-exit target IDs for a Task mutation.
     * Pending targets stay
     * in the active-member list so existing Task rights remain valid; the separate
     * ID set lets
     * Task reject only new/self-assignment. The all-current-member pass is
     * intentionally bounded
     * by the Project's current membership cardinality and avoids a second
     * Account-to-Project
     * lock-order cycle. When called inside {@code TaskService}'s transaction, every
     * lock and DTO
     * snapshot remains held through the outer commit or rollback.
     *
     * @param actorUserId authenticated Task actor
     * @param projectId   owning Project identifier
     * @return DTO-only locked mutation context
     * @throws ProjectAccessDeniedException for missing or unauthorized Projects
     */
    // Chuẩn bị thông tin project cho module Task (ai là Leader, ai đang chờ
    // rời...).
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
     * Completes an Intern after recomputing Project leadership and Task ownership
     * under the shared lock order.
     *
     * <p>
     * The active Admin and target Account/profile rows are locked first. Every
     * current Project is then locked in
     * ascending identifier order before unfinished Tasks are counted. Because all
     * Project and Task mutations use the
     * same Account-before-Project order, the guard remains stable until the
     * Account-owned completion commits.
     * </p>
     *
     * @param adminUserId  active Admin performing the terminal action
     * @param internUserId Intern account being completed
     * @throws IllegalArgumentException when the actor or target account shape is
     *                                  invalid
     * @throws IllegalStateException    when Project/Task readiness or the
     *                                  internship state rejects completion
     */
    // Admin kết thúc thực tập intern — kiểm tra intern không còn là Leader hoặc
    // task chưa xong trước khi đổi trạng thái tài khoản.
    @Transactional
    public void completeInternship(long adminUserId, long internUserId) {
        accounts.completeInternship(
                internUserId,
                adminUserId,
                lockedInternshipLifecycleGuard(adminUserId, internUserId));
    }

    /**
     * Withdraws an Intern after recomputing Project leadership and Task ownership
     * under the shared lock order.
     *
     * <p>
     * The readiness transaction is identical to completion. Account withdrawal then
     * deactivates the Intern and
     * expires existing sessions before the surrounding transaction commits.
     * </p>
     *
     * @param adminUserId  active Admin performing the terminal action
     * @param internUserId Intern account being withdrawn
     * @throws IllegalArgumentException when the actor or target account shape is
     *                                  invalid
     * @throws IllegalStateException    when Project/Task readiness or the
     *                                  internship state rejects withdrawal
     */
    // Admin rút intern khỏi thực tập — cùng điều kiện kiểm tra như hoàn thành thực
    // tập.
    @Transactional
    public void withdrawInternship(long adminUserId, long internUserId) {
        accounts.withdrawInternship(
                internUserId,
                adminUserId,
                lockedInternshipLifecycleGuard(adminUserId, internUserId));
    }

    /**
     * Activates a planned Project while holding its write lock. Current Account
     * eligibility and
     * Task-assignee validity are checked inside the same transaction after all
     * current-member
     * Account/profile locks are acquired in ascending order; any failure leaves the
     * Project
     * planned and preserves Tasks and interval history.
     *
     * @param actorUserId authenticated owning Mentor
     * @param projectId   planned Project to activate
     */
    // === ACTIVATE PROJECT | Service ===
    // Chức năng: PLANNED → ACTIVE sau khi kiểm tra member + task assignee.

    @Transactional
    public void activate(long actorUserId, long projectId) {
        var route = projectRoute(projectId); // đọc mentorId nhẹ — → Repo
        if (route.mentorUserId() != actorUserId) { // chỉ Mentor sở hữu project mới được activate
            throw new ProjectAccessDeniedException();
        }
        // Bước chuẩn bị trước khi activate — chống race condition khi nhiều request cùng lúc:
        // 1) Chụp snapshot danh sách Intern đang là member (chưa lock DB)
        var snapshotMemberIds = projects.findCurrentInternUserIdsByProjectId(projectId).stream()
                .collect(Collectors.toUnmodifiableSet()); // → Repo: đọc userId member hiện tại
        // 2) Lock Mentor + toàn bộ member theo thứ tự ID tăng dần (Account trước, Project sau)
        var lockedAccounts = lockAccounts(concat(actorUserId, snapshotMemberIds)); // → AccountService: FOR UPDATE
        requireActiveMentor(snapshotFor(lockedAccounts, actorUserId)); // Mentor vẫn ACTIVE sau lock
        // 3) Lock project — giữ quyền sửa cho đến khi activate xong
        var project = lockedProject(projectId); // → Repo: SELECT ... FOR UPDATE
        project.authorizeOwner(actorUserId); // xác nhận lại Mentor là owner trên aggregate
        // 4) So snapshot trước lock vs membership sau lock — ai add/xóa member giữa chừng thì bắt retry
        if (!snapshotMemberIds.equals(currentInternUserIds(project))) {
            throw new ProjectRuleViolationException("Project membership changed; retry activation");
        }
        // === Bước 5: Kiểm tra điều kiện activate — member + task ===
        // Lọc member đang current VÀ còn eligible (ACTIVE + trong kỳ thực tập)
        var activeMemberships = project.memberships().stream()
                .filter(membership -> membership.isCurrent()
                        && eligibleInternForProjectMutation(
                                snapshotFor(lockedAccounts, membership.internUserId())))
                .toList();
        // Lấy membershipId — dùng để query task: assignee có nằm ngoài member hiện tại không
        var activeMembershipIds = activeMemberships.stream()
                .map(membership -> membership.id())
                .collect(Collectors.toUnmodifiableSet());
        // Lấy internUserId — truyền vào Entity.activate() để check Leader còn trong list
        Set<Long> activeInternUserIds = activeMemberships.stream()
                .map(membership -> membership.internUserId())
                .collect(Collectors.toUnmodifiableSet());
        // Đếm task đang gán cho người KHÔNG còn là member hiện tại → phải = 0 mới activate được
        var allTaskAssigneesAreCurrent = taskQueries.countCurrentTasksAssignedOutside(
                projectId, activeMembershipIds) == 0; // → TaskQueryService

        // === Bước 6: Ghi DB — PLANNED → ACTIVE ===
        // Entity kiểm tra: có ít nhất 1 member, Leader thuộc active list, task assignee hợp lệ
        project.activate(actorUserId, activeInternUserIds, allTaskAssigneesAreCurrent, clock.instant());
        projects.flush(); // → Repo: UPDATE projects (status, activated_at)
    }

    /**
     * Deletes an owning Mentor's planned Project draft.
     *
     * <p>
     * Project and all project-owned rows are removed atomically in dependency
     * order. The
     * persistence context is cleared before native child deletes so managed
     * invitation/exit
     * rows cannot retain references to leadership or membership rows that are about
     * to be
     * removed. ACTIVE and COMPLETED Projects are rejected before any delete is
     * attempted.
     * </p>
     *
     * @param actorUserId authenticated owning Mentor
     * @param projectId   planned Project identifier
     * @throws ProjectAccessDeniedException  when the actor does not own the Project
     * @throws ProjectRuleViolationException when the Project is not planned
     */
    // === DELETE PROJECT | Service ===
    // Chức năng: xóa cascade project PLANNED (native SQL theo thứ tự FK).
    @Transactional
    public void delete(long actorUserId, long projectId) {
        var locked = lockOwnedProject(actorUserId, projectId); // lock account → lock project (Mentor-owned)
        locked.project().requireDeletable(actorUserId); // chỉ xóa project PLANNED
        entityManager.flush(); // đẩy pending changes trước khi clear context
        entityManager.clear(); // xóa managed entities — tránh FK conflict khi native DELETE
        deleteProjectRows(projectId); // native SQL xóa con→cha (task, invitation, membership...)
    }

    /** Removes a planned aggregate in child-to-parent foreign-key order. */
    // Xóa dữ liệu project theo thứ tự con→cha (task, invitation, membership...) rồi
    // xóa projects.
    private void deleteProjectRows(long projectId) {
        nativeDelete("""
                delete from task_comments
                where task_id in (select id from tasks where project_id = :projectId)
                """, projectId);
        nativeDelete("delete from task_work_logs where project_id = :projectId", projectId);
        nativeDelete("delete from tasks where project_id = :projectId", projectId);
        nativeDelete(
                "delete from project_membership_exit_requests where project_id = :projectId",
                projectId);
        nativeDelete("delete from project_invitations where project_id = :projectId", projectId);
        nativeDelete("delete from project_leadership_terms where project_id = :projectId", projectId);
        nativeDelete("delete from project_memberships where project_id = :projectId", projectId);
        nativeDelete("delete from projects where id = :projectId", projectId);
    }

    // Chạy câu SQL native DELETE với tham số projectId.
    private void nativeDelete(String sql, long projectId) {
        entityManager.createNativeQuery(sql)
                .setParameter("projectId", projectId)
                .executeUpdate();
    }

    // === SERVICE HELPERS | lock + route + notification ===
    // Lấy project và giữ quyền sửa cho đến khi thao tác xong (tránh hai người sửa
    // cùng lúc).
    private ProjectEntity lockedProject(long projectId) {
        return projects.findLockedById(projectId).orElseThrow(ProjectAccessDeniedException::new); // → Repo
    }

    // Đọc route nhẹ (mentorId, leaderId) trước khi lock project — tránh lock không
    // cần thiết.
    private ProjectMutationRoute projectRoute(long projectId) {
        return projects.findMutationRouteById(projectId)
                .orElseThrow(ProjectAccessDeniedException::new);
    }

    // Lấy projectId + invitedInternUserId từ invitationId (dùng khi chưa có
    // projectId trong URL).
    private ProjectInvitationRoute invitationRoute(long invitationId) {
        return invitations.findRouteById(invitationId)
                .orElseThrow(ProjectAccessDeniedException::new);
    }

    // Route phục vụ thông báo lời mời: intern được mời, leader gửi, mentor chủ
    // project.
    private ProjectInvitationNotificationRoute invitationNotificationRoute(long invitationId) {
        return invitations.findNotificationRouteById(invitationId)
                .orElseThrow(ProjectAccessDeniedException::new);
    }

    // Lấy projectId từ exit-requestId (yêu cầu rời/bị loại).
    private ProjectExitRequestRoute exitRequestRoute(long requestId) {
        return exitRequests.findRouteById(requestId)
                .orElseThrow(ProjectAccessDeniedException::new);
    }

    // URL nested phải khớp projectId thật — chống gọi nhầm project khác.
    private static void requireRouteProject(long expectedProjectId, long actualProjectId) {
        if (expectedProjectId != actualProjectId) {
            throw new ProjectAccessDeniedException();
        }
    }

    // Overload không có thêm recipient — gọi overload đầy đủ bên dưới.
    private LockedOwnedProject lockOwnedProject(long actorMentorUserId, long projectId) {
        return lockOwnedProject(actorMentorUserId, projectId, List.of());
    }

    /**
     * Establishes the Account/profile-before-Project lock order for Mentor-owned
     * mutations.
     *
     * <p>
     * Additional scalar account IDs cover retained recipients that are not current
     * members,
     * especially a historical exit requester resolved after leadership replacement.
     * Pending exit
     * requester IDs are also read as scalars so completion and direct removal
     * cannot introduce a
     * post-Project Account lock through their retained decision notifications.
     * Immutable
     * notification recipient facts are resolved after the Account/profile locks and
     * before the
     * Project lock, then reused by every notification emitted by the locked
     * mutation.
     * </p>
     *
     * @param actorMentorUserId    authenticated owning Mentor
     * @param projectId            Project identifier
     * @param additionalAccountIds immutable route recipients required by this
     *                             mutation
     * @return locked Project, initial Leader snapshot, Account eligibility
     *         snapshots, and
     *         pre-resolved recipient facts
     */
    // Chuẩn bị trước khi Mentor sửa project: xác nhận quyền sở hữu và danh sách
    // người cần nhận thông báo.
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

    // Xác nhận yêu cầu rời thuộc đúng project trước khi xử lý.
    private ProjectExitRequestEntity lockedExitRequest(long requestId, long projectId) {
        var request = exitRequests.findLockedById(requestId)
                .orElseThrow(ProjectAccessDeniedException::new);
        if (request.projectId() != projectId) {
            throw new ProjectAccessDeniedException();
        }
        return request;
    }

    // Tìm membership theo ID trong aggregate; sai project → AccessDenied thay vì
    // RuleViolation.
    private ProjectMembershipEntity membershipInProject(ProjectEntity project, long membershipId) {
        try {
            return project.membership(membershipId);
        } catch (ProjectRuleViolationException exception) {
            throw new ProjectAccessDeniedException();
        }
    }

    // Lấy membership hiện tại của Intern trong project (đổi Leader, add member...).
    private ProjectMembershipEntity currentMemberTarget(ProjectEntity project, long internUserId) {
        try {
            return project.currentMember(internUserId);
        } catch (ProjectRuleViolationException exception) {
            throw new ProjectAccessDeniedException();
        }
    }

    // Xác nhận actor là Leader hiện tại và Intern còn eligible (thao tác của
    // Leader).
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

    // Xác nhận actor còn là thành viên hiện tại (xin rời, hủy exit...).
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

    // Mỗi thành viên chỉ được có một yêu cầu rời đang chờ duyệt.
    private void ensureNoPendingExit(long targetMembershipId) {
        if (exitRequests.findLockedPendingByTargetMembershipId(targetMembershipId).isPresent()) {
            throw new ProjectRuleViolationException("A pending exit request already targets this member");
        }
    }

    // Mentor add trực tiếp → hủy lời mời PENDING trùng Intern (SUPERSEDED).
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
     * Publishes the affected Interns for a retained Project membership interval
     * change.
     *
     * <p>
     * The caller remains inside the Project transaction, so the notification rows
     * commit or
     * roll back with the membership mutation. An empty recipient collection is
     * valid for no-op
     * closure paths, although current Project transitions always provide at least
     * one account.
     * </p>
     *
     * @param projectId       owning Project identifier
     * @param transition      retained membership transition
     * @param affectedUserIds Intern accounts whose membership interval changed
     */
    private void notifyMembershipChanged(
            long projectId, String transition, Collection<Long> affectedUserIds) {
        publishMembershipChanged(projectId, transition, notificationRecipients(affectedUserIds)); // →
                                                                                                  // NotificationService.publish
    }

    // Overload: dùng recipientFacts đã lock (complete, directRemove...).
    private void notifyMembershipChanged(
            long projectId,
            String transition,
            Collection<Long> affectedUserIds,
            Map<Long, NotificationRecipient> recipientFacts) {
        publishMembershipChanged(
                projectId, transition, notificationRecipients(affectedUserIds, recipientFacts));
    }

    // Gửi thông báo thay đổi membership (Create Project: INITIAL_MEMBER_ADDED).
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
     * Publishes the outgoing and incoming Leaders for a retained leadership-term
     * mutation.
     *
     * <p>
     * Initial appointment supplies only the incoming Leader; final Project closure
     * supplies
     * only the outgoing Leader. The Platform service collapses any duplicate
     * account IDs.
     * </p>
     *
     * @param projectId       owning Project identifier
     * @param transition      retained leadership transition
     * @param affectedUserIds outgoing/incoming Leader accounts as applicable
     */
    private void notifyLeadershipChanged(
            long projectId, String transition, Collection<Long> affectedUserIds) {
        publishLeadershipChanged(projectId, transition, notificationRecipients(affectedUserIds)); // →
                                                                                                  // NotificationService.publish
    }

    // Overload: dùng recipientFacts đã lock.
    private void notifyLeadershipChanged(
            long projectId,
            String transition,
            Collection<Long> affectedUserIds,
            Map<Long, NotificationRecipient> recipientFacts) {
        publishLeadershipChanged(
                projectId, transition, notificationRecipients(affectedUserIds, recipientFacts));
    }

    // Gửi thông báo thay đổi Leader (Create Project: INITIAL_LEADER_ASSIGNED).
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
     * Persists the invitation-created notification in the same Project transaction
     * as the pending row.
     *
     * @param invitation newly persisted invitation
     */
    // Thông báo cho Intern: có lời mời mới (cùng transaction với INSERT
    // invitation).
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
     * Publishes an invitation response to the retained issuing Leader and owning
     * Mentor.
     *
     * @param invitation     resolved invitation
     * @param mentorUserId   owning Mentor
     * @param resolutionCode retained response code
     */
    // Intern accept/decline → thông báo Leader gửi lời mời và Mentor.
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
     * Publishes invitation revocation or supersession to invitee and retained
     * Leader/Mentor.
     *
     * @param invitation     resolved invitation
     * @param mentorUserId   owning Mentor
     * @param resolutionCode retained terminal reason
     */
    // Lời mời bị thu hồi/hết hạn/đổi Leader → thông báo intern + leader + mentor.
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

    // Overload dùng recipient đã resolve sẵn khi lockOwnedProject (tránh lock
    // account lần nữa).
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

    // Gửi thông báo khi lời mời kết thúc (chấp nhận, từ chối, thu hồi hoặc bị thay
    // thế).
    private void publishInvitationResolution(
            ProjectInvitationEntity invitation,
            InvitationResolutionCode resolutionCode,
            List<NotificationRecipient> recipients) {
        notifications.publish(
                invitationEvent(resolutionCode),
                new NotificationAction(invitationActionUrl(invitation.projectId(), invitation.id()), false),
                recipients);
    }

    // Dựng NotificationEvent cho mọi loại kết thúc lời mời (theo resolutionCode).
    private NotificationEvent invitationEvent(InvitationResolutionCode resolutionCode) {
        return new NotificationEvent(
                NotificationType.PROJECT_INVITATION_RESOLVED,
                resolutionCode.name(),
                "Project invitation updated",
                "A Project invitation is now " + resolutionCode + ".");
    }

    /**
     * Persists a request notification with the request-type-specific NOT-010
     * recipients.
     *
     * @param request newly persisted pending exit request
     * @param project locked owning Project
     */
    // Có yêu cầu rời mới → thông báo Mentor (+ Leader hoặc người bị đề xuất loại).
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
     * Persists a decision or cancellation event for requester, target, and current
     * Leader, collapsing
     * repeated roles to one recipient account.
     *
     * @param request             resolved exit request
     * @param project             locked owning Project
     * @param currentLeaderUserId current Leader before terminal Project closure
     */
    // Yêu cầu rời đã xử lý → thông báo người gửi, người bị ảnh hưởng, Leader hiện
    // tại.
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

    // Overload dùng recipientFacts đã lock trước (complete, directRemove...).
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

    // Gửi thông báo khi yêu cầu rời được duyệt, từ chối, hủy hoặc không còn hiệu
    // lực.
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
        // Một người có thể đứng nhiều vai; chỉ gửi một thông báo cho mỗi người.
        return userIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .map(accounts::requireIdentityById)
                .map(identity -> new NotificationRecipient(identity.id(), identity.email()))
                .toList();
    }

    // Overload: lấy email từ map đã resolve trong lockOwnedProject (không gọi
    // Account lại).
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

    // URL deep-link trong email/thông báo — mở trang chi tiết lời mời.
    private static String invitationActionUrl(long projectId, long invitationId) {
        return "/projects/" + projectId + "/invitations/" + invitationId;
    }

    // URL mở trang project (membership/leadership notification).
    private static String projectActionUrl(long projectId) {
        return "/projects/" + projectId;
    }

    // URL mở trang xử lý yêu cầu rời.
    private static String exitActionUrl(long projectId, long requestId) {
        return "/projects/" + projectId + "/exits/" + requestId;
    }

    private void requireOpenProject(ProjectEntity project) {
        if (project.status() == com.lab.labtimesheet.feature.project.model.ProjectStatus.COMPLETED) {
            // Project đã hoàn thành chỉ xem, không cho sửa.
            throw new ProjectRuleViolationException("Completed Projects are read-only");
        }
    }

    // Khóa một account và trả snapshot (helper ngắn cho chỗ chỉ cần 1 user).
    private LockedAccountMutationEligibility lockAccount(long userId) {
        return snapshotFor(lockAccounts(List.of(userId)), userId);
    }

    private List<LockedAccountMutationEligibility> lockAccounts(Collection<Long> userIds) {
        try {
            // Khóa tài khoản theo thứ tự cố định để hai thao tác không chen ngang nhau.
            return accounts.lockedAccountMutationEligibility(userIds);
        } catch (IllegalArgumentException exception) {
            throw new ProjectAccessDeniedException();
        }
    }

    // Admin kết thúc hoặc rút intern khỏi thực tập: kiểm tra intern không còn là
    // Leader
    // và không còn công việc chưa xong trong các project đang tham gia.
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
        // Khóa từng project intern còn tham gia để đếm đúng Leader và công việc chưa
        // xong.
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

    // Khóa account+profile trước khi thêm member/đổi leader/create — lỗi Account →
    // RuleViolation (form field).
    private List<LockedAccountMutationEligibility> lockAccountsForTargetMutation(
            Collection<Long> userIds) {
        try {
            return accounts.lockedAccountMutationEligibility(userIds); // → AccountService (lock + snapshot)
        } catch (IllegalArgumentException exception) {
            throw new ProjectRuleViolationException("Intern is not eligible for Project membership");
        }
    }

    // Account phải ACTIVE (actor chung — invitation, revoke...).
    private static void requireActiveAccount(LockedAccountMutationEligibility account) {
        if (account.accountStatus() != AccountStatus.ACTIVE) {
            throw new ProjectAccessDeniedException();
        }
    }

    private static void requireActiveMentor(LockedAccountMutationEligibility account) {
        // Phải đồng thời đúng role và trạng thái. Sai actor là authorization failure,
        // không phải lỗi field form.
        if (account.role() != GlobalRole.MENTOR || account.accountStatus() != AccountStatus.ACTIVE) {
            throw new ProjectAccessDeniedException();
        }
    }

    // Actor Intern phải ACTIVE (chưa cần đủ điều kiện thực tập — chỉ check
    // role+status).
    private static void requireActiveInternForProjectActor(LockedAccountMutationEligibility account) {
        if (account.role() != GlobalRole.INTERN || account.accountStatus() != AccountStatus.ACTIVE) {
            throw new ProjectAccessDeniedException();
        }
    }

    // Intern actor (Leader) phải đủ eligible — không đủ → AccessDenied.
    private void requireEligibleInternForProjectActor(LockedAccountMutationEligibility account) {
        if (!eligibleInternForProjectMutation(account)) {
            throw new ProjectAccessDeniedException();
        }
    }

    private void requireEligibleInternForProjectTarget(LockedAccountMutationEligibility account) {
        if (!eligibleInternForProjectMutation(account)) {
            throw new ProjectRuleViolationException("Intern is not eligible for Project membership"); // Controller gắn
                                                                                                      // lỗi vào field
                                                                                                      // initialLeaderUserId
        }
    }

    private ProjectInternEligibility projectInternEligibility(
            LockedAccountMutationEligibility account) {
        return new ProjectInternEligibility(account.userId(), eligibleInternForProjectMutation(account));
    }

    private boolean eligibleInternForProjectMutation(LockedAccountMutationEligibility account) {
        return account.eligibleForProjectMutation()
                && accounts.isEligibleIntern(account.userId(), LocalDate.now(clock)); // → AccountService kiểm tra ngày
                                                                                      // thực tập
    }

    // Tìm snapshot của một userId trong list đã lock (create, addMembers...).
    private static LockedAccountMutationEligibility snapshotFor(
            List<LockedAccountMutationEligibility> snapshots, long userId) {
        return snapshots.stream()
                .filter(snapshot -> snapshot.userId() == userId)
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Locked Account snapshot is missing"));
    }

    // Gộp actorUserId + danh sách user khác thành một list để lock.
    private static List<Long> concat(long actorUserId, Collection<Long> otherUserIds) {
        var ids = new java.util.ArrayList<Long>(otherUserIds.size() + 1);
        ids.add(actorUserId);
        ids.addAll(otherUserIds);
        return ids;
    }

    // Thêm intern/leader/mentor từ pending invitation vào list account cần lock.
    private static void appendInvitationNotificationUsers(
            List<Long> accountIds, Collection<ProjectInvitationNotificationRoute> routes) {
        routes.forEach(route -> {
            accountIds.add(route.invitedInternUserId());
            accountIds.add(route.issuingLeaderUserId());
            accountIds.add(route.mentorUserId());
        });
    }

    // Sau khi khóa tài khoản, đọc lại danh sách người nhận thông báo lời mời;
    // nếu đã đổi thì yêu cầu thử lại để không gửi nhầm hoặc thiếu.
    private void requireStablePendingInvitationRecipients(
            long projectId,
            Collection<Long> selectedInternUserIds,
            Set<Long> expectedRecipientUserIds) {
        var currentRoutes = selectedInternUserIds == null
                ? invitations.findPendingNotificationRoutesByProjectId(projectId)
                : invitations.findPendingNotificationRoutesByProjectIdAndInvitedInternUserIds(
                        projectId, selectedInternUserIds);
        // Danh sách người nhận đổi trong lúc chờ khóa → hủy thao tác và thử lại.
        if (!invitationNotificationUserIds(currentRoutes).equals(expectedRecipientUserIds)) {
            throw new ProjectRuleViolationException("Invitation recipients changed; retry Project mutation");
        }
    }

    // Gom userId từ route lời mời để so sánh trước/sau lock
    // (requireStablePendingInvitationRecipients).
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

    // Set internUserId của các membership đang current trong aggregate (so với
    // snapshot DB).
    private static Set<Long> currentInternUserIds(ProjectEntity project) {
        return project.memberships().stream()
                .filter(ProjectMembershipEntity::isCurrent)
                .map(ProjectMembershipEntity::internUserId)
                .collect(Collectors.toUnmodifiableSet());
    }

    // Lý do xin rời/bị loại không được rỗng.
    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ProjectRuleViolationException("A nonblank exit reason is required");
        }
        return reason.trim();
    }

    // Ghi chú Mentor khi duyệt/từ chối — blank thì lưu null.
    private static String normalizeDecisionNote(String note) {
        return note == null || note.isBlank() ? null : note.trim();
    }

    // Kết quả lockOwnedProject: project đã lock + snapshot account + recipient
    // notification.
    private record LockedOwnedProject(
            ProjectEntity project,
            Long initialLeaderUserId,
            List<LockedAccountMutationEligibility> accounts,
            Map<Long, NotificationRecipient> notificationRecipients) {
    }

}
