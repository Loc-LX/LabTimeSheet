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
// Entity trung tâm đại diện cho một Project và toàn bộ lịch sử thành viên/Leader của nó.
// ProjectService gọi các method ở đây để giữ business rule gần dữ liệu trước khi Repository lưu xuống database.
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
    // [Lập kế hoạch Project]
    // Tạo Project ở trạng thái PLANNED cùng membership và Leader đầu tiên,
    // vì Project mới không được phép tồn tại mà chưa có Leader.
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
        // Ngày kết thúc không thể đứng trước ngày bắt đầu.
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
        // Leader ban đầu đồng thời phải là một thành viên hiện tại của Project.
        var membership = project.addEligibleMember(initialLeader, mentorUserId, at);
        project.leadershipTerms.add(new ProjectLeadershipTermEntity(project, membership, at, mentorUserId));
        return project;
    }

    /**
     * Adds a distinct eligible current member to a mutable Project after owner authorization.
     *
     * @param actorMentorUserId authenticated owning Mentor
     * @param intern current Account/internship eligibility fact
     * @param at server join instant
     * @return newly created membership interval
     */
    // [Thêm thành viên trực tiếp]
    // Chỉ Mentor sở hữu Project được thêm Intern đủ điều kiện; mỗi Intern chỉ có một membership đang hiệu lực.
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
    // [Chuẩn bị đổi Leader]
    // Đóng term Leader cũ trước. ProjectService sẽ flush thay đổi này rồi mới mở term mới
    // để database không có hai Leader hiện tại cùng lúc.
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
    // [Hoàn tất đổi Leader]
    // Chỉ tạo term mới sau khi xác nhận term cũ đã đóng; nhờ đó lịch sử Leader luôn nối tiếp nhau.
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
    // [Kích hoạt Project]
    // Project chỉ chuyển từ PLANNED sang ACTIVE khi còn ít nhất một Intern active,
    // Leader active và mọi Task hiện tại vẫn được gán cho một thành viên hiện tại.
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
    // [Hoàn thành Project]
    // Đóng term Leader và toàn bộ membership đang mở nhưng giữ lại các dòng lịch sử,
    // sau đó chuyển trạng thái Project thành COMPLETED để chặn thay đổi tiếp theo.
    public void complete(long actorMentorUserId, Instant at) {
        requireOwner(actorMentorUserId);
        Objects.requireNonNull(at, "at");
        if (status != ProjectStatus.ACTIVE) {
            throw new ProjectRuleViolationException("Only an active Project can be completed");
        }
        var endedAt = currentLeadershipTerm().end(at, actorMentorUserId);
        // Stream chỉ chọn membership còn hiệu lực để đóng; lịch sử đã đóng không bị sửa lại.
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
    // [Kiểm tra Mentor sở hữu]
    // Service dùng method này trước các thao tác chỉ dành cho Mentor quản lý Project.
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
    // [Lấy Leader hiện tại]
    // Leader được suy ra từ leadership term đang mở, không lưu thành một cột riêng để giữ lịch sử chính xác.
    public ProjectMembershipEntity currentLeader() {
        return currentMembership(currentLeadershipTerm().internUserId());
    }

    /**
     * Returns the current leadership term for Project-owned invitation and exit operations.
     *
     * @return current leadership term
     * @throws ProjectRuleViolationException when an open Project has no current Leader
     */
    // [Lấy term Leader hiện tại]
    // Tìm term chưa có thời điểm kết thúc; nếu không có thì Project đang sai invariant và phải dừng xử lý.
    public ProjectLeadershipTermEntity currentLeadershipTerm() {
        // Chỉ có một term không có endedAt được xem là Leader hiện tại.
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
    // [Tìm membership theo ID]
    // Chỉ chấp nhận membership thuộc chính Project này để tránh dùng nhầm ID từ Project khác.
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
    // [Nhận lời mời vào Project]
    // Khi Intern đồng ý invitation, Entity tạo membership mới. Eligibility của Account đã được Service kiểm tra trước đó.
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
        memberships.add(membership);
        updatedAt = at;
        return membership;
    }

    private ProjectMembershipEntity currentMembership(long internUserId) {
        // Lambda lọc đúng Intern và chỉ lấy interval chưa rời Project.
        return memberships.stream()
                .filter(membership -> membership.internUserId() == internUserId && membership.isCurrent())
                .findFirst()
                .orElseThrow(() -> new ProjectRuleViolationException(
                        "Leader must be a current same-Project member"));
    }

    private void requireOwner(long actorMentorUserId) {
        // Không tiết lộ thông tin Project cho Mentor không phải chủ sở hữu.
        if (mentorUserId != actorMentorUserId) {
            throw new ProjectAccessDeniedException();
        }
    }

    private void requireMutable() {
        // COMPLETED là trạng thái cuối; lịch sử sau khi hoàn thành chỉ được đọc.
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
