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
 * JPA leadership interval attached to an active same-Project membership.
 *
 * <p>Changes close the current term and create a new term; completion closes the final term.
 * Historical terms retain the appointing and ending Mentor attribution.
 */
// Entity lưu từng giai đoạn một Intern làm Leader của Project.
// Đổi Leader không sửa Leader cũ mà đóng term cũ và tạo term mới để lịch sử luôn truy vết được.
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
     * Returns the interval identity.
     *
     * @return persisted leadership-term identifier, or null before insertion
     */
    public Long id() {
        return id;
    }

    /**
     * Returns the Intern who led during this interval.
     *
     * @return Intern account identifier obtained from the retained membership interval
     */
    public long internUserId() {
        return membership.internUserId();
    }

    /**
     * Returns when leadership authority began.
     *
     * @return inclusive leadership start instant
     */
    public Instant startedAt() {
        return startedAt;
    }

    /**
     * Returns appointment provenance.
     *
     * @return owning Mentor account that appointed this Leader
     */
    public long appointedByMentorUserId() {
        return appointedByMentorUserId;
    }

    /**
     * Returns when leadership authority ended.
     *
     * @return term end instant, or null while current
     */
    public Instant endedAt() {
        return endedAt;
    }

    /**
     * Returns closure provenance.
     *
     * @return Mentor that closed the term, or null while current
     */
    public Long endedByMentorUserId() {
        return endedByMentorUserId;
    }

    /**
     * Indicates whether this term currently grants Leader authority.
     *
     * @return true while the term has no end instant
     */
    public boolean isCurrent() {
        return endedAt == null;
    }

    // [Đóng term Leader]
    // Chỉ ProjectEntity gọi method package-private này khi đổi Leader hoặc hoàn thành Project.
    // Kết thúc không được sớm hơn lúc bắt đầu và term đã đóng không thể đóng lần nữa.
    Instant end(Instant at, long mentorUserId) {
        if (!isCurrent() || at.isBefore(startedAt)) {
            throw new ProjectRuleViolationException("Leadership term end must follow its start");
        }
        endedAt = at.equals(startedAt) ? startedAt.plusNanos(1_000) : at;
        endedByMentorUserId = mentorUserId;
        return endedAt;
    }
}
