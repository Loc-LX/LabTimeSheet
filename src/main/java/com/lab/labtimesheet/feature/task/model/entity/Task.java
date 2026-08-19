package com.lab.labtimesheet.feature.task.model.entity;

import com.lab.labtimesheet.feature.task.model.TaskStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Dòng Task được lưu với đúng một người được giao hiện tại trong cùng Project.
 *
 * <p>Thông tin người tạo không bao giờ đổi. Người thực hiện và thời điểm phân công mô tả lần phân
 * công hiện tại; xóa là xóa mềm để giữ lịch sử và version JPA phát hiện cập nhật cạnh tranh. Task
 * mới luôn bắt đầu ở TODO; trạng thái phải đi theo đồ thị cố định của {@link TaskStatus}.
 */
@Entity
@Table(name = "tasks")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Task {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private long projectId;

    @Column(name = "assignee_membership_id", nullable = false)
    private long assigneeMembershipId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "text")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private TaskStatus status;

    @Column(name = "due_date")
    private LocalDate dueDate;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "created_by_membership_id", nullable = false)
    private long creatorMembershipId;

    @Column(name = "assigned_by_membership_id", nullable = false)
    private long assignerMembershipId;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "deleted_by_membership_id")
    @Getter(AccessLevel.NONE)
    private Long deletedByMembershipId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @Getter(AccessLevel.NONE)
    private Instant updatedAt;

    @Version
    @Getter(AccessLevel.NONE)
    private long version;

    /**
     * Tạo Task TODO và ghi membership tạo Task đồng thời là người phân công ban đầu.
     *
     * @param projectId mã Project sở hữu Task
     * @param assigneeMembershipId membership hiện tại nhận Task trong cùng Project
     * @param title tiêu đề bắt buộc đã chuẩn hóa
     * @param description mô tả tùy chọn đã chuẩn hóa
     * @param dueDate ngày đến hạn tùy chọn đã được kiểm tra
     * @param actorMembershipId membership của người tạo Task
     * @param now thời điểm tạo và phân công do server cấp
     */
    public Task(
            long projectId,
            long assigneeMembershipId,
            String title,
            String description,
            LocalDate dueDate,
            long actorMembershipId,
            Instant now) {
        this.projectId = projectId;
        this.assigneeMembershipId = assigneeMembershipId;
        this.title = title;
        this.description = description;
        this.status = TaskStatus.TODO;
        this.dueDate = dueDate;
        this.assignedAt = now;
        this.creatorMembershipId = actorMembershipId;
        this.assignerMembershipId = actorMembershipId;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * [I2-PRJ-04] Chuyển một Task chưa hoàn thành sang membership hiện tại khác.
     *
     * <p>Không sửa membership tạo Task, trạng thái, bình luận hay work log. Vì luồng hỗ trợ này
     * không có một Mentor membership riêng, membership nhận Task được ghi là actor của lần phân
     * công mới để các khóa ngoại cùng Project vẫn hợp lệ.
     *
     * @param targetMembershipId membership hiện tại nhận Task
     * @param assignmentActorMembershipId membership được ghi là người thực hiện lần phân công mới
     * @param now thời điểm phân công do server cấp
     */
    public void transferUnfinishedTo(
            long targetMembershipId, long assignmentActorMembershipId, Instant now) {
        if (deletedAt != null || status == TaskStatus.DONE) {
            throw new IllegalArgumentException("Only a current unfinished Task can be transferred");
        }
        if (targetMembershipId <= 0
                || assignmentActorMembershipId <= 0
                || now == null
                || targetMembershipId == assigneeMembershipId) {
            throw new IllegalArgumentException("Task transfer target is invalid");
        }
        assigneeMembershipId = targetMembershipId;
        assignerMembershipId = assignmentActorMembershipId;
        assignedAt = now;
        updatedAt = now;
    }

    /**
     * Applies one permitted fixed-graph status transition and advances the update timestamp.
     *
     * @param target next Task status
     * @param now server-controlled mutation instant
     * @throws IllegalArgumentException when the requested direct transition is forbidden
     */
    public void changeStatus(TaskStatus target, Instant now) {
        if (!status.canTransitionTo(target)) {
            throw new IllegalArgumentException("Task status transition is not allowed");
        }
        status = target;
        updatedAt = now;
    }

}
