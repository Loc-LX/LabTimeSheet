package com.lab.labtimesheet.feature.project.service;

import com.lab.labtimesheet.feature.identity.model.dto.AccountIdentity;
import com.lab.labtimesheet.feature.internship.model.dto.InternshipLifecycleGuard;
import com.lab.labtimesheet.feature.identity.service.AccountService;
import com.lab.labtimesheet.feature.internship.service.InternshipService;
import com.lab.labtimesheet.feature.project.exception.ProjectAccessDeniedException;
import com.lab.labtimesheet.feature.project.exception.ProjectRuleViolationException;
import com.lab.labtimesheet.feature.project.model.ProjectStatus;
import com.lab.labtimesheet.feature.project.model.dto.PendingProjectInvitationView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectActorView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectDashboardSummary;
import com.lab.labtimesheet.feature.project.model.dto.ProjectDetail;
import com.lab.labtimesheet.feature.project.model.dto.ProjectExitReadinessView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectExitRequestHistoryView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectHistoryView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectInvitationHistoryView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectLeadershipTermView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectListPage;
import com.lab.labtimesheet.feature.project.model.dto.ProjectMemberView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectMembershipIntervalView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectSummary;
import com.lab.labtimesheet.feature.project.model.dto.ProjectTaskContext;
import com.lab.labtimesheet.feature.project.model.dto.ProjectTaskMemberView;
import com.lab.labtimesheet.feature.project.model.entity.ProjectEntity;
import com.lab.labtimesheet.feature.project.model.entity.ProjectLeadershipTermEntity;
import com.lab.labtimesheet.feature.project.repository.ProjectExitRequestRepository;
import com.lab.labtimesheet.feature.project.repository.ProjectInvitationRepository;
import com.lab.labtimesheet.feature.project.repository.ProjectRepository;
import com.lab.labtimesheet.feature.project.service.TaskQueryService;
import com.lab.labtimesheet.feature.project.service.TaskTransferService;
import com.lab.labtimesheet.platform.model.GlobalRole;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Provides authorization-aware, DTO-only Project reads to MVC and other features.
 *
 * <p>Admins inspect all Projects, Mentors inspect only owned Projects, and Interns inspect open
 * Projects only while currently enrolled. Completed Projects remain visible to historical
 * members but expose no current Leader or active-member context.
 */
// Service ĐỌC Project. Tìm cụm: Ctrl+F "=== QUERY".
@Service
@RequiredArgsConstructor
public class ProjectQueryService {

    private static final int PROJECT_LIST_PAGE_SIZE = 50;

    private final ProjectRepository projects;
    private final ProjectExitRequestRepository exitRequests;
    private final ProjectInvitationRepository invitations;
    private final AccountService accounts;
    private final InternshipService internships;
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
        return authenticatedActor(email).userId(); // wrapper: chỉ lấy userId
    }

    /**
     * Resolves the active authenticated actor needed for role-aware Project navigation.
     *
     * @param email authenticated email address
     * @return user identifier and immutable global role
     * @throws ProjectAccessDeniedException when no active identity is available
     */
    @Transactional(readOnly = true)
    // Xác định user đăng nhập (userId + role) — dùng ở createForm/create và nhiều chỗ khác.
    public ProjectActorView authenticatedActor(String email) {
        try {
            // email đến từ authenticated Principal. AccountService query theo email và trả DTO,
            // không trả JPA entity ra ngoài feature Account.
            var actor = accounts.requireIdentityByEmail(email);
            if (!"ACTIVE".equals(actor.status().name())) {
                // Account tồn tại nhưng không ACTIVE vẫn không được dùng chức năng Project.
                throw new ProjectAccessDeniedException();
            }
            // Chuyển Account DTO thành Project-owned DTO tối thiểu: userId + role.
            // [01]/[09] dùng role để yêu cầu MENTOR; [10] authorize lại dưới database lock.
            return new ProjectActorView(actor.id(), actor.role().name());
        } catch (IllegalArgumentException exception) {
            // Đổi lỗi identity sang lỗi Project 404 an toàn để không lộ account/email có tồn tại hay không.
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
        return listVisible(actorUserId, PageRequest.of(0, PROJECT_LIST_PAGE_SIZE)); // trang 1, tối đa 50
    }

    /**
     * Lists one bounded page of Projects visible under the actor's current role and Project
     * relationship. Historical Intern membership grants visibility only to completed Projects;
     * the page size is capped so an MVC list cannot turn a catalogue read into an unbounded
     * aggregate load.
     *
     * @param actorUserId active actor user identifier
     * @param requestedPage requested page; null or unpaged input uses the first default page
     * @return ordered authorized summaries within the bounded page
     */
    @Transactional(readOnly = true)
    public List<ProjectSummary> listVisible(long actorUserId, Pageable requestedPage) {
        return listPage(actorUserId, requestedPage).projects();
    }

    /**
     * Lists the complete Project scope authorized for the Daily Project Work Report.
     *
     * <p>The normal navigation method is deliberately capped at 50 rows. Report scope cannot
     * silently truncate a Mentor's owned Projects, so this producer-owned seam uses the complete
     * ownership-filtered query and still returns DTOs only. Admins and Interns do not receive a
     * global Daily preset.</p>
     *
     * @param actorUserId active owning Mentor account identifier
     * @return every authorized Project in deterministic update order
     * @throws ProjectAccessDeniedException when the actor is inactive, unsupported, or not a
     *         Mentor
     */
    @Transactional(readOnly = true)
    public List<ProjectSummary> listAllVisibleForReport(long actorUserId) {
        var actor = activeActor(actorUserId);
        if (actor.role() != GlobalRole.MENTOR) {
            throw new ProjectAccessDeniedException();
        }
        List<ProjectEntity> visible = projects.findByMentorUserIdOrderByUpdatedAtDescIdDesc(actorUserId);
        return visible.stream().map(ProjectQueryService::summary).toList();
    }

    /**
     * Returns the one open Project whose stored current leadership belongs to the active Intern.
     *
     * <p>This is a producer-owned authorization boundary for the Leader Daily report. The
     * caller must provide an active Intern identity and an exact Project identifier; membership,
     * the global role alone, and a guessed identifier never grant access. Completed Projects are
     * intentionally excluded because completion closes the current leadership term.</p>
     *
     * @param actorUserId active authenticated actor identifier
     * @param projectId exact Project identifier requested by the actor
     * @return the authorized Project summary
     * @throws ProjectAccessDeniedException when the actor is not an Intern, the Project is
     *         missing, completed, or not currently led by the actor
     */
    @Transactional(readOnly = true)
    public ProjectSummary currentLeaderProjectForDailyReport(long actorUserId, long projectId) {
        var actor = activeActor(actorUserId);
        if (actor.role() != GlobalRole.INTERN) {
            throw new ProjectAccessDeniedException();
        }

        ProjectEntity project = projects.findById(projectId)
                .orElseThrow(ProjectAccessDeniedException::new);
        if (project.status() != ProjectStatus.PLANNED && project.status() != ProjectStatus.ACTIVE) {
            throw new ProjectAccessDeniedException();
        }
        if (!project.hasCurrentMember(actorUserId)) {
            throw new ProjectAccessDeniedException();
        }
        long currentLeaderId;
        try {
            currentLeaderId = project.currentLeader().internUserId();
        } catch (ProjectRuleViolationException malformedLeadership) {
            throw new ProjectAccessDeniedException();
        }
        if (currentLeaderId != actorUserId) {
            throw new ProjectAccessDeniedException();
        }
        return summary(project);
    }

    /**
     * Lists every open Project whose current Leader is the active Intern.
     *
     * <p>This producer-owned boundary powers conditional Daily-report navigation. The repository
     * filters the current leadership term, current membership, and open lifecycle before rows
     * become DTOs; a former Leader, ordinary member, or completed Project therefore cannot make
     * the navigation entry appear.</p>
     *
     * @param actorUserId active authenticated Intern account identifier
     * @return authorized current-led Project summaries in deterministic update order
     * @throws ProjectAccessDeniedException when the actor is inactive or not an Intern
     */
    @Transactional(readOnly = true)
    public List<ProjectSummary> listCurrentLeaderProjectsForDailyReport(long actorUserId) {
        var actor = activeActor(actorUserId);
        if (actor.role() != GlobalRole.INTERN) {
            throw new ProjectAccessDeniedException();
        }
        return projects.findCurrentLeaderProjectsByInternUserId(actorUserId).stream()
                .map(ProjectQueryService::summary)
                .toList();
    }

    /**
     * Checks the current-Leader Daily capability without loading Project summaries.
     *
     * <p>The shared layout calls this Boolean producer for ordinary pages. The ordered Project
     * list remains exclusive to the Daily no-selection landing flow.</p>
     *
     * @param actorUserId active authenticated Intern account identifier
     * @return true when the actor currently leads at least one PLANNED/ACTIVE Project
     * @throws ProjectAccessDeniedException when the actor is inactive or not an Intern
     */
    @Transactional(readOnly = true)
    public boolean hasCurrentLeaderProjectForDailyReport(long actorUserId) {
        var actor = activeActor(actorUserId);
        if (actor.role() != GlobalRole.INTERN) {
            throw new ProjectAccessDeniedException();
        }
        return projects.existsCurrentLeaderProjectByInternUserId(actorUserId);
    }

    /**
     * Lists one bounded page of Projects and exposes whether an authorized continuation exists.
     * The returned page is role-filtered before its rows are mapped to DTOs, and its continuation
     * flags come from the same repository slice rather than from a truncated display collection.
     *
     * @param actorUserId active actor user identifier
     * @param requestedPage requested zero-based Spring page; null or unpaged input uses page zero
     * @return one-based MVC page metadata and authorized summaries
     */
    @Transactional(readOnly = true)
    // === QUERY | danh sách project ===
    // Chức năng: lọc theo role + phân trang → list.html.
    public ProjectListPage listPage(long actorUserId, Pageable requestedPage) {
        var actor = activeActor(actorUserId);
        Pageable bounded = boundedPage(requestedPage);
        // Admin=all | Mentor=project của mình | Intern=đang/đã tham gia
        Slice<ProjectEntity> visiblePage = visibleProjectSlice(actor, actorUserId, bounded);
        List<ProjectSummary> summaries = visiblePage.getContent().stream()
                .map(ProjectQueryService::summary)
                .toList();
        // → list.html: projectPage (số trang, hasNext/hasPrevious)
        return new ProjectListPage(
                summaries,
                visiblePage.getNumber() + 1,
                visiblePage.getSize(),
                visiblePage.hasPrevious(),
                visiblePage.hasNext());
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
    // === QUERY | chi tiết project ===
    // Chức năng: header + canManage + isCurrentLeader → detail.html.
    public ProjectDetail detail(long actorUserId, long projectId) {
        var actor = activeActor(actorUserId);
        var project = visibleProject(actorUserId, projectId); //service: kiểm tra quyền xem project này
        var completed = project.status() == ProjectStatus.COMPLETED;
        Long currentLeaderId = completed ? null : project.currentLeader().internUserId();
        // → detail.html: tên, status, Mentor, Leader, canManage, isCurrentLeader
        return new ProjectDetail(
                project.id(),
                project.name(),
                project.description(),
                project.status().name(),
                project.startDate(),
                project.endDate(),
                displayName(project.mentorUserId()),
                currentLeaderId == null ? null : displayName(currentLeaderId),
                !completed && project.mentorUserId() == actorUserId,
                !completed && actor.role() == GlobalRole.INTERN && currentLeaderId == actorUserId);
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
    // === QUERY | bảng thành viên ===
    // Chức năng: membership history + ai là Leader → members.html.
    public List<ProjectMemberView> members(long actorUserId, long projectId) {
        var project = visibleProject(actorUserId, projectId);
        Long leaderUserId = project.status() == ProjectStatus.COMPLETED
                ? null
                : project.currentLeader().internUserId();
        // → members.html: mỗi dòng = 1 membership (join/leave, ai là Leader)
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
        // Dùng cho module Task (không phải màn Project)
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
    // === QUERY | inbox lời mời ===
    // Chức năng: lời mời PENDING gửi cho Intern → invitations.html.
    public List<PendingProjectInvitationView> pendingInvitations(long actorUserId) {
        var actor = activeActor(actorUserId);
        if (!"INTERN".equals(actor.role().name())) {
            throw new ProjectAccessDeniedException();
        }
        // → invitations.html: lời mời PENDING gửi cho Intern này
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
    // === QUERY | lịch sử kỳ Leader ===
    // Chức năng: các kỳ Leader (mới nhất trước) → leadership.html.
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
    // === QUERY | lịch sử project (read-only) ===
    // Chức năng: membership, invitation, exit, task → history.html + workflows.html.
    public ProjectHistoryView history(long actorUserId, long projectId) {
        var project = visibleProject(actorUserId, projectId); // → Repo: ProjectRepository
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
        var invitationEntities = invitations.findByProject_IdOrderByCreatedAtAscIdAsc(projectId);
        var invitationHistory = invitationEntities.stream()
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
        var exitEntities = exitRequests.findByProject_IdOrderByCreatedAtAscIdAsc(projectId);
        var exitHistory = exitEntities.stream()
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
        var taskHistory = taskQueries.history(projectId);
        Map<Long, String> usernamesByUserId = new LinkedHashMap<>();
        Map<Long, String> usernamesByMembershipId = new LinkedHashMap<>();
        Map<Long, String> usernamesByLeadershipTermId = new LinkedHashMap<>();
        rememberUsername(usernamesByUserId, project.mentorUserId());
        project.memberships().forEach(membership -> {
            rememberUsername(usernamesByUserId, membership.internUserId());
            rememberUsername(usernamesByUserId, membership.addedByUserId());
            rememberUsername(usernamesByUserId, membership.removedByMentorUserId());
            rememberMembershipUsername(
                    project, membership.id(), usernamesByUserId, usernamesByMembershipId);
        });
        project.leadershipTerms().forEach(term -> {
            rememberUsername(usernamesByUserId, term.internUserId());
            rememberUsername(usernamesByUserId, term.appointedByMentorUserId());
            rememberUsername(usernamesByUserId, term.endedByMentorUserId());
            rememberLeadershipTermUsername(
                    term, usernamesByUserId, usernamesByLeadershipTermId);
        });
        invitationEntities.forEach(invitation -> {
            rememberUsername(usernamesByUserId, invitation.invitedInternUserId());
            rememberUsername(
                    usernamesByUserId, invitation.issuingLeadershipTerm().internUserId());
            rememberUsername(usernamesByUserId, invitation.resolvedByUserId());
            rememberLeadershipTermUsername(
                    invitation.issuingLeadershipTerm(),
                    usernamesByUserId,
                    usernamesByLeadershipTermId);
        });
        exitEntities.forEach(request -> {
            rememberMembershipUsername(
                    project, request.targetMembershipId(), usernamesByUserId, usernamesByMembershipId);
            rememberMembershipUsername(
                    project, request.requesterMembershipId(), usernamesByUserId, usernamesByMembershipId);
            rememberUsername(usernamesByUserId, request.resolvedByUserId());
        });
        taskHistory.forEach(task -> {
            rememberMembershipUsername(
                    project, task.assigneeMembershipId(), usernamesByUserId, usernamesByMembershipId);
            rememberMembershipUsername(
                    project, task.creatorMembershipId(), usernamesByUserId, usernamesByMembershipId);
            rememberMembershipUsername(
                    project, task.assignerMembershipId(), usernamesByUserId, usernamesByMembershipId);
            rememberMembershipUsername(
                    project, task.deletedByMembershipId(), usernamesByUserId, usernamesByMembershipId);
            task.comments().forEach(comment ->
                    rememberUsername(usernamesByUserId, comment.authorUserId()));
            task.workLogs().forEach(workLog -> rememberMembershipUsername(
                    project, workLog.membershipId(), usernamesByUserId, usernamesByMembershipId));
        });
        return new ProjectHistoryView(
                project.id(),
                memberships,
                leadership,
                invitationHistory,
                exitHistory,
                taskHistory,
                usernamesByUserId,
                usernamesByMembershipId,
                usernamesByLeadershipTermId);
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
    // === QUERY | hàng chờ exit ===
    // Chức năng: yêu cầu rời PENDING + số task chưa DONE → detail.html badge + workflows.html.
    public List<ProjectExitReadinessView> exitReadiness(long actorUserId, long projectId) {
        var project = visibleProject(actorUserId, projectId);
        var currentLeaderId = project.status() == ProjectStatus.COMPLETED
                ? null : project.currentLeader().id();
        // → workflows.html: hàng chờ exit — ai chờ, còn bao nhiêu task chưa DONE
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
                            !leader && unfinished == 0); // Mentor có thể duyệt khi không còn task
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
    // Thông tin project cho module Task (ai là Leader, ai đang chờ rời…).
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
    // Số liệu tổng hợp cho dashboard theo role (số project đang chạy…).
    @Transactional(readOnly = true)
    public ProjectDashboardSummary dashboardSummary(long actorUserId) {
        var actor = activeActor(actorUserId);
        return switch (actor.role().name()) {
            case "ADMIN" -> new ProjectDashboardSummary(projects.countActiveProjects(), 0L);
            case "MENTOR" -> new ProjectDashboardSummary(
                    projects.countActiveProjectsByMentor(actorUserId),
                    countEligibleCurrentMembers(actorUserId));
            case "INTERN" -> new ProjectDashboardSummary(
                    internships.isEligibleIntern(actorUserId)
                            ? projects.countActiveProjectsByIntern(actorUserId)
                            : 0L,
                    0L);
            default -> throw new ProjectAccessDeniedException();
        };
    }

    /**
     * Previews the Project and Task readiness facts for an Admin-managed Intern terminal action.
     *
     * <p>This read snapshot drives explanatory UI only. {@link ProjectService} recomputes the same facts while
     * retaining Account/profile and Project locks before completing or withdrawing the Intern.</p>
     *
     * @param adminUserId active Admin requesting the preview
     * @param internUserId Intern account being inspected
     * @return current-Leader and unfinished-Task facts across current Project memberships
     * @throws ProjectAccessDeniedException when the actor or target shape is unavailable
     */
    @Transactional(readOnly = true)
    public InternshipLifecycleGuard internshipLifecycleGuard(long adminUserId, long internUserId) {
        if (activeActor(adminUserId).role() != GlobalRole.ADMIN) {
            throw new ProjectAccessDeniedException();
        }
        AccountIdentity intern;
        try {
            intern = accounts.requireIdentityById(internUserId);
        } catch (IllegalArgumentException failure) {
            throw new ProjectAccessDeniedException();
        }
        if (intern.role() != GlobalRole.INTERN) {
            throw new ProjectAccessDeniedException();
        }
        var currentMemberships = projects.findMembershipIntervalsByInternUserId(internUserId).stream()
                .filter(interval -> interval.leftAt() == null)
                .toList();
        boolean currentLeader = currentMemberships.stream().anyMatch(interval -> {
            var route = projects.findMutationRouteById(interval.projectId())
                    .orElseThrow(ProjectAccessDeniedException::new);
            return route.currentLeaderUserId() != null && route.currentLeaderUserId() == internUserId;
        });
        long unfinishedTaskCount = currentMemberships.stream()
                .mapToLong(interval -> taskTransfers.unfinishedCount(
                        interval.projectId(), interval.membershipId()))
                .sum();
        return new InternshipLifecycleGuard(currentLeader, unfinishedTaskCount);
    }

    // Tải Project và chặn người không thuộc phạm vi xem; lỗi giống nhau dù ID không tồn tại hay không có quyền.
    private ProjectEntity visibleProject(long actorUserId, long projectId) {
        var project = projects.findById(projectId).orElseThrow(ProjectAccessDeniedException::new);
        requireVisibleProject(actorUserId, project);
        return project;
    }

    // Admin xem mọi Project; Mentor chỉ xem Project sở hữu; Intern xem Project đang tham gia hoặc completed từng tham gia.
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

    private Slice<ProjectEntity> visibleProjectSlice(
            AccountIdentity actor, long actorUserId, Pageable pageable) {
        return switch (actor.role().name()) {
            case "ADMIN" -> projects.findAllByOrderByUpdatedAtDescIdDesc(pageable);
            case "MENTOR" -> projects.findByMentorUserIdOrderByUpdatedAtDescIdDesc(actorUserId, pageable);
            case "INTERN" -> projects.findVisibleToIntern(actorUserId, pageable);
            default -> throw new ProjectAccessDeniedException();
        };
    }

    /**
     * Counts eligible active Interns across the complete distinct current-member set for a
     * Mentor-owned active Project scope. The Project query supplies only scalar IDs; Account
     * eligibility remains the public Account-service decision boundary.
     *
     * @param mentorUserId owning Mentor account identifier
     * @return distinct eligible active-member total
     */
    private long countEligibleCurrentMembers(long mentorUserId) {
        return projects.findDistinctCurrentMemberUserIdsByMentor(mentorUserId).stream()
                .filter(internships::isEligibleIntern)
                .count();
    }

    private static Pageable defaultProjectPage() {
        return PageRequest.of(0, PROJECT_LIST_PAGE_SIZE);
    }

    private static Pageable boundedPage(Pageable requestedPage) {
        if (requestedPage == null || requestedPage.isUnpaged()) {
            return defaultProjectPage();
        }
        return PageRequest.of(
                requestedPage.getPageNumber(),
                Math.min(requestedPage.getPageSize(), PROJECT_LIST_PAGE_SIZE));
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

    /** Adds one retained account's Project username and optional Intern Student Code to the presentation map. */
    private void rememberUsername(Map<Long, String> usernamesByUserId, long userId) {
        if (userId <= 0 || usernamesByUserId.containsKey(userId)) {
            return;
        }
        try {
            String username = accounts.requireIdentityById(userId).displayName();
            var studentCode = internships.studentCodeByUserId(userId);
            if (studentCode != null && studentCode.isPresent() && !studentCode.get().isBlank()) {
                username += " (" + studentCode.get() + ")";
            }
            usernamesByUserId.put(userId, username);
        } catch (IllegalArgumentException ignored) {
        }
    }

    /** Null-safe overload for optional retained actor attribution. */
    private void rememberUsername(Map<Long, String> usernamesByUserId, Long userId) {
        if (userId != null) {
            rememberUsername(usernamesByUserId, userId.longValue());
        }
    }

    /** Resolves a retained membership identifier to the participating Intern username. */
    private void rememberMembershipUsername(
            ProjectEntity project,
            Long membershipId,
            Map<Long, String> usernamesByUserId,
            Map<Long, String> usernamesByMembershipId) {
        if (membershipId == null || membershipId <= 0 || usernamesByMembershipId.containsKey(membershipId)) {
            return;
        }
        try {
            var membership = project.membership(membershipId);
            rememberUsername(usernamesByUserId, membership.internUserId());
            String username = usernamesByUserId.get(membership.internUserId());
            if (username != null) {
                usernamesByMembershipId.put(membershipId, username);
            }
        } catch (RuntimeException ignored) {
        }
    }

    /** Resolves a retained leadership term identifier to its Leader username. */
    private void rememberLeadershipTermUsername(
            ProjectLeadershipTermEntity term,
            Map<Long, String> usernamesByUserId,
            Map<Long, String> usernamesByLeadershipTermId) {
        if (term == null || term.id() == null || term.id() <= 0) {
            return;
        }
        rememberUsername(usernamesByUserId, term.internUserId());
        String username = usernamesByUserId.get(term.internUserId());
        if (username != null) {
            usernamesByLeadershipTermId.put(term.id(), username);
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
        return internships.isEligibleIntern(userId);
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
