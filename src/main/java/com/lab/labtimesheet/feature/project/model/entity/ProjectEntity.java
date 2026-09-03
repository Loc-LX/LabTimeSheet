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
 * JPA aggregate root for Project lifecycle, membership intervals, and leadership intervals.
 *
 * <p>A planned or active Project owns exactly one current Leader membership. Completion is
 * terminal and closes current intervals; history is retained rather than reassigned or deleted.
 * Mutation methods enforce aggregate rules independently of browser control visibility.
 */
// Aggregate root: Project + Membership + LeadershipTerm. save root → JPA cascade INSERT các bảng con.
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
    // cascade=ALL: save Project root sẽ persist ProjectMembershipEntity mới trong list này.
    private List<ProjectMembershipEntity> memberships = new ArrayList<>();

    @OneToMany(mappedBy = "project", cascade = CascadeType.ALL)
    // LeadershipTerm tham chiếu Membership nên Hibernate sắp thứ tự INSERT theo foreign key dependency.
    private List<ProjectLeadershipTermEntity> leadershipTerms = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ProjectStatus status = ProjectStatus.PLANNED;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

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
     * Plans a Project with one eligible initial Leader membership and its first leadership term.
     * The returned aggregate is never empty or leaderless.
     *
     * @param mentorUserId active Mentor who owns the Project
     * @param name required Project name
     * @param description optional description
     * @param startDate inclusive start date
     * @param endDate inclusive end date, not before {@code startDate}
     * @param initialLeader eligible Intern appointed as first Leader
     * @param at server mutation instant used for all initial records
     * @return new unsaved planned aggregate
     */
    // === CREATE PROJECT | Entity.plan ===
    // Chức năng: dựng Project PLANNED + membership Leader + leadership term trong memory (chưa INSERT DB).
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
        // Validate lại ở domain boundary để factory vẫn an toàn nếu caller không phải MVC/@Valid.
        var normalizedName = requireText(name, "Project name is required");
        Objects.requireNonNull(startDate, "startDate");
        Objects.requireNonNull(endDate, "endDate");
        Objects.requireNonNull(at, "at");
        // Invariant domain: Project không thể kết thúc trước ngày bắt đầu.
        if (endDate.isBefore(startDate)) {
            throw new ProjectRuleViolationException("Project end date must not precede its start date");
        }
        // Service đã check Account state; Entity vẫn yêu cầu value object xác nhận eligibility.
        requireEligible(initialLeader);

        // Chỉ new object trong memory. Constructor private buộc caller dùng plan() để không tạo Project thiếu Leader.
        // description blank được normalize thành null; status đã khởi tạo mặc định PLANNED ở field phía trên.
        var project = new ProjectEntity(
                mentorUserId,
                normalizedName,
                normalizeOptionalText(description),
                startDate,
                endDate,
                at);
        var membership = project.addEligibleMember(initialLeader, mentorUserId, at);
        project.leadershipTerms.add(new ProjectLeadershipTermEntity(project, membership, at, mentorUserId));
        return project; // INSERT DB xảy ra ở ProjectService khi gọi projects.saveAndFlush
    }

    /**
     * Adds a distinct eligible current member to a mutable Project after owner authorization.
     *
     * @param actorMentorUserId authenticated owning Mentor
     * @param intern current Account/internship eligibility fact
     * @param at server join instant
     * @return newly created membership interval
     */
    // Mentor sở hữu thêm Intern đủ điều kiện; mỗi Intern chỉ có một membership đang hiệu lực trong Project.
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
     * Closes the current leadership term and prepares an eligible active-member replacement.
     * Callers must flush the closed interval before opening the replacement term.
     *
     * @param actorMentorUserId authenticated owning Mentor
     * @param intern eligible replacement Intern
     * @param at server effective instant
     * @return replacement membership and adjacent-term effective instant
     */
    // Mentor đóng term Leader cũ trước; Intern thay thế phải đang là thành viên và chưa là Leader.
    public ProjectLeaderChange prepareLeaderChange(
            long actorMentorUserId, ProjectInternEligibility intern, Instant at) {
        requireOwner(actorMentorUserId);
        requireMutable();
        requireEligible(intern);
        Objects.requireNonNull(at, "at");
        var replacement = currentMembership(intern.userId());
        var current = currentLeadershipTerm();
        if (current.internUserId() == intern.userId()) {
            throw new ProjectRuleViolationException("Selected Intern is already the current Leader");
        }

        var effectiveAt = current.end(at, actorMentorUserId);
        updatedAt = effectiveAt;
        return new ProjectLeaderChange(replacement, effectiveAt);
    }

    /**
     * Opens the replacement term after the former current term has been closed and flushed.
     *
     * @param actorMentorUserId authenticated owning Mentor
     * @param change prepared replacement from this transaction
     */
    // Sau khi term cũ đã đóng, Mentor mở term Leader mới để luôn chỉ có một Leader hiện tại.
    public void completeLeaderChange(long actorMentorUserId, ProjectLeaderChange change) {
        requireOwner(actorMentorUserId);
        Objects.requireNonNull(change, "change");
        if (leadershipTerms.stream().anyMatch(ProjectLeadershipTermEntity::isCurrent)) {
            throw new ProjectRuleViolationException("Current Leader must be closed before replacement");
        }
        leadershipTerms.add(new ProjectLeadershipTermEntity(
                this, change.replacement(), change.effectiveAt(), actorMentorUserId));
    }

    /**
     * Moves a planned Project to active after current member, Leader, and Task-assignee guards
     * pass. The transition is one-way and records the server activation instant.
     *
     * @param actorMentorUserId authenticated owning Mentor
     * @param activeInternUserIds currently eligible member user identifiers
     * @param allTaskAssigneesAreCurrent true when every non-deleted Task points to a current membership
     * @param at server activation instant
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
        status = ProjectStatus.ACTIVE; // PLANNED → ACTIVE
        activatedAt = at;
        updatedAt = at;
    }

    /**
     * Returns the persistence identity.
     *
     * @return persisted identifier, or null before insertion
     */
    public Long id() {
        return id;
    }

    /**
     * Returns immutable Project ownership.
     *
     * @return owning Mentor user identifier
     */
    public long mentorUserId() {
        return mentorUserId;
    }

    /**
     * Returns the display name.
     *
     * @return normalized Project name
     */
    public String name() {
        return name;
    }

    /**
     * Returns optional descriptive copy.
     *
     * @return normalized optional description, or null when absent
     */
    public String description() {
        return description;
    }

    /**
     * Returns the lower business-date boundary.
     *
     * @return inclusive Project start date
     */
    public LocalDate startDate() {
        return startDate;
    }

    /**
     * Returns the upper business-date boundary.
     *
     * @return inclusive Project end date
     */
    public LocalDate endDate() {
        return endDate;
    }

    /**
     * Returns the current lifecycle state.
     *
     * @return current aggregate lifecycle status
     */
    public ProjectStatus status() {
        return status;
    }

    /**
     * Returns when execution began.
     *
     * @return server activation instant, or null while planned
     */
    public Instant activatedAt() {
        return activatedAt;
    }

    /**
     * Returns when the Project became terminal and all current intervals were closed.
     *
     * @return server completion instant, or null while the Project is open
     */
    public Instant completedAt() {
        return completedAt;
    }

    /**
     * Completes an active Project after its caller has verified every current Task is DONE.
     *
     * <p>The final leadership term and every current membership are closed before the terminal
     * status is stored. Their retained rows and Task attribution are never deleted or rewritten.</p>
     *
     * @param actorMentorUserId authenticated owning Mentor
     * @param at server completion instant
     * @throws ProjectAccessDeniedException when the actor does not own this Project
     * @throws ProjectRuleViolationException when the Project is not active or has no current Leader
     */
    // Mentor đóng Leader và mọi membership đang mở, giữ lịch sử, rồi chuyển sang COMPLETED — không cho sửa tiếp.
    public void complete(long actorMentorUserId, Instant at) {
        requireOwner(actorMentorUserId);
        Objects.requireNonNull(at, "at");
        if (status != ProjectStatus.ACTIVE) {
            throw new ProjectRuleViolationException("Only an active Project can be completed");
        }
        var endedAt = currentLeadershipTerm().end(at, actorMentorUserId);
        memberships.stream()
                .filter(ProjectMembershipEntity::isCurrent)
                .forEach(membership -> membership.close(endedAt, actorMentorUserId));
        status = ProjectStatus.COMPLETED;
        completedAt = endedAt;
        updatedAt = endedAt;
    }

    /**
     * Returns a defensive snapshot of current and historical membership intervals.
     *
     * @return unmodifiable membership snapshot
     */
    public List<ProjectMembershipEntity> memberships() {
        return List.copyOf(memberships);
    }

    /**
     * Returns a defensive snapshot of current and historical leadership intervals.
     *
     * @return unmodifiable leadership-term snapshot
     */
    public List<ProjectLeadershipTermEntity> leadershipTerms() {
        return List.copyOf(leadershipTerms);
    }

    /**
     * Enforces owning-Mentor authority without revealing details to non-owners.
     *
     * @param actorMentorUserId authenticated Mentor identifier
     * @throws ProjectAccessDeniedException when the actor does not own this Project
     */
    // Chỉ Mentor sở hữu Project mới được gọi các thao tác quản lý; người khác nhận lỗi từ chối không tiết lộ chi tiết.
    public void authorizeOwner(long actorMentorUserId) {
        requireOwner(actorMentorUserId);
    }

    /**
     * Authorizes deletion of a disposable draft Project.
     *
     * <p>Once execution has started, the Project and its retained history are immutable. The
     * service performs the physical aggregate delete only after this guard succeeds.</p>
     *
     * @param actorMentorUserId authenticated owning Mentor
     * @throws ProjectAccessDeniedException when the actor does not own this Project
     * @throws ProjectRuleViolationException when the Project is no longer planned
     */
    public void requireDeletable(long actorMentorUserId) {
        requireOwner(actorMentorUserId);
        if (status != ProjectStatus.PLANNED) {
            throw new ProjectRuleViolationException("Only a planned Project can be deleted");
        }
    }

    /**
     * Checks only open membership intervals.
     *
     * @param internUserId Intern account identifier
     * @return true when the Intern currently belongs to this Project
     */
    public boolean hasCurrentMember(long internUserId) {
        return memberships.stream()
                .anyMatch(membership -> membership.internUserId() == internUserId && membership.isCurrent());
    }

    /**
     * Checks current and closed membership intervals for completed-history authorization.
     *
     * @param internUserId Intern account identifier
     * @return true when the Intern has ever belonged to this Project
     */
    public boolean hasEverHadMember(long internUserId) {
        return memberships.stream().anyMatch(membership -> membership.internUserId() == internUserId);
    }

    /**
     * Resolves the active membership referenced by the one current leadership term.
     * Completed Projects deliberately have no current Leader and callers must not use this method
     * for completed-history rendering.
     *
     * @return current Leader membership
     * @throws ProjectRuleViolationException when the open-Project Leader invariant is absent
     */
    public ProjectMembershipEntity currentLeader() {
        return currentMembership(currentLeadershipTerm().internUserId());
    }

    /**
     * Returns the current leadership term for Project-owned invitation and exit operations.
     *
     * @return current leadership term
     * @throws ProjectRuleViolationException when an open Project has no current Leader
     */
    public ProjectLeadershipTermEntity currentLeadershipTerm() {
        return leadershipTerms.stream()
                .filter(ProjectLeadershipTermEntity::isCurrent)
                .findFirst()
                .orElseThrow(() -> new ProjectRuleViolationException("Project has no current Leader"));
    }

    /**
     * Resolves one current membership by its Intern account identifier.
     *
     * @param internUserId Intern account identifier
     * @return current membership interval
     * @throws ProjectRuleViolationException when no current membership exists
     */
    public ProjectMembershipEntity currentMember(long internUserId) {
        return currentMembership(internUserId);
    }

    /**
     * Resolves one membership interval by its stable identifier.
     *
     * @param membershipId membership identifier
     * @return matching membership, current or historical
     * @throws ProjectRuleViolationException when the identifier is outside this Project
     */
    public ProjectMembershipEntity membership(long membershipId) {
        return memberships.stream()
                .filter(candidate -> candidate.id() != null && candidate.id() == membershipId)
                .findFirst()
                .orElseThrow(() -> new ProjectRuleViolationException("Membership is not in this Project"));
    }

    /**
     * Adds a membership accepted by the intended Intern's authenticated invitation response.
     *
     * @param internUserId accepting Intern account identifier
     * @param at server join instant
     * @return newly created current membership
     */
    // Intern đồng ý lời mời thì được thêm membership mới; Service đã kiểm tra đủ điều kiện trước đó.
    public ProjectMembershipEntity acceptMembership(long internUserId, Instant at) {
        requireMutable();
        if (internUserId <= 0 || at == null || hasCurrentMember(internUserId)) {
            throw new ProjectRuleViolationException("Intern is not eligible for Project membership");
        }
        return addEligibleMember(internUserId, internUserId, at);
    }

    private ProjectMembershipEntity addEligibleMember(
            ProjectInternEligibility intern, long addedByUserId, Instant at) {
        return addEligibleMember(intern.userId(), addedByUserId, at);
    }

    private ProjectMembershipEntity addEligibleMember(
            long internUserId, long addedByUserId, Instant at) {
        var membership = new ProjectMembershipEntity(this, internUserId, at, addedByUserId);
        memberships.add(membership); // cascade khi save Project root
        // Mutation aggregate cập nhật timestamp root cùng thời điểm membership bắt đầu.
        updatedAt = at;
        return membership;
    }

    private ProjectMembershipEntity currentMembership(long internUserId) {
        return memberships.stream()
                .filter(membership -> membership.internUserId() == internUserId && membership.isCurrent())
                .findFirst()
                .orElseThrow(() -> new ProjectRuleViolationException(
                        "Leader must be a current same-Project member"));
    }

    private void requireOwner(long actorMentorUserId) {
        if (mentorUserId != actorMentorUserId) {
            throw new ProjectAccessDeniedException();
        }
    }

    private void requireMutable() {
        if (status == ProjectStatus.COMPLETED) {
            throw new ProjectRuleViolationException("Completed Projects are read-only");
        }
    }

    private static void requireEligible(ProjectInternEligibility intern) {
        Objects.requireNonNull(intern, "intern");
        if (!intern.isEligible()) {
            throw new ProjectRuleViolationException("Intern must have an active account and internship");
        }
    }

    private static String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new ProjectRuleViolationException(message);
        }
        return value.trim();
    }

    private static String normalizeOptionalText(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}
