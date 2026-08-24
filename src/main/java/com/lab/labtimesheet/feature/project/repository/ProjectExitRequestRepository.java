package com.lab.labtimesheet.feature.project.repository;

import com.lab.labtimesheet.feature.project.model.entity.ProjectExitRequestEntity;
import com.lab.labtimesheet.feature.project.model.dto.ProjectExitRequestRoute;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persists retained Project membership-exit requests and their transaction locks.
 */
// Repository lưu yêu cầu rời/loại thành viên và các query phục vụ duyệt yêu cầu.
// Service luôn dùng dữ liệu route hoặc write lock từ đây để tránh xử lý sai Project hay xử lý đồng thời.
public interface ProjectExitRequestRepository extends JpaRepository<ProjectExitRequestEntity, Long> {

    /**
     * Locks one exit request after its Project lock has been acquired.
     *
     * @param id request identifier
     * @return locked request, when present
     */
    // [Khóa yêu cầu exit]
    // Giữ lock trên request cho đến khi transaction hoàn tất để nó chỉ được quyết định một lần.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select request from ProjectExitRequestEntity request where request.id = :id")
    Optional<ProjectExitRequestEntity> findLockedById(@Param("id") long id);

    /**
     * Reads only the owning Project identifier needed to establish mutation lock order. The exit
     * request entity is first loaded under its write lock after Account and Project locks.
     *
     * @param id exit-request identifier
     * @return scalar route, or empty when the request does not exist
     */
    // [Lấy route của yêu cầu exit]
    // Chỉ trả projectId và requesterId để Service khóa đúng Account trước khi đọc request đầy đủ.
    @Query("""
            select new com.lab.labtimesheet.feature.project.model.dto.ProjectExitRequestRoute(
                    request.project.id, request.requesterMembership.internUserId)
            from ProjectExitRequestEntity request
            where request.id = :id
            """)
    Optional<ProjectExitRequestRoute> findRouteById(@Param("id") long id);

    /**
     * Finds the pending request targeting one membership under a write lock.
     *
     * @param targetMembershipId target membership identifier
     * @return pending request, when present
     */
    // [Tìm yêu cầu pending của membership]
    // Một target membership không thể có hai yêu cầu đang chờ; query kèm lock giúp Service kiểm tra điều này.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select request
            from ProjectExitRequestEntity request
            where request.targetMembership.id = :targetMembershipId
              and request.status = com.lab.labtimesheet.feature.project.model.ProjectExitRequestStatus.PENDING
            """)
    Optional<ProjectExitRequestEntity> findLockedPendingByTargetMembershipId(
            @Param("targetMembershipId") long targetMembershipId);

    /**
     * Returns current target membership identifiers with pending exit requests for one Project.
     * This read boundary exposes only stable IDs and does not leak the retained request entity.
     *
     * @param projectId Project identifier
     * @return pending target membership identifiers, empty when none are pending
     */
    // [Danh sách membership đang có yêu cầu exit]
    // Chỉ trả ID target để ProjectService kiểm tra nhanh trước khi thay đổi thành viên/Leader.
    @Query("""
            select request.targetMembership.id
            from ProjectExitRequestEntity request
            where request.project.id = :projectId
              and request.status = com.lab.labtimesheet.feature.project.model.ProjectExitRequestStatus.PENDING
            """)
    Set<Long> findPendingTargetMembershipIdsByProjectId(@Param("projectId") long projectId);

    /**
     * Reads the immutable requester account IDs for pending requests before a Project mutation
     * acquires its Account/profile locks. A requester may be a retained historical member rather
     * than a current member, so current-member snapshots alone are insufficient for notification
     * lock ordering.
     *
     * @param projectId Project identifier
     * @return pending requester account identifiers
     */
    // [Người gửi các yêu cầu pending]
    // Lấy user ID để Service khóa đúng Account khi một lifecycle mutation có thể tự kết thúc request.
    @Query("""
            select request.requesterMembership.internUserId
            from ProjectExitRequestEntity request
            where request.project.id = :projectId
              and request.status = com.lab.labtimesheet.feature.project.model.ProjectExitRequestStatus.PENDING
            order by request.requesterMembership.internUserId asc
            """)
    List<Long> findPendingRequesterUserIdsByProjectId(@Param("projectId") long projectId);

    /**
     * Lists a Project's exit-request history in creation order.
     *
     * @param projectId Project identifier
     * @return retained request records
     */
    // [Lịch sử yêu cầu exit của Project]
    // Spring Data trả cả request đã quyết định để QueryService hiển thị tab Exit decisions.
    List<ProjectExitRequestEntity> findByProject_IdOrderByCreatedAtAscIdAsc(long projectId);

    /**
     * Locks all pending requests for lifecycle supersession.
     *
     * @param projectId Project identifier
     * @return pending requests
     */
    // [Khóa tất cả yêu cầu pending của Project]
    // Dùng khi hoàn thành Project hoặc thao tác lifecycle cần supersede các request đang chờ.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select request
            from ProjectExitRequestEntity request
            where request.project.id = :projectId
              and request.status = com.lab.labtimesheet.feature.project.model.ProjectExitRequestStatus.PENDING
            order by request.id
            """)
    List<ProjectExitRequestEntity> findLockedPendingByProjectId(@Param("projectId") long projectId);
}
