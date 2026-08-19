package com.lab.labtimesheet.feature.project.service;

import com.lab.labtimesheet.feature.project.exception.ProjectAccessDeniedException;
import com.lab.labtimesheet.feature.project.exception.ProjectRuleViolationException;
import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.project.model.ProjectInternEligibility;
import com.lab.labtimesheet.feature.project.model.dto.ProjectCreateCommand;
import com.lab.labtimesheet.feature.project.model.dto.ProjectTaskContext;
import com.lab.labtimesheet.feature.project.model.entity.ProjectEntity;
import com.lab.labtimesheet.feature.project.repository.ProjectRepository;
import com.lab.labtimesheet.feature.task.service.TaskQueryService;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Thực hiện các thay đổi trên aggregate Project trong transaction do Spring quản lý.
 *
 * <p>Aggregate đã tồn tại được khóa bi quan trước khi phân quyền và kiểm tra vòng đời tại thời
 * điểm thay đổi. Thông tin Account và Task được lấy qua service công khai của feature tương ứng;
 * Project không import repository hoặc entity của các feature đó.
 *
 * <p>Truy vết mã công việc: Iter 1 gồm {@code I1-PRJ-01} đến {@code I1-PRJ-04}; trong đó
 * {@code I1-PRJ-01} hiện gắn vào path tạo vì chưa có operation chỉnh sửa riêng. Iter 2 đã có
 * trong service này gồm {@code I2-PRJ-01}, {@code I2-PRJ-02} và {@code I2-PRJ-03}. Các mã
 * {@code I2-PRJ-04} và {@code I2-PRJ-05} là luồng chuyển Task khi xóa member/chốt Project chưa
 * có operation ở service này, nên không gắn nhầm vào luồng tạo, thêm thành viên, đổi Leader hoặc kích hoạt.
 */
@Service
@RequiredArgsConstructor
public class ProjectService {

    private final ProjectRepository projects;
    private final AccountService accounts;
    private final ProjectQueryService queries;
    private final TaskQueryService taskQueries;
    private final Clock clock;

    /**
     * [I1-PRJ-01] Nguyên tử tạo Project Planned thuộc Mentor, lượt tham gia ban đầu đủ điều kiện và nhiệm kỳ
     * Leader đầu tiên. {@code saveAndFlush} phát hiện lỗi bất biến cơ sở dữ liệu trước khi commit.
     *
     * @param actorUserId mã Mentor đang hoạt động và đã xác thực, là người tạo và sở hữu Project
     * @param command các giá trị tạo đã được kiểm tra
     * @return mã Project được sinh
     * @throws ProjectAccessDeniedException khi người gọi không phải Mentor đang hoạt động
     * @throws com.lab.labtimesheet.feature.project.exception.ProjectRuleViolationException khi ngày,
     *         tên hoặc điều kiện của Leader ban đầu vi phạm quy tắc aggregate
     */
    @Transactional
    public long create(long actorUserId, ProjectCreateCommand command) {
        requireActiveMentor(actorUserId);
        var project = ProjectEntity.plan(
                actorUserId,
                command.name(),
                command.description(),
                command.startDate(),
                command.endDate(),
                eligibleIntern(command.initialLeaderUserId()),
                clock.instant());
        return projects.saveAndFlush(project).id();
    }

    /**
     * Thêm một Intern đủ điều kiện làm thành viên hiện tại trong khi giữ khóa ghi của Project.
     * Một Intern có thể thuộc Project khác, nhưng lượt tham gia hiện tại bị trùng trong Project này
     * sẽ bị từ chối trước khi flush.
     *
     * @param actorUserId mã Mentor sở hữu đã xác thực
     * @param projectId mã Project cần cập nhật
     * @param internUserId mã Intern được chọn để thêm trực tiếp
     */
    @Transactional
    public void addMember(long actorUserId, long projectId, long internUserId) {
        addMembers(actorUserId, projectId, List.of(internUserId));
    }

    /**
     * [I1-PRJ-02] Thêm trọn bộ lựa chọn các Intern đủ điều kiện chưa là thành viên trong khi giữ một khóa ghi
     * của Project. Mọi mã đều được kiểm tra lại sau khi phân quyền chủ sở hữu và trước khi aggregate
     * thay đổi, vì vậy lựa chọn thiếu, trùng, cũ, không đủ điều kiện hoặc đã là thành viên sẽ không
     * làm thay đổi dữ liệu thành viên.
     *
     * @param actorUserId mã Mentor sở hữu đã xác thực
     * @param projectId mã Project cần cập nhật
     * @param internUserIds các mã tài khoản Intern đủ điều kiện và không trùng
     * @throws com.lab.labtimesheet.feature.project.exception.ProjectRuleViolationException khi lựa
     *         chọn null, rỗng, sai định dạng, trùng, cũ, không đủ điều kiện hoặc đã có thành viên hiện tại
     */
    @Transactional
    public void addMembers(long actorUserId, long projectId, List<Long> internUserIds) {
        var project = lockedProject(projectId);
        project.authorizeOwner(actorUserId);
        if (internUserIds == null || internUserIds.isEmpty()) {
            throw new ProjectRuleViolationException("Select at least one Intern");
        }
        if (internUserIds.stream().anyMatch(userId -> userId == null || userId <= 0)
                || new HashSet<>(internUserIds).size() != internUserIds.size()) {
            throw new ProjectRuleViolationException("Intern selection is invalid");
        }

        var selectedInterns = internUserIds.stream().map(this::eligibleIntern).toList();
        if (selectedInterns.stream().anyMatch(intern -> !intern.isEligible())
                || selectedInterns.stream().anyMatch(intern -> project.hasCurrentMember(intern.userId()))) {
            throw new ProjectRuleViolationException("One or more selected Interns are no longer eligible");
        }

        var addedAt = clock.instant();
        selectedInterns.forEach(intern -> project.addMember(actorUserId, intern, addedAt));
        projects.flush();
    }

    /**
     * [I1-PRJ-03, I2-PRJ-01, I2-PRJ-02] Thay Leader hiện tại chỉ khi nhiệm kỳ được gửi lên vẫn là nhiệm kỳ hiện tại.
     *
     * <p>Khóa ghi của Project tuần tự hóa các lần bàn giao cạnh tranh. Kiểm tra token nhiệm kỳ sẽ
     * từ chối form được gửi sau khi một lần bàn giao khác đã commit; việc đóng/mở hai khoảng thời
     * gian vẫn nằm trong một transaction nguyên tử. Phân công Task, người tạo và người thực hiện
     * phân công được giữ nguyên; Leader cũ chỉ mất quyền quản lý Task khi nhiệm kỳ kết thúc.
     *
     * @param actorUserId mã Mentor sở hữu đã xác thực
     * @param projectId mã Project cần thay Leader
     * @param expectedLeadershipTermId mã nhiệm kỳ hiện tại, không null, được hiển thị cùng biểu mẫu
     * @param internUserId mã Intern thay thế đủ điều kiện trong cùng Project
     */
    @Transactional
    public void changeLeader(
            long actorUserId, long projectId, Long expectedLeadershipTermId, long internUserId) {
        if (expectedLeadershipTermId == null || expectedLeadershipTermId <= 0) {
            throw new ProjectRuleViolationException("Leadership term is invalid; refresh the Project and try again");
        }
        var project = lockedProject(projectId);
        project.authorizeOwner(actorUserId);
        var change = project.prepareLeaderChange(
                actorUserId,
                expectedLeadershipTermId,
                eligibleIntern(internUserId),
                clock.instant());

        // PostgreSQL kiểm tra ngay việc chồng lấn nhiệm kỳ. Flush thời điểm kết thúc nhiệm kỳ cũ
        // trước khi thêm nhiệm kỳ thay thế; I2-PRJ-02: không có thao tác cập nhật bảng tasks.
        // Toàn bộ bàn giao vẫn nguyên tử trong transaction.
        projects.flush();
        project.completeLeaderChange(actorUserId, change);
        projects.flush();
    }

    /**
     * [I2-PRJ-03] Thay Leader và đóng membership Leader cũ chỉ sau khi replacement đã được kiểm
     * tra. Transaction giữ khóa Project, flush term cũ trước khi mở term mới để PostgreSQL kiểm tra
     * khoảng thời gian không chồng lấn; membership cũ chỉ được đóng ở bước cuối.
     *
     * <p>Luồng chuyển Task chưa hoàn tất thuộc {@code I2-PRJ-04}; operation này không tự ý sửa các
     * cột assignee/creator/assigner của Task.
     *
     * @param actorUserId mã Mentor sở hữu đã xác thực
     * @param projectId mã Project cần thay Leader và đóng membership cũ
     * @param expectedLeadershipTermId mã nhiệm kỳ hiện tại từ form, dùng chống form cũ
     * @param replacementUserId mã Intern đang là member hiện tại được chọn làm replacement
     */
    @Transactional
    public void removeLeader(
            long actorUserId, long projectId, Long expectedLeadershipTermId, long replacementUserId) {
        if (expectedLeadershipTermId == null || expectedLeadershipTermId <= 0) {
            throw new ProjectRuleViolationException("Leadership term is invalid; refresh the Project and try again");
        }
        var project = lockedProject(projectId);
        var removal = project.prepareLeaderRemoval(
                actorUserId,
                expectedLeadershipTermId,
                eligibleIntern(replacementUserId),
                clock.instant());

        // I2-PRJ-03: replacement được chuẩn bị trước, term cũ được flush trước khi mở term mới.
        projects.flush();
        project.completeLeaderRemoval(actorUserId, removal);
        projects.flush();
    }

    /**
     * Khóa Project và đánh giá lại quyền xem, vòng đời, Leader hiện tại cùng các lượt tham gia đủ
     * điều kiện cho thao tác Task. Khi được gọi trong transaction của {@code TaskService}, khóa bi quan
     * vẫn được giữ đến commit hoặc rollback của transaction bên ngoài.
     *
     * @param actorUserId mã người thực hiện Task đã xác thực
     * @param projectId mã Project sở hữu
     * @return context thay đổi đã khóa, chỉ gồm DTO
     * @throws ProjectAccessDeniedException khi Project không tồn tại hoặc người gọi không được phép
     */
    @Transactional
    public ProjectTaskContext taskMutationContext(long actorUserId, long projectId) {
        return queries.taskContext(actorUserId, lockedProject(projectId));
    }

    /**
     * [I1-PRJ-04] Kích hoạt Project Planned trong khi giữ khóa ghi. Điều kiện Account hiện tại và tính hợp lệ
     * của người được giao Task được kiểm tra trong cùng transaction; lỗi ở bất kỳ bước nào giữ
     * Project ở Planned và bảo toàn Task cùng lịch sử khoảng thời gian.
     *
     * @param actorUserId mã Mentor sở hữu đã xác thực
     * @param projectId mã Project Planned cần kích hoạt
     */
    @Transactional
    public void activate(long actorUserId, long projectId) {
        var project = lockedProject(projectId);
        project.authorizeOwner(actorUserId);
        var activeMemberships = project.memberships().stream()
                .filter(membership -> membership.isCurrent()
                        && accounts.isEligibleIntern(membership.internUserId()))
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

    /** Tải Project bằng khóa ghi; mã không tồn tại được trả về cùng lỗi với mã không được phép. */
    private ProjectEntity lockedProject(long projectId) {
        return projects.findLockedById(projectId).orElseThrow(ProjectAccessDeniedException::new);
    }

    /** Lấy thông tin đủ điều kiện hiện tại của Intern từ Account service tại ngày của server. */
    private ProjectInternEligibility eligibleIntern(long userId) {
        return new ProjectInternEligibility(userId, accounts.isEligibleIntern(userId, LocalDate.now(clock)));
    }

    /** Bảo đảm mã người gọi thuộc Mentor đang hoạt động trước khi tạo Project. */
    private void requireActiveMentor(long userId) {
        try {
            var identity = accounts.requireIdentityById(userId);
            if (!"MENTOR".equals(identity.role().name()) || !"ACTIVE".equals(identity.status().name())) {
                throw new ProjectAccessDeniedException();
            }
        } catch (IllegalArgumentException exception) {
            throw new ProjectAccessDeniedException();
        }
    }
}
