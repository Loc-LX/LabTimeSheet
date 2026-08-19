package com.lab.labtimesheet.feature.project.model.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

/**
 * Khoảng thời gian thành viên JPA liên kết một Intern với một Project.
 *
 * <p>Khi rời Project, khoảng thời gian được đóng; dòng dữ liệu và nguồn gốc vẫn được giữ lại cho
 * lịch sử Project và Task đã hoàn tất. Thành viên hiện tại được biểu diễn bằng {@code leftAt} null.
 */
@Entity
@Table(name = "project_memberships")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectMembershipEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private ProjectEntity project;

    @Column(name = "intern_user_id", nullable = false)
    private long internUserId;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    @Column(name = "added_by_user_id", nullable = false)
    private long addedByUserId;

    @Column(name = "left_at")
    private Instant leftAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    /** Tạo khoảng thời gian tham gia mới và lưu thông tin người thêm thành viên. */
    ProjectMembershipEntity(ProjectEntity project, long internUserId, Instant joinedAt, long addedByUserId) {
        this.project = project;
        this.internUserId = internUserId;
        this.joinedAt = joinedAt;
        this.addedByUserId = addedByUserId;
        this.updatedAt = joinedAt;
    }

    /**
     * Trả về định danh khoảng thời gian được Project và Task sử dụng.
     *
     * @return mã lượt tham gia đã lưu, hoặc null trước khi insert
     */
    public Long id() {
        return id;
    }

    /**
     * Trả về Intern tham gia.
     *
     * @return mã tài khoản Intern tham gia
     */
    public long internUserId() {
        return internUserId;
    }

    /**
     * Trả về thời điểm quyền thành viên bắt đầu.
     *
     * @return thời điểm bắt đầu tham gia, được tính cả thời điểm này
     */
    public Instant joinedAt() {
        return joinedAt;
    }

    /**
     * Trả về nguồn gốc tạo lượt tham gia.
     *
     * @return mã tài khoản trực tiếp tạo khoảng thời gian này
     */
    public long addedByUserId() {
        return addedByUserId;
    }

    /**
     * Trả về thời điểm quyền thành viên kết thúc.
     *
     * @return thời điểm kết thúc khoảng thời gian, hoặc null khi thành viên còn hiện tại
     */
    public Instant leftAt() {
        return leftAt;
    }

    /**
     * Cho biết Intern hiện còn thuộc Project hay không.
     *
     * @return true khi khoảng thời gian tham gia chưa có thời điểm kết thúc
     */
    public boolean isCurrent() {
        return leftAt == null;
    }
}
