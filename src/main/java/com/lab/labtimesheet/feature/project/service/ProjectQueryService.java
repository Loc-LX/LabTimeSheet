package com.lab.labtimesheet.feature.project.service;

import com.lab.labtimesheet.feature.account.model.dto.AccountIdentity;
import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.project.exception.ProjectAccessDeniedException;
import com.lab.labtimesheet.feature.project.model.ProjectStatus;
import com.lab.labtimesheet.feature.project.model.dto.ProjectDetail;
import com.lab.labtimesheet.feature.project.model.dto.ProjectActorView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectDashboardSummary;
import com.lab.labtimesheet.feature.project.model.dto.ProjectLeadershipTermView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectMemberView;
import com.lab.labtimesheet.feature.project.model.dto.ProjectSummary;
import com.lab.labtimesheet.feature.project.model.dto.ProjectTaskContext;
import com.lab.labtimesheet.feature.project.model.dto.ProjectTaskMemberView;
import com.lab.labtimesheet.feature.project.model.entity.ProjectEntity;
import com.lab.labtimesheet.feature.project.repository.ProjectRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cung cấp dữ liệu đọc Project chỉ gồm DTO và có kiểm tra phân quyền cho MVC cùng các feature khác.
 *
 * <p>Admin xem mọi Project, Mentor chỉ xem Project mình sở hữu, còn Intern chỉ xem Project đang
 * mở khi vẫn đang tham gia. Project đã hoàn tất vẫn hiển thị cho thành viên lịch sử nhưng không
 * cung cấp context Leader hiện tại hoặc thành viên đang hoạt động.
 */
@Service
@RequiredArgsConstructor
public class ProjectQueryService {

    private final ProjectRepository projects;
    private final AccountService accounts;

    /**
     * Tìm mã người dùng ổn định từ tài khoản đang hoạt động và đã xác thực.
     *
     * @param email địa chỉ email đã xác thực
     * @return mã người dùng đang hoạt động
     * @throws ProjectAccessDeniedException khi không có danh tính đang hoạt động
     */
    @Transactional(readOnly = true)
    public long authenticatedUserId(String email) {
        return authenticatedActor(email).userId();
    }

    /**
     * Tìm người thực hiện đang hoạt động và đã xác thực để điều hướng Project theo vai trò.
     *
     * @param email địa chỉ email đã xác thực
     * @return mã người dùng và vai trò toàn cục bất biến
     * @throws ProjectAccessDeniedException khi không có danh tính đang hoạt động
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
     * Liệt kê Project hiển thị theo vai trò hiện tại và quan hệ của người thực hiện với Project.
     * Lượt tham gia lịch sử của Intern chỉ cấp quyền xem Project đã hoàn tất.
     *
     * @param actorUserId mã người dùng đang hoạt động
     * @return các thông tin tóm tắt đã phân quyền, được sắp xếp
     */
    @Transactional(readOnly = true)
    public List<ProjectSummary> listVisible(long actorUserId) {
        var actor = activeActor(actorUserId);
        return visibleProjects(actor, actorUserId).stream().map(ProjectQueryService::summary).toList();
    }

    /**
     * Trả về chi tiết một Project đã phân quyền. Project đã hoàn tất không có Leader hiện tại và
     * không bao giờ cấp khả năng thay đổi, kể cả cho Mentor sở hữu.
     *
     * @param actorUserId mã người dùng đang hoạt động
     * @param projectId mã Project được yêu cầu
     * @return chi tiết đã phân quyền
     * @throws ProjectAccessDeniedException cho cả mã không tồn tại và mã không được phép
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
     * Trả về lịch sử khoảng thời gian thành viên của Project đã phân quyền. Lịch sử Project đã
     * hoàn tất không đánh dấu thành viên nào là Leader hiện tại vì việc hoàn tất đã đóng nhiệm kỳ cuối.
     *
     * @param actorUserId mã người dùng đang hoạt động
     * @param projectId mã Project được yêu cầu
     * @return lịch sử thành viên theo thứ tự trong aggregate
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
                                && membership.internUserId() == leaderUserId))
                .toList();
    }

    /**
     * Trả về các nhiệm kỳ Leader được lưu lại của Project đã phân quyền, nhiệm kỳ mới nhất trước.
     *
     * @param actorUserId mã người dùng đang hoạt động
     * @param projectId mã Project được yêu cầu
     * @return lịch sử nhiệm kỳ Leader, bao gồm cả nhiệm kỳ đã đóng
     */
    @Transactional(readOnly = true)
    public List<ProjectLeadershipTermView> leadership(long actorUserId, long projectId) {
        return visibleProject(actorUserId, projectId).leadershipTerms().stream()
                .sorted((left, right) -> right.startedAt().compareTo(left.startedAt()))
                .map(term -> new ProjectLeadershipTermView(
                        term.id(),
                        displayName(term.internUserId()),
                        term.startedAt(),
                        term.endedAt()))
                .toList();
    }

    /**
     * Trả về thông tin Project chỉ gồm DTO mà Task cần khi đọc. Project đang mở gồm lượt tham gia
     * Leader hiện tại và các thành viên hiện tại đủ điều kiện; Project đã hoàn tất trả Leader null
     * cùng danh sách thành viên đang hoạt động rỗng nhưng vẫn hiển thị cho thành viên cũ.
     *
     * @param actorUserId mã người dùng đang hoạt động
     * @param projectId mã Project được yêu cầu
     * @return context Task đã phân quyền
     */
    @Transactional(readOnly = true)
    public ProjectTaskContext taskContext(long actorUserId, long projectId) {
        var project = projects.findById(projectId).orElseThrow(ProjectAccessDeniedException::new);
        return taskContext(actorUserId, project);
    }

    /** Tạo context Task từ aggregate đã được khóa bởi service thay đổi. */
    ProjectTaskContext taskContext(long actorUserId, ProjectEntity project) {
        requireVisibleProject(actorUserId, project);
        if (project.status() == ProjectStatus.COMPLETED) {
            return new ProjectTaskContext(
                    project.id(),
                    project.mentorUserId(),
                    project.status().name(),
                    project.startDate(),
                    project.endDate(),
                    null,
                    List.of());
        }
        var activeMembers = project.memberships().stream()
                .filter(membership -> membership.isCurrent() && isEligibleIntern(membership.internUserId()))
                .map(membership -> new ProjectTaskMemberView(
                        membership.id(),
                        membership.internUserId(),
                        displayName(membership.internUserId())))
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
                activeMembers);
    }

    /**
     * Tính số liệu dashboard theo vai trò từ các quan hệ Project hiện tại và đang hoạt động.
     * Lượt tham gia lịch sử không bao giờ góp vào số liệu hiện tại của Intern hoặc Mentor.
     *
     * @param actorUserId mã người dùng đang hoạt động
     * @return số Project đang hoạt động và, với Mentor, số thành viên đang hoạt động đủ điều kiện không trùng
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

    /** Tải Project và chỉ trả về sau khi kiểm tra người thực hiện được phép xem. */
    private ProjectEntity visibleProject(long actorUserId, long projectId) {
        var project = projects.findById(projectId).orElseThrow(ProjectAccessDeniedException::new);
        requireVisibleProject(actorUserId, project);
        return project;
    }

    /** Kiểm tra quyền xem Project theo vai trò và quan hệ thành viên của người thực hiện. */
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

    /** Chọn truy vấn danh sách Project tương ứng với vai trò toàn cục của người thực hiện. */
    private List<ProjectEntity> visibleProjects(AccountIdentity actor, long actorUserId) {
        return switch (actor.role().name()) {
            case "ADMIN" -> projects.findAllByOrderByUpdatedAtDescIdDesc();
            case "MENTOR" -> projects.findByMentorUserIdOrderByUpdatedAtDescIdDesc(actorUserId);
            case "INTERN" -> projects.findVisibleToIntern(actorUserId);
            default -> throw new ProjectAccessDeniedException();
        };
    }

    /** Lấy danh tính Account đang hoạt động hoặc chuyển mọi lỗi nhận diện thành lỗi truy cập chung. */
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

    /** Lấy tên hiển thị của Account để đưa vào DTO; không để lộ lỗi tra cứu cho bên ngoài. */
    private String displayName(long userId) {
        try {
            return accounts.requireIdentityById(userId).displayName();
        } catch (IllegalArgumentException exception) {
            throw new ProjectAccessDeniedException();
        }
    }

    /** Kiểm tra nhanh Intern còn đủ điều kiện để xuất hiện trong context Task hay không. */
    private boolean isEligibleIntern(long userId) {
        return accounts.isEligibleIntern(userId);
    }

    /** Chuyển aggregate Project thành dòng tóm tắt chỉ đọc cho trang danh sách. */
    private static ProjectSummary summary(ProjectEntity project) {
        return new ProjectSummary(
                project.id(),
                project.name(),
                project.status().name(),
                project.startDate(),
                project.endDate());
    }
}
