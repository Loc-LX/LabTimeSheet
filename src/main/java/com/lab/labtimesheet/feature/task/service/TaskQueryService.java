package com.lab.labtimesheet.feature.task.service;

import com.lab.labtimesheet.feature.task.repository.TaskRepository;
import java.time.Instant;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Boundary công khai của Task để feature khác dùng mà không truy cập entity hoặc repository Task.
 */
@Service
@RequiredArgsConstructor
public class TaskQueryService {

    private final TaskRepository tasks;

    /**
     * Counts current Tasks assigned outside the supplied active membership set.
     *
     * <p>Only non-deleted Tasks are considered. An empty set means every current Task is invalid
     * and avoids an empty {@code NOT IN} predicate. This method joins an existing transaction; a
     * Project lifecycle caller must acquire and retain the Project write lock before calling it so
     * the assignee guard remains stable through the Project decision and commit.
     *
     * @param projectId Project whose current Task assignments are being validated
     * @param activeMembershipIds current eligible same-Project membership identifiers
     * @return number of current Tasks whose assignee is not in the supplied set
     */
    @Transactional(readOnly = true)
    public long countCurrentTasksAssignedOutside(long projectId, Set<Long> activeMembershipIds) {
        if (activeMembershipIds.isEmpty()) {
            return tasks.countByProjectIdAndDeletedAtIsNull(projectId);
        }
        return tasks.countCurrentTasksAssignedOutside(projectId, Set.copyOf(activeMembershipIds));
    }

    /**
     * [I2-PRJ-05] Đếm Task chưa xóa chưa ở DONE để boundary Project kiểm tra điều kiện hoàn tất.
     * ProjectService giữ khóa Project trước khi gọi nên các luồng đổi trạng thái Task trong ứng
     * dụng không thể chạy song song với quyết định hoàn tất.
     *
     * @param projectId mã Project cần kiểm tra
     * @return số Task còn TODO, IN_PROGRESS hoặc BLOCKED
     */
    @Transactional(readOnly = true)
    public long countUnfinishedTasks(long projectId) {
        if (projectId <= 0) {
            throw new IllegalArgumentException("Project ID must be positive");
        }
        return tasks.countUnfinishedByProjectId(projectId);
    }

    /**
     * [I2-PRJ-04] Chuyển toàn bộ Task chưa hoàn thành của membership sắp rời sang Leader hiện tại.
     *
     * <p>ProjectService đã khóa Project trước khi gọi. Các dòng Task được khóa theo cùng thứ tự,
     * cập nhật trong transaction đang mở và flush ngay; nếu bất kỳ dòng nào lỗi, exception chạy ra
     * ngoài để transaction Project rollback cả transfer lẫn đóng membership.
     *
     * @param projectId Project sở hữu các Task
     * @param departingMembershipId membership đang rời Project
     * @param leaderMembershipId membership Leader hiện tại hoặc replacement mới
     * @param assignedAt thời điểm transfer do server cấp
     */
    @Transactional
    public void transferUnfinishedTasks(
            long projectId,
            long departingMembershipId,
            long leaderMembershipId,
            Instant assignedAt) {
        if (projectId <= 0
                || departingMembershipId <= 0
                || leaderMembershipId <= 0
                || departingMembershipId == leaderMembershipId
                || assignedAt == null) {
            throw new IllegalArgumentException("Task transfer context is invalid");
        }
        var unfinishedTasks = tasks.findLockedUnfinishedByProjectIdAndAssigneeMembershipId(
                projectId, departingMembershipId);
        unfinishedTasks.forEach(task -> task.transferUnfinishedTo(
                leaderMembershipId, leaderMembershipId, assignedAt));
        tasks.flush();
    }
}
