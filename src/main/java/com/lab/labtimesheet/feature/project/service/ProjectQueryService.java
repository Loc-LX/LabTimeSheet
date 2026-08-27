package com.lab.labtimesheet.feature.project.service;

import com.lab.labtimesheet.feature.account.model.GlobalRole;
import com.lab.labtimesheet.feature.account.model.dto.AccountIdentity;
import com.lab.labtimesheet.feature.account.model.dto.InternshipLifecycleGuard;
import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.project.exception.ProjectAccessDeniedException;
import com.lab.labtimesheet.feature.project.exception.ProjectRuleViolationException;
import com.lab.labtimesheet.feature.project.model.ProjectStatus;
import com.lab.labtimesheet.feature.project.model.dto.ProjectDetail;
import com.lab.labtimesheet.feature.project.model.dto.ProjectActorView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectDashboardSummary;
import com.lab.labtimesheet.feature.project.model.dto.ProjectExitRequestHistoryView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectExitReadinessView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectHistoryView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectInvitationHistoryView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectLeadershipTermView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectListPage;
import com.lab.labtimesheet.feature.project.model.dto.ProjectMemberView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectMembershipIntervalView;
import com.lab.labtimesheet.feature.project.model.dto.PendingProjectInvitationView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectSummary;
import com.lab.labtimesheet.feature.project.model.dto.ProjectTaskContext;
import com.lab.labtimesheet.feature.project.model.dto.ProjectTaskMemberView;
import com.lab.labtimesheet.feature.project.model.entity.ProjectEntity;
import com.lab.labtimesheet.feature.project.model.entity.ProjectLeadershipTermEntity;
import com.lab.labtimesheet.feature.project.repository.ProjectExitRequestRepository;
import com.lab.labtimesheet.feature.project.repository.ProjectInvitationRepository;
import com.lab.labtimesheet.feature.project.repository.ProjectRepository;
import com.lab.labtimesheet.feature.task.service.TaskQueryService;
import com.lab.labtimesheet.feature.task.service.TaskTransferService;
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
// Service chỉ đọc dữ liệu Project cho Controller và các feature khác.
// Nó kiểm tra quyền theo role trước, sau đó gọi Repository lấy dữ liệu và đổi Entity thành DTO an toàn cho UI.
@Service
@RequiredArgsConstructor
public class ProjectQueryService {

    private static final int PROJECT_LIST_PAGE_SIZE = 50;

    // Spring inject các nguồn đọc Project, Account và Task dùng để dựng dữ liệu cho màn hình.
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
    // [Lấy ID người đăng nhập]
    // Chỉ chuyển email thành user ID sau khi authenticatedActor xác nhận account còn active.
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
    // [Xác thực actor Project]
    // Luồng xử lý:
    // 1. Lấy identity theo email đăng nhập.
    // 2. Từ chối account không tồn tại hoặc không còn ACTIVE.
    // 3. Trả DTO nhỏ gồm ID và role cho controller dùng tiếp.
    @Transactional(readOnly = true)
    public ProjectActorView authenticatedActor(String email) {
        try {
            // Mọi màn Project đều bắt đầu bằng việc xác nhận account còn ACTIVE.
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
    // [Danh sách Project mặc định]
    // Dùng trang mặc định để các caller không phân trang vẫn không tải toàn bộ Project.
    @Transactional(readOnly = true)
    public List<ProjectSummary> listVisible(long actorUserId) {
        return listVisible(actorUserId, PageRequest.of(0, PROJECT_LIST_PAGE_SIZE));
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
    // [Danh sách Project có phân trang]
    // Đây là lớp bọc tiện dụng; logic lọc theo role và phân trang nằm ở listPage.
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
            // A broken open-Project invariant is still non-disclosing denial at a read boundary.
            throw new ProjectAccessDeniedException();
        }
        if (currentLeaderId != actorUserId) {
            throw new ProjectAccessDeniedException();
        }
        return summary(project);
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
    // [Tạo trang danh sách Project]
    // Luồng xử lý:
    // 1. Kiểm tra actor ACTIVE và giới hạn page size.
    // 2. Query repository theo role (Admin/Mentor/Intern) để chỉ nhận row hợp lệ.
    // 3. Chuyển entity thành DTO và giữ metadata có trang trước/sau cho giao diện.
    @Transactional(readOnly = true)
    public ProjectListPage listPage(long actorUserId, Pageable requestedPage) {
        // Giới hạn kích thước trang trước khi query để màn danh sách không tải toàn bộ Project.
        var actor = activeActor(actorUserId);
        Pageable bounded = boundedPage(requestedPage);
        // Từ đây Service chọn đúng repository query theo role. Database đã lọc ownership/membership trước khi
        // trả row; Controller không nhận danh sách toàn hệ thống để rồi cố lọc bằng template.
        Slice<ProjectEntity> visiblePage = visibleProjectSlice(actor, actorUserId, bounded);
        // Entity chỉ được dùng nội bộ trong read transaction. Map sang DTO bỏ persistence behavior khỏi Model và
        // chỉ expose những field mà projects/list.html cần render.
        List<ProjectSummary> summaries = visiblePage.getContent().stream()
                .map(ProjectQueryService::summary)
                .toList();
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
    // [Lấy chi tiết Project]
    // Luồng xử lý:
    // 1. visibleProject áp quyền xem và dùng cùng lỗi cho ID không tồn tại/lỗi quyền.
    // 2. Project COMPLETED vẫn xem được history nhưng không trả quyền quản lý hay Leader hiện tại.
    @Transactional(readOnly = true)
    public ProjectDetail detail(long actorUserId, long projectId) {
        // Đây là read sau redirect PRG (GET /projects/{projectId}). visibleProject vừa lấy Entity vừa authorize;
        // cùng một lỗi access được dùng cho ID không tồn tại và ID không thuộc actor.
        var actor = activeActor(actorUserId);
        var project = visibleProject(actorUserId, projectId);
        // Project đã hoàn thành chỉ để xem lịch sử: không còn Leader hiện tại hoặc quyền quản lý.
        var completed = project.status() == ProjectStatus.COMPLETED;
        Long currentLeaderId = completed ? null : project.currentLeader().internUserId();
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
    // [Lấy thành viên và membership history]
    // Luồng xử lý:
    // 1. Xác thực quyền xem Project.
    // 2. Chuyển mọi membership interval thành DTO, bao gồm member đã rời Project.
    // 3. Chỉ đánh dấu current Leader khi Project chưa completed.
    @Transactional(readOnly = true)
    public List<ProjectMemberView> members(long actorUserId, long projectId) {
        var project = visibleProject(actorUserId, projectId);
        // Khi Project hoàn thành, không đánh dấu ai là Leader hiện tại trong dữ liệu lịch sử.
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
    // [Lấy membership của Intern]
    // Intern dùng dữ liệu này để làm việc với Task giữa các Project; không có quyền mutation ở đây.
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
    // [Lấy inbox invitation của Intern]
    // Luồng xử lý:
    // 1. Chỉ cho account Intern ACTIVE đọc inbox.
    // 2. Chỉ query invitation PENDING có invitedInternUserId trùng actor.
    // 3. Bổ sung tên Project và Leader phát hành để UI có thể hiển thị.
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
                    // Invitee chưa là member nên chỉ được đọc đúng lời mời mang user ID của họ.
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
    // [Lấy lịch sử Leadership]
    // Lấy term mới nhất trước nhưng vẫn giữ các term cũ để Project History không mất dữ liệu.
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
    // [Tạo snapshot Project History]
    // Luồng xử lý:
    // 1. Xác thực quyền xem Project trước.
    // 2. Gom membership, leadership, invitation, exit request và Task history thành một snapshot.
    // 3. Tạo map username/student code cho mọi actor để UI không phải hiển thị ID nội bộ.
    @Transactional(readOnly = true)
    public ProjectHistoryView history(long actorUserId, long projectId) {
        // Xác nhận quyền xem trước khi gom các bản ghi đã lưu của Project và Task.
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
        // Project không đọc bảng Task trực tiếp; chỉ nhận DTO history từ Task service.
        var taskHistory = taskQueries.history(projectId);
        // Tạo các map hiển thị một lần để UI dùng username thay vì ID nội bộ.
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
            // Thu thập mọi actor xuất hiện trong Task, comment và work log để History không lộ raw ID.
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
    // [Kiểm tra điều kiện duyệt exit]
    // Luồng xử lý:
    // 1. Chỉ đọc exit request PENDING của Project đã được phép xem.
    // 2. Tính các điều kiện hiển thị: target có là Leader và còn Task chưa DONE không.
    // 3. Kết quả chỉ phục vụ UI; không phải quyết định cuối cùng để approve.
    @Transactional(readOnly = true)
    public List<ProjectExitReadinessView> exitReadiness(long actorUserId, long projectId) {
        var project = visibleProject(actorUserId, projectId);
        var currentLeaderId = project.status() == ProjectStatus.COMPLETED
                ? null : project.currentLeader().id();
        return exitRequests.findByProject_IdOrderByCreatedAtAscIdAsc(projectId).stream()
                .filter(request -> request.isPending())
                .map(request -> {
                    // Readiness chỉ phục vụ giao diện; approveExit sẽ tính lại dưới lock trước khi đổi dữ liệu.
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
    // [Tạo Task context từ Project]
    // Luồng xử lý:
    // 1. Tải Project và áp quyền xem đúng như các màn Project khác.
    // 2. Lấy membership đang có exit pending.
    // 3. Đóng gói thành DTO cho Task service, không truyền entity/repository ra ngoài feature.
    @Transactional(readOnly = true)
    public ProjectTaskContext taskContext(long actorUserId, long projectId) {
        // Task feature chỉ nhận context đã qua quyền Project, không tự chạm vào entity Project.
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
    // [Tạo Task context khi Project đã khóa]
    // Luồng xử lý khi Project đã được service mutation lock:
    // 1. Nếu Project COMPLETED, chỉ trả context lịch sử không có member/Leader hoạt động.
    // 2. Nếu còn mở, chỉ đưa member hiện tại và còn đủ điều kiện internship vào danh sách Task.
    // 3. Giữ riêng tập pending exit để Task chặn assignment mới nhưng vẫn đọc được dữ liệu cũ.
    ProjectTaskContext taskContext(
            long actorUserId, ProjectEntity project, Set<Long> pendingExitMembershipIds) {
        requireVisibleProject(actorUserId, project);
        if (project.status() == ProjectStatus.COMPLETED) {
            // Lịch sử Task vẫn xem được, nhưng không cung cấp member/Leader để tạo mutation mới.
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
                // Chỉ member còn trong Project và còn đủ điều kiện internship mới được thao tác Task.
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
    // [Thống kê Project dashboard]
    // Chọn query thống kê theo role để không dùng chung số liệu của các Project ngoài phạm vi người xem.
    @Transactional(readOnly = true)
    public ProjectDashboardSummary dashboardSummary(long actorUserId) {
        var actor = activeActor(actorUserId);
        // Mỗi role có một phạm vi thống kê riêng, tránh vô tình đếm Project ngoài quyền xem.
        return switch (actor.role().name()) {
            case "ADMIN" -> new ProjectDashboardSummary(projects.countActiveProjects(), 0L);
            case "MENTOR" -> new ProjectDashboardSummary(
                    projects.countActiveProjectsByMentor(actorUserId),
                    countEligibleCurrentMembers(actorUserId));
            case "INTERN" -> new ProjectDashboardSummary(
                    accounts.isEligibleIntern(actorUserId)
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
    // [Kiểm tra guard complete/withdraw Intern]
    // Luồng xử lý:
    // 1. Chỉ Admin được xem guard này.
    // 2. Duyệt các membership còn mở của Intern để kiểm tra họ có đang là Leader không.
    // 3. Cộng Task chưa hoàn tất để UI giải thích vì sao chưa complete/withdraw được.
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
            // Kiểm tra từng Project hiện tại vì một Intern không thể complete/withdraw khi còn là Leader.
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

    // Tải một Project rồi bắt buộc đi qua cùng hàng rào phân quyền trước khi trả cho bất kỳ query nào.
    // findById() tạo SELECT theo khóa chính; nếu Optional rỗng hoặc requireVisibleProject thất bại, Service ném
    // ProjectAccessDeniedException để ControllerAdvice trả 404 generic thay vì tiết lộ Project có tồn tại hay không.
    private ProjectEntity visibleProject(long actorUserId, long projectId) {
        var project = projects.findById(projectId).orElseThrow(ProjectAccessDeniedException::new);
        requireVisibleProject(actorUserId, project);
        return project;
    }

    // [Kiểm tra quyền xem Project]
    // Luồng kiểm tra quyền xem:
    // 1. Admin xem mọi Project.
    // 2. Mentor chỉ xem Project do mình sở hữu.
    // 3. Intern xem Project đang tham gia, hoặc Project đã completed mà họ từng tham gia.
    private void requireVisibleProject(long actorUserId, ProjectEntity project) {
        var actor = activeActor(actorUserId);
        // Admin xem mọi Project; Mentor xem Project sở hữu; Intern chỉ xem Project đang tham gia
        // hoặc Project đã hoàn thành mà họ từng tham gia.
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

    // [Chọn query Project theo role]
    // Chọn repository query theo role để lọc dữ liệu ngay từ database thay vì lọc sau khi tải hết.
    private Slice<ProjectEntity> visibleProjectSlice(
            AccountIdentity actor, long actorUserId, Pageable pageable) {
        // Switch này là điểm nối giữa role đã resolve và query database: Admin lấy all, Mentor lọc mentor_user_id,
        // Intern đi qua JPQL membership/status. Mọi nhánh đều trả Slice bounded, sau đó listPage map sang DTO.
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
                .filter(accounts::isEligibleIntern)
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
            // Dùng cùng hàng rào ACTIVE cho tất cả query để account đã khóa không đọc được Project.
            // AccountService trả identity DTO; QueryService không query AppUserRepository trực tiếp và không đẩy
            // entity Account vào view. Nếu account bị disable sau lúc login, request hiện tại vẫn bị chặn tại đây.
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
    // [Chuẩn bị username cho History]
    // Bổ sung một username vào map History một lần duy nhất; Intern có thêm Student Code nếu tồn tại.
    private void rememberUsername(Map<Long, String> usernamesByUserId, long userId) {
        if (userId <= 0 || usernamesByUserId.containsKey(userId)) {
            return;
        }
        try {
            // Student code chỉ có với Intern; các role khác chỉ hiển thị username.
            String username = accounts.requireIdentityById(userId).displayName();
            var studentCode = accounts.studentCodeByUserId(userId);
            if (studentCode != null && studentCode.isPresent() && !studentCode.get().isBlank()) {
                username += " (" + studentCode.get() + ")";
            }
            usernamesByUserId.put(userId, username);
        } catch (IllegalArgumentException ignored) {
            // Retained history must remain renderable even when an old actor is unavailable.
        }
    }

    /** Null-safe overload for optional retained actor attribution. */
    private void rememberUsername(Map<Long, String> usernamesByUserId, Long userId) {
        if (userId != null) {
            rememberUsername(usernamesByUserId, userId.longValue());
        }
    }

    /** Resolves a retained membership identifier to the participating Intern username. */
    // [Đổi membership ID thành username]
    // Đổi membership ID lịch sử thành username của Intern sở hữu membership đó cho template sử dụng.
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
            // A malformed retained reference should never make the authorized history page fail.
        }
    }

    /** Resolves a retained leadership term identifier to its Leader username. */
    // [Đổi leadership term ID thành username]
    // Đổi leadership term ID lịch sử thành username của Leader trong term đó.
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
        return accounts.isEligibleIntern(userId);
    }

    private static ProjectSummary summary(ProjectEntity project) {
        // DTO mapping là bước cuối trước Model/Thymeleaf: entity state được chuyển thành dữ liệu bất biến để template
        // không thể gọi domain mutation hoặc vô tình trigger lazy loading ngoài read boundary.
        return new ProjectSummary(
                project.id(),
                project.name(),
                project.status().name(),
                project.startDate(),
                project.endDate());
    }
}
