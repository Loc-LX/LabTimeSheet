package com.lab.labtimesheet.feature.project.model.entity;

import com.lab.labtimesheet.feature.project.exception.ProjectRuleViolationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * [I1-PRJ-03, I2-PRJ-01, I2-PRJ-02] Khoảng thời gian nhiệm kỳ Leader JPA gắn với một lượt tham gia
 * đang hoạt động của cùng Project.
 *
 * <p>Mỗi lần thay đổi sẽ đóng nhiệm kỳ hiện tại và tạo nhiệm kỳ mới; khi hoàn tất Project, nhiệm kỳ
 * cuối cùng được đóng. Nhiệm kỳ lịch sử giữ lại thông tin Mentor bổ nhiệm và Mentor kết thúc.
 */
@Entity
@Table(name = "project_leadership_terms")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectLeadershipTermEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private ProjectEntity project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "membership_id", nullable = false)
    private ProjectMembershipEntity membership;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "appointed_by_mentor_user_id", nullable = false)
    private long appointedByMentorUserId;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "ended_by_mentor_user_id")
    private Long endedByMentorUserId;

    /** Tạo nhiệm kỳ Leader mới cho lượt tham gia hiện tại. */
    ProjectLeadershipTermEntity(
            ProjectEntity project,
            ProjectMembershipEntity membership,
            Instant startedAt,
            long appointedByMentorUserId) {
        this.project = project;
        this.membership = membership;
        this.startedAt = startedAt;
        this.appointedByMentorUserId = appointedByMentorUserId;
    }

    /**
     * Trả về định danh khoảng thời gian.
     *
     * @return mã nhiệm kỳ đã lưu, hoặc null trước khi insert
     */
    public Long id() {
        return id;
    }

    /**
     * Trả về Intern giữ vai trò Leader trong khoảng thời gian này.
     *
     * @return mã tài khoản Intern lấy từ lượt tham gia được lưu lại
     */
    public long internUserId() {
        return membership.internUserId();
    }

    /**
     * Trả về thời điểm quyền Leader bắt đầu.
     *
     * @return thời điểm bắt đầu nhiệm kỳ Leader, được tính cả thời điểm này
     */
    public Instant startedAt() {
        return startedAt;
    }

    /**
     * Trả về nguồn gốc bổ nhiệm.
     *
     * @return tài khoản Mentor sở hữu đã bổ nhiệm Leader này
     */
    public long appointedByMentorUserId() {
        return appointedByMentorUserId;
    }

    /**
     * Trả về thời điểm quyền Leader kết thúc.
     *
     * @return thời điểm kết thúc nhiệm kỳ, hoặc null khi còn hiện tại
     */
    public Instant endedAt() {
        return endedAt;
    }

    /**
     * Trả về nguồn gốc thao tác đóng nhiệm kỳ.
     *
     * @return Mentor đã đóng nhiệm kỳ, hoặc null khi nhiệm kỳ còn hiện tại
     */
    public Long endedByMentorUserId() {
        return endedByMentorUserId;
    }

    /**
     * Cho biết nhiệm kỳ này hiện còn cấp quyền Leader hay không.
     *
     * @return true khi nhiệm kỳ chưa có thời điểm kết thúc
     */
    public boolean isCurrent() {
        return endedAt == null;
    }

    /** Đóng nhiệm kỳ hiện tại tại thời điểm hợp lệ và ghi nhận Mentor thực hiện. */
    Instant end(Instant at, long mentorUserId) {
        if (!isCurrent() || at.isBefore(startedAt)) {
            throw new ProjectRuleViolationException("Leadership term end must follow its start");
        }
        endedAt = at.equals(startedAt) ? startedAt.plusNanos(1_000) : at;
        endedByMentorUserId = mentorUserId;
        return endedAt;
    }
}
