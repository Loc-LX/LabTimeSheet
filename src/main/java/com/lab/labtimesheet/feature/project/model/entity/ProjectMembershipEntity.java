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
 * JPA membership interval linking one Intern to one Project.
 *
 * <p>Leaving closes the interval; the row and its provenance remain for completed Project and
 * Task history. Current membership is represented by a null {@code leftAt}.
 */
// Entity đại diện cho một khoảng thời gian Intern thuộc Project, không phải bản ghi bị xóa khi Intern rời đi.
// ProjectEntity tạo và đóng khoảng này để vẫn giữ được lịch sử thành viên và Task cũ.
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

    @Column(name = "removed_by_mentor_user_id")
    private Long removedByMentorUserId;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    ProjectMembershipEntity(ProjectEntity project, long internUserId, Instant joinedAt, long addedByUserId) {
        this.project = project;
        this.internUserId = internUserId;
        this.joinedAt = joinedAt;
        this.addedByUserId = addedByUserId;
        this.updatedAt = joinedAt;
    }

    /**
     * Returns the interval identity used by Project and Task relationships.
     *
     * @return persisted membership identifier, or null before insertion
     */
    public Long id() {
        return id;
    }

    /**
     * Returns the participating Intern.
     *
     * @return participating Intern account identifier
     */
    public long internUserId() {
        return internUserId;
    }

    /**
     * Returns when membership authority began.
     *
     * @return inclusive membership start instant
     */
    public Instant joinedAt() {
        return joinedAt;
    }

    /**
     * Returns membership provenance.
     *
     * @return account identifier that directly created this interval
     */
    public long addedByUserId() {
        return addedByUserId;
    }

    /**
     * Returns when membership authority ended.
     *
     * @return interval end instant, or null while membership is current
     */
    public Instant leftAt() {
        return leftAt;
    }

    /**
     * Returns the Mentor that closed this retained membership interval.
     *
     * @return closing owning-Mentor identifier, or null while membership remains current
     */
    public Long removedByMentorUserId() {
        return removedByMentorUserId;
    }

    /**
     * Indicates whether the Intern currently belongs to the Project.
     *
     * @return true while the membership interval has no end instant
     */
    public boolean isCurrent() {
        return leftAt == null;
    }

    /**
     * Closes this interval while retaining its assignment and history identity.
     *
     * @param at server closure instant; equality with join time is advanced by one microsecond
     * @param mentorUserId owning Mentor performing the closure
     * @throws IllegalStateException when the interval is already closed
     * @throws IllegalArgumentException when the closure actor or instant is invalid
     */
    // [Đóng membership]
    // Đánh dấu thời điểm rời và Mentor thực hiện thay vì xóa dòng dữ liệu.
    // Nếu thời điểm rời trùng lúc tham gia, tăng thêm một microsecond để khoảng thời gian luôn hợp lệ.
    public void close(Instant at, long mentorUserId) {
        if (!isCurrent()) {
            throw new IllegalStateException("Membership is already closed");
        }
        if (at == null || mentorUserId <= 0) {
            throw new IllegalArgumentException("Membership closure is incomplete");
        }
        leftAt = at.isAfter(joinedAt) ? at : joinedAt.plusNanos(1_000);
        removedByMentorUserId = mentorUserId;
        updatedAt = leftAt;
    }
}
