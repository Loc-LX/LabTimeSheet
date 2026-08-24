package com.lab.labtimesheet.feature.project.model.entity;

import com.lab.labtimesheet.feature.project.exception.ProjectRuleViolationException;
import com.lab.labtimesheet.feature.project.model.InvitationResolutionCode;
import com.lab.labtimesheet.feature.project.model.InvitationStatus;
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
 * Retained invitation issued by one Project leadership term to one Intern.
 *
 * <p>Invitations have no expiry. A terminal row preserves the issuing term, response actor, and
 * resolution code so later Project history does not need a synthetic event record.
 */
// Entity lưu một lời mời Intern vào Project từ lúc tạo đến khi được chấp nhận, từ chối hoặc thu hồi.
// ProjectService dùng record này để giữ lịch sử lời mời, không xóa dữ liệu đã xử lý.
@Entity
@Table(name = "project_invitations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectInvitationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private ProjectEntity project;

    @Column(name = "invited_intern_user_id", nullable = false)
    private long invitedInternUserId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "issuing_leadership_term_id", nullable = false)
    private ProjectLeadershipTermEntity issuingLeadershipTerm;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private InvitationStatus status = InvitationStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "accepted_membership_id")
    private ProjectMembershipEntity acceptedMembership;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "resolved_by_user_id")
    private Long resolvedByUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "resolution_code", length = 32)
    private InvitationResolutionCode resolutionCode;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    private ProjectInvitationEntity(
            ProjectEntity project,
            long invitedInternUserId,
            ProjectLeadershipTermEntity issuingLeadershipTerm,
            Instant createdAt) {
        this.project = project;
        this.invitedInternUserId = invitedInternUserId;
        this.issuingLeadershipTerm = issuingLeadershipTerm;
        this.createdAt = createdAt;
        this.updatedAt = createdAt;
    }

    /**
     * Creates a pending invitation tied to the current leadership term.
     *
     * @param project mutable Project receiving the invitation
     * @param invitedInternUserId eligible target Intern
     * @param issuingLeadershipTerm current term issuing the invitation
     * @param createdAt server creation instant
     * @return unsaved pending invitation
     */
    // [Tạo lời mời chờ phản hồi]
    // Lời mời chỉ lưu dữ liệu ban đầu và có trạng thái PENDING; chưa tạo membership cho Intern ở bước này.
    public static ProjectInvitationEntity pending(
            ProjectEntity project,
            long invitedInternUserId,
            ProjectLeadershipTermEntity issuingLeadershipTerm,
            Instant createdAt) {
        if (project == null || invitedInternUserId <= 0 || issuingLeadershipTerm == null
                || !issuingLeadershipTerm.isCurrent() || createdAt == null) {
            throw new ProjectRuleViolationException("Invitation issuing context is invalid");
        }
        return new ProjectInvitationEntity(project, invitedInternUserId, issuingLeadershipTerm, createdAt);
    }

    /**
     * Returns the durable invitation identifier.
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
     * Returns the intended authenticated Intern.
     *
     * @return target Intern account identifier
     */
    public long invitedInternUserId() {
        return invitedInternUserId;
    }

    /**
     * Returns the leadership term that issued this invitation.
     *
     * @return issuing term
     */
    public ProjectLeadershipTermEntity issuingLeadershipTerm() {
        return issuingLeadershipTerm;
    }

    /**
     * Returns the current invitation state.
     *
     * @return persisted lifecycle state
     */
    public InvitationStatus status() {
        return status;
    }

    /**
     * Returns the membership created by acceptance.
     *
     * @return accepted membership, or null for non-accepted outcomes
     */
    public ProjectMembershipEntity acceptedMembership() {
        return acceptedMembership;
    }

    /**
     * Returns the terminal resolution time.
     *
     * @return resolution instant, or null while pending
     */
    public Instant resolvedAt() {
        return resolvedAt;
    }

    /**
     * Returns the actor that resolved the invitation.
     *
     * @return account identifier, or null for automatic lifecycle revocation
     */
    public Long resolvedByUserId() {
        return resolvedByUserId;
    }

    /**
     * Returns the retained terminal reason.
     *
     * @return resolution code, or null while pending
     */
    public InvitationResolutionCode resolutionCode() {
        return resolutionCode;
    }

    /**
     * Returns when the invitation was created by its issuing leadership term.
     *
     * @return immutable creation instant
     */
    public Instant createdAt() {
        return createdAt;
    }

    /**
     * Returns when the invitation row last changed state.
     *
     * @return latest stored update instant
     */
    public Instant updatedAt() {
        return updatedAt;
    }

    /**
     * Indicates whether the invitation can still be answered or revoked.
     *
     * @return true only while pending
     */
    public boolean isPending() {
        return status == InvitationStatus.PENDING;
    }

    /**
     * Resolves the invitation once and retains any accepted membership linkage.
     *
     * @param terminalStatus terminal state
     * @param code retained reason
     * @param resolvedByUserId response or management actor, nullable for automatic lifecycle changes
     * @param acceptedMembership membership created by acceptance, otherwise null
     * @param at server resolution instant
     */
    // [Kết thúc lời mời]
    // Chỉ lời mời PENDING được xử lý một lần. Nếu chấp nhận, acceptedMembership liên kết
    // lời mời với membership mới để màn hình lịch sử biết Intern đã vào Project từ invitation nào.
    public void resolve(
            InvitationStatus terminalStatus,
            InvitationResolutionCode code,
            Long resolvedByUserId,
            ProjectMembershipEntity acceptedMembership,
            Instant at) {
        if (!isPending()) {
            throw new ProjectRuleViolationException("Invitation is no longer pending");
        }
        if (terminalStatus == InvitationStatus.PENDING || code == null || at == null) {
            throw new IllegalArgumentException("Invitation resolution is incomplete");
        }
        status = terminalStatus;
        resolutionCode = code;
        this.resolvedByUserId = resolvedByUserId;
        this.acceptedMembership = acceptedMembership;
        resolvedAt = at;
        updatedAt = at;
    }
}
