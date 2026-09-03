package com.lab.labtimesheet.feature.project.model.entity;

import com.lab.labtimesheet.feature.project.exception.ProjectRuleViolationException;
import com.lab.labtimesheet.feature.project.model.ProjectExitRequestStatus;
import com.lab.labtimesheet.feature.project.model.ProjectExitRequestType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Retained request to close one Project membership after Mentor decision.
 *
 * <p>Pending requests do not close the target interval. Completed transfer batches and the
 * request decision remain separate feature-owned facts and are never rewritten here.
 */
// Entity lưu yêu cầu rời/loại thành viên khỏi Project để Mentor xét duyệt.
// Việc tạo request không làm Intern rời ngay; ProjectService chỉ đóng membership sau khi approve đúng điều kiện.
@Entity
@Table(name = "project_membership_exit_requests")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectExitRequestEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private ProjectEntity project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "target_membership_id", nullable = false)
    private ProjectMembershipEntity targetMembership;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "requester_membership_id", nullable = false)
    private ProjectMembershipEntity requesterMembership;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false, length = 24)
    private ProjectExitRequestType requestType;

    @Column(nullable = false, columnDefinition = "text")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ProjectExitRequestStatus status = ProjectExitRequestStatus.PENDING;

    @Column(name = "resolution_note", columnDefinition = "text")
    private String resolutionNote;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by_user_id")
    private Long resolvedByUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    private ProjectExitRequestEntity(
            ProjectEntity project,
            ProjectMembershipEntity targetMembership,
            ProjectMembershipEntity requesterMembership,
            ProjectExitRequestType requestType,
            String reason,
            Instant createdAt) {
        this.project = project;
        this.targetMembership = targetMembership;
        this.requesterMembership = requesterMembership;
        this.requestType = requestType;
        this.reason = reason;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    /**
     * Creates a pending exit request while preserving active membership rights.
     *
     * @param project owning Project
     * @param targetMembership membership requested for closure
     * @param requesterMembership authenticated requesting membership
     * @param requestType participant shape required by the request
     * @param reason normalized nonblank reason
     * @param createdAt server creation instant
     * @return unsaved pending request
     */
    // Tạo yêu cầu rời hoặc bị loại — chờ Mentor duyệt, thành viên vẫn ở lại project.
    // Điều này ngăn Intern tự gửi yêu cầu loại một thành viên khác hoặc Mentor tự loại chính mình.
    public static ProjectExitRequestEntity pending(
            ProjectEntity project,
            ProjectMembershipEntity targetMembership,
            ProjectMembershipEntity requesterMembership,
            ProjectExitRequestType requestType,
            String reason,
            Instant createdAt) {
        if (project == null || targetMembership == null || requesterMembership == null
                || requestType == null || reason == null || reason.isBlank() || createdAt == null) {
            throw new IllegalArgumentException("Exit request is incomplete");
        }
        if (requestType == ProjectExitRequestType.MEMBER_LEAVE
                && targetMembership.id() != null
                && !targetMembership.id().equals(requesterMembership.id())) {
            throw new ProjectRuleViolationException("Member leave must target the requester");
        }
        if (requestType == ProjectExitRequestType.LEADER_REMOVAL
                && targetMembership.id() != null
                && targetMembership.id().equals(requesterMembership.id())) {
            throw new ProjectRuleViolationException("Leader removal must target another member");
        }
        return new ProjectExitRequestEntity(
                project, targetMembership, requesterMembership, requestType, reason, createdAt);
    }

    /**
     * Returns the durable request identifier.
     *
     * @return database identifier, or null before insertion
     */
    public Long id() {
        return id;
    }

    /**
     * Returns the owning Project identifier.
     *
     * @return Project identifier
     */
    public long projectId() {
        return project.id();
    }

    /**
     * Returns the target membership identifier.
     *
     * @return membership identifier
     */
    public long targetMembershipId() {
        return targetMembership.id();
    }

    /**
     * Returns the requester membership identifier.
     *
     * @return membership identifier
     */
    public long requesterMembershipId() {
        return requesterMembership.id();
    }

    /**
     * Returns the request participant shape.
     *
     * @return request type
     */
    public ProjectExitRequestType requestType() {
        return requestType;
    }

    /**
     * Returns the retained nonblank reason.
     *
     * @return request reason
     */
    public String reason() {
        return reason;
    }

    /**
     * Returns the current request state.
     *
     * @return request status
     */
    public ProjectExitRequestStatus status() {
        return status;
    }

    /**
     * Returns the optional Mentor decision note.
     *
     * @return resolution note, or null while pending
     */
    public String resolutionNote() {
        return resolutionNote;
    }

    /**
     * Returns the terminal resolution instant.
     *
     * @return resolution instant, or null while pending
     */
    public Instant resolvedAt() {
        return resolvedAt;
    }

    /**
     * Returns the decision actor.
     *
     * @return resolving account identifier, or null for automatic supersession
     */
    public Long resolvedByUserId() {
        return resolvedByUserId;
    }

    /**
     * Returns when the retained exit request was created.
     *
     * @return immutable creation instant
     */
    public Instant createdAt() {
        return createdAt;
    }

    /**
     * Returns when the retained request most recently changed state.
     *
     * @return latest stored update instant
     */
    public Instant updatedAt() {
        return updatedAt;
    }

    /**
     * Indicates whether this request still awaits a decision.
     *
     * @return true while pending
     */
    public boolean isPending() {
        return status == ProjectExitRequestStatus.PENDING;
    }

    /**
     * Resolves a pending request without changing the target membership interval.
     *
     * @param terminalStatus terminal request state
     * @param note optional safe decision note
     * @param resolvedByUserId resolving account, nullable for automatic supersession
     * @param at server resolution instant
     */
    // Đóng yêu cầu exit (duyệt/từ chối/hủy); việc rời project thật do ProjectService làm tiếp.
    public void resolve(
            ProjectExitRequestStatus terminalStatus,
            String note,
            Long resolvedByUserId,
            Instant at) {
        if (!isPending()) {
            throw new ProjectRuleViolationException("Exit request is no longer pending");
        }
        if (terminalStatus == ProjectExitRequestStatus.PENDING || at == null) {
            throw new IllegalArgumentException("Exit resolution is incomplete");
        }
        status = terminalStatus;
        resolutionNote = note == null || note.isBlank() ? null : note.trim();
        this.resolvedByUserId = resolvedByUserId;
        resolvedAt = at;
        updatedAt = at;
    }
}
