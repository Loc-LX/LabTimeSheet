package com.lab.labtimesheet.feature.project.model.entity;

import com.lab.labtimesheet.feature.project.exception.ProjectAccessDeniedException;
import com.lab.labtimesheet.feature.project.exception.ProjectRuleViolationException;
import com.lab.labtimesheet.feature.project.model.ProjectInternEligibility;
import com.lab.labtimesheet.feature.project.model.ProjectLeaderChange;
import com.lab.labtimesheet.feature.project.model.ProjectStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Aggregate root JPA quản lý vòng đời Project, các khoảng thời gian thành viên và nhiệm kỳ Leader.
 *
 * <p>Project ở trạng thái Planned hoặc Active luôn có đúng một lượt tham gia Leader hiện tại.
 * Trạng thái Completed là kết thúc; các khoảng thời gian hiện tại được đóng và lịch sử được giữ
 * lại thay vì gán lại hoặc xóa. Các phương thức thay đổi tự kiểm tra quy tắc aggregate, độc lập với
 * việc nút điều khiển có được hiển thị trên trình duyệt hay không.
 *
 * <p>Truy vết mã công việc: {@code I1-PRJ-01} tạo Project, {@code I1-PRJ-02} quản lý membership,
 * {@code I1-PRJ-03} quản lý Leader, {@code I1-PRJ-04} kích hoạt Project, và
 * {@code I2-PRJ-01}–{@code I2-PRJ-02} bảo toàn lịch sử/bàn giao Leader.
 */
@Entity
@Table(name = "projects")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "mentor_user_id", nullable = false)
    private long mentorUserId;

    @Column(nullable = false, length = 160)
    private String name;

    @Column
    private String description;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL)
    private List<ProjectMembershipEntity> memberships = new ArrayList<>();

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL)
    private List<ProjectLeadershipTermEntity> leadershipTerms = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ProjectStatus status = ProjectStatus.PLANNED;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    /** Khởi tạo aggregate với các thông tin bất biến ban đầu của Project. */
    private ProjectEntity(
            long mentorUserId,
            String name,
            String description,
            LocalDate startDate,
            LocalDate endDate,
            Instant createdAt) {
        this.mentorUserId = mentorUserId;
        this.name = name;
        this.description = description;
        this.startDate = startDate;
        this.endDate = endDate;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    /**
     * [I1-PRJ-01, I1-PRJ-03] Tạo Project ở trạng thái Planned với một lượt tham gia Leader ban đầu đủ điều kiện và nhiệm
     * kỳ Leader đầu tiên. Aggregate trả về không bao giờ rỗng hoặc không có Leader.
     *
     * @param mentorUserId Mentor đang hoạt động sở hữu Project
     * @param name tên Project bắt buộc
     * @param description mô tả tùy chọn
     * @param startDate ngày bắt đầu, được tính cả ngày này
     * @param endDate ngày kết thúc, không được trước {@code startDate}
     * @param initialLeader Intern đủ điều kiện được bổ nhiệm làm Leader đầu tiên
     * @param at thời điểm thay đổi do server cấp, dùng cho mọi bản ghi ban đầu
     * @return aggregate Planned mới, chưa lưu
     */
    public static ProjectEntity plan(
            long mentorUserId,
            String name,
            String description,
            LocalDate startDate,
            LocalDate endDate,
            ProjectInternEligibility initialLeader,
            Instant at) {
        if (mentorUserId <= 0) {
            throw new IllegalArgumentException("Mentor user ID must be positive");
        }
        var normalizedName = requireText(name, "Project name is required");
        Objects.requireNonNull(startDate, "startDate");
        Objects.requireNonNull(endDate, "endDate");
        Objects.requireNonNull(at, "at");
        if (endDate.isBefore(startDate)) {
            throw new ProjectRuleViolationException("Project end date must not precede its start date");
        }
        requireEligible(initialLeader);

        var project = new ProjectEntity(
                mentorUserId,
                normalizedName,
                normalizeOptionalText(description),
                startDate,
                endDate,
                at);
        var membership = project.addEligibleMember(initialLeader, mentorUserId, at);
        project.leadershipTerms.add(new ProjectLeadershipTermEntity(project, membership, at, mentorUserId));
        return project;
    }

    /**
     * [I1-PRJ-02] Thêm một thành viên hiện tại đủ điều kiện và không trùng vào Project còn thay đổi được sau
     * khi đã xác thực Mentor sở hữu.
     *
     * @param actorMentorUserId mã Mentor sở hữu đã xác thực
     * @param intern thông tin đủ điều kiện Account/thực tập hiện tại
     * @param at thời điểm tham gia do server cấp
     * @return khoảng thời gian tham gia vừa tạo
     */
    public ProjectMembershipEntity addMember(
            long actorMentorUserId, ProjectInternEligibility intern, Instant at) {
        requireOwner(actorMentorUserId);
        requireMutable();
        requireEligible(intern);
        Objects.requireNonNull(at, "at");
        if (hasCurrentMember(intern.userId())) {
            throw new ProjectRuleViolationException("Intern is already a current Project member");
        }
        return addEligibleMember(intern, actorMentorUserId, at);
    }

    /**
     * [I1-PRJ-03, I2-PRJ-01, I2-PRJ-02] Đóng nhiệm kỳ Leader hiện tại và chuẩn bị thành viên hiện tại đủ điều kiện làm
     * Leader thay thế chỉ khi bên gọi vẫn đang giữ đúng nhiệm kỳ đã đọc. Bên gọi phải flush khoảng
     * thời gian đã đóng trước khi mở nhiệm kỳ thay thế.
     *
     * <p>Token nhiệm kỳ là cơ chế chống ghi đè từ biểu mẫu Leader. Service khóa Project trước khi
     * gọi hàm này; nếu nhiệm kỳ đã đổi thì có nghĩa một lần bàn giao khác đã commit sau khi biểu
     * mẫu được hiển thị và yêu cầu hiện tại không được ghi đè lên kết quả đó. Việc đổi Leader chỉ
     * đóng/mở nhiệm kỳ; khoảng thời gian thành viên cũ và các khóa phân công Task không bị chạm tới.
     *
     * @param actorMentorUserId mã Mentor sở hữu đã xác thực
     * @param expectedCurrentLeadershipTermId mã nhiệm kỳ bên gọi đã đọc, hoặc null với aggregate
     *        tạm thời chưa được cơ sở dữ liệu cấp mã
     * @param intern Intern thay thế đủ điều kiện
     * @param at thời điểm hiệu lực do server quyết định
     * @return lượt tham gia thay thế và thời điểm hiệu lực liền kề của hai nhiệm kỳ
     * @throws ProjectRuleViolationException khi yêu cầu đã cũ hoặc vi phạm nghiệp vụ Project
     */
    public ProjectLeaderChange prepareLeaderChange(
            long actorMentorUserId,
            Long expectedCurrentLeadershipTermId,
            ProjectInternEligibility intern,
            Instant at) {
        requireOwner(actorMentorUserId);
        requireMutable();
        requireEligible(intern);
        Objects.requireNonNull(at, "at");
        var current = currentLeadershipTerm();
        // I2-PRJ-02: Leader cũ phải còn là thành viên hiện tại để vẫn giữ quyền assignee của Task.
        currentMembership(current.internUserId());
        var replacement = currentMembership(intern.userId());
        if (expectedCurrentLeadershipTermId != null
                && !Objects.equals(current.id(), expectedCurrentLeadershipTermId)) {
            throw new ProjectRuleViolationException("Leadership changed; refresh the Project and try again");
        }
        if (current.internUserId() == intern.userId()) {
            throw new ProjectRuleViolationException("Selected Intern is already the current Leader");
        }

        var effectiveAt = current.end(at, actorMentorUserId);
        updatedAt = effectiveAt;
        return new ProjectLeaderChange(replacement, effectiveAt);
    }

    /**
     * [I1-PRJ-03, I2-PRJ-01, I2-PRJ-02] Mở nhiệm kỳ Leader thay thế sau khi nhiệm kỳ hiện tại trước đó đã được đóng và
     * flush; không đóng membership cũ và không cập nhật các dòng Task.
     *
     * @param actorMentorUserId mã Mentor sở hữu đã xác thực
     * @param change thông tin Leader thay thế đã chuẩn bị trong transaction này
     */
    public void completeLeaderChange(long actorMentorUserId, ProjectLeaderChange change) {
        requireOwner(actorMentorUserId);
        Objects.requireNonNull(change, "change");
        if (!change.replacement().isCurrent()) {
            throw new ProjectRuleViolationException("Replacement Leader must be a current Project member");
        }
        if (leadershipTerms.stream().anyMatch(ProjectLeadershipTermEntity::isCurrent)) {
            throw new ProjectRuleViolationException("Current Leader must be closed before replacement");
        }
        leadershipTerms.add(new ProjectLeadershipTermEntity(
                this, change.replacement(), change.effectiveAt(), actorMentorUserId));
    }

    /**
     * [I1-PRJ-04] Chuyển Project Planned sang Active sau khi vượt qua các kiểm tra về thành viên hiện tại,
     * Leader và người được giao Task. Chuyển trạng thái chỉ đi một chiều và lưu thời điểm kích hoạt
     * do server cấp.
     *
     * @param actorMentorUserId mã Mentor sở hữu đã xác thực
     * @param activeInternUserIds mã người dùng của các thành viên hiện tại đủ điều kiện
     * @param allTaskAssigneesAreCurrent true khi mọi Task chưa xóa đều trỏ tới lượt tham gia hiện tại
     * @param at thời điểm kích hoạt do server cấp
     */
    public void activate(
            long actorMentorUserId,
            Set<Long> activeInternUserIds,
            boolean allTaskAssigneesAreCurrent,
            Instant at) {
        requireOwner(actorMentorUserId);
        Objects.requireNonNull(activeInternUserIds, "activeInternUserIds");
        Objects.requireNonNull(at, "at");
        if (status != ProjectStatus.PLANNED) {
            throw new ProjectRuleViolationException("Only a planned Project can be activated");
        }
        if (memberships.stream().noneMatch(membership -> membership.isCurrent()
                && activeInternUserIds.contains(membership.internUserId()))) {
            throw new ProjectRuleViolationException("Project requires an active member");
        }
        if (!activeInternUserIds.contains(currentLeader().internUserId())) {
            throw new ProjectRuleViolationException("Project Leader must be an active member");
        }
        if (!allTaskAssigneesAreCurrent) {
            throw new ProjectRuleViolationException("Every current Task assignee must be an active Project member");
        }
        status = ProjectStatus.ACTIVE;
        activatedAt = at;
        updatedAt = at;
    }

    /**
     * Trả về định danh lưu trữ.
     *
     * @return mã đã lưu, hoặc null trước khi insert
     */
    public Long id() {
        return id;
    }

    /**
     * Trả về thông tin sở hữu Project bất biến.
     *
     * @return mã người dùng Mentor sở hữu
     */
    public long mentorUserId() {
        return mentorUserId;
    }

    /**
     * Trả về tên hiển thị.
     *
     * @return tên Project đã chuẩn hóa
     */
    public String name() {
        return name;
    }

    /**
     * Trả về bản sao mô tả tùy chọn.
     *
     * @return mô tả tùy chọn đã chuẩn hóa, hoặc null khi không có
     */
    public String description() {
        return description;
    }

    /**
     * Trả về mốc ngày nghiệp vụ thấp hơn.
     *
     * @return ngày bắt đầu Project, được tính cả ngày này
     */
    public LocalDate startDate() {
        return startDate;
    }

    /**
     * Trả về mốc ngày nghiệp vụ cao hơn.
     *
     * @return ngày kết thúc Project, được tính cả ngày này
     */
    public LocalDate endDate() {
        return endDate;
    }

    /**
     * Trả về trạng thái vòng đời hiện tại.
     *
     * @return trạng thái vòng đời của aggregate
     */
    public ProjectStatus status() {
        return status;
    }

    /**
     * Trả về thời điểm bắt đầu thực thi.
     *
     * @return thời điểm kích hoạt do server cấp, hoặc null khi còn Planned
     */
    public Instant activatedAt() {
        return activatedAt;
    }

    /**
     * Trả về bản chụp an toàn của các khoảng thời gian thành viên hiện tại và lịch sử.
     *
     * @return bản chụp thành viên không thể sửa
     */
    public List<ProjectMembershipEntity> memberships() {
        return List.copyOf(memberships);
    }

    /**
     * Trả về bản chụp an toàn của các khoảng thời gian nhiệm kỳ Leader hiện tại và lịch sử.
     *
     * @return bản chụp nhiệm kỳ Leader không thể sửa
     */
    public List<ProjectLeadershipTermEntity> leadershipTerms() {
        return List.copyOf(leadershipTerms);
    }

    /**
     * Bắt buộc quyền của Mentor sở hữu mà không tiết lộ chi tiết Project cho người không sở hữu.
     *
     * @param actorMentorUserId mã Mentor đã xác thực
     * @throws ProjectAccessDeniedException khi người gọi không sở hữu Project này
     */
    public void authorizeOwner(long actorMentorUserId) {
        requireOwner(actorMentorUserId);
    }

    /**
     * Chỉ kiểm tra các khoảng thời gian thành viên đang mở.
     *
     * @param internUserId mã tài khoản Intern
     * @return true khi Intern hiện đang thuộc Project này
     */
    public boolean hasCurrentMember(long internUserId) {
        return memberships.stream()
                .anyMatch(membership -> membership.internUserId() == internUserId && membership.isCurrent());
    }

    /**
     * Kiểm tra cả khoảng thời gian hiện tại và đã đóng để phân quyền xem lịch sử Project hoàn tất.
     *
     * @param internUserId mã tài khoản Intern
     * @return true khi Intern từng thuộc Project này
     */
    public boolean hasEverHadMember(long internUserId) {
        return memberships.stream().anyMatch(membership -> membership.internUserId() == internUserId);
    }

    /**
     * Tìm lượt tham gia hiện tại được nhiệm kỳ Leader hiện tại duy nhất tham chiếu. Project đã
     * hoàn tất cố ý không có Leader hiện tại; không dùng phương thức này để hiển thị lịch sử đã hoàn tất.
     *
     * @return lượt tham gia của Leader hiện tại
     * @throws ProjectRuleViolationException khi bất biến Leader hiện tại của Project bị thiếu
     */
    public ProjectMembershipEntity currentLeader() {
        return currentMembership(currentLeadershipTerm().internUserId());
    }

    /** Thêm lượt tham gia mới từ thông tin Intern đã đủ điều kiện và cập nhật thời điểm Project. */
    private ProjectMembershipEntity addEligibleMember(
            ProjectInternEligibility intern, long addedByUserId, Instant at) {
        var membership = new ProjectMembershipEntity(this, intern.userId(), at, addedByUserId);
        memberships.add(membership);
        updatedAt = at;
        return membership;
    }

    /** Tìm lượt tham gia hiện tại của Intern hoặc báo lỗi nếu Leader không còn là thành viên. */
    private ProjectMembershipEntity currentMembership(long internUserId) {
        return memberships.stream()
                .filter(membership -> membership.internUserId() == internUserId && membership.isCurrent())
                .findFirst()
                .orElseThrow(() -> new ProjectRuleViolationException(
                        "Leader must be a current same-Project member"));
    }

    /** Bảo đảm aggregate có đúng một nhiệm kỳ Leader hiện tại và trả về nhiệm kỳ đó. */
    private ProjectLeadershipTermEntity currentLeadershipTerm() {
        var currentTerms = leadershipTerms.stream()
                .filter(ProjectLeadershipTermEntity::isCurrent)
                .toList();
        if (currentTerms.isEmpty()) {
            throw new ProjectRuleViolationException("Project has no current Leader");
        }
        if (currentTerms.size() > 1) {
            throw new ProjectRuleViolationException("Project has multiple current Leaders");
        }
        return currentTerms.getFirst();
    }

    /** Kiểm tra người gọi có phải Mentor sở hữu Project hay không. */
    private void requireOwner(long actorMentorUserId) {
        if (mentorUserId != actorMentorUserId) {
            throw new ProjectAccessDeniedException();
        }
    }

    /** Từ chối mọi thay đổi khi Project đã ở trạng thái kết thúc chỉ đọc. */
    private void requireMutable() {
        if (status == ProjectStatus.COMPLETED) {
            throw new ProjectRuleViolationException("Completed Projects are read-only");
        }
    }

    /** Bảo đảm thông tin Intern tồn tại và đang đủ điều kiện tham gia Project. */
    private static void requireEligible(ProjectInternEligibility intern) {
        Objects.requireNonNull(intern, "intern");
        if (!intern.isEligible()) {
            throw new ProjectRuleViolationException("Intern must have an active account and internship");
        }
    }

    /** Kiểm tra chuỗi bắt buộc và trả về giá trị đã loại bỏ khoảng trắng đầu/cuối. */
    private static String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new ProjectRuleViolationException(message);
        }
        return value.trim();
    }

    /** Chuẩn hóa mô tả tùy chọn; chuỗi rỗng được lưu thành null. */
    private static String normalizeOptionalText(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
