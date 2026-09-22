package com.lab.labtimesheet.feature.project.repository;

import com.lab.labtimesheet.feature.project.model.dto.ProjectExitRequestRoute;
import com.lab.labtimesheet.feature.project.model.entity.ProjectExitRequestEntity;
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
// Lưu/đọc yêu cầu rời hoặc bị loại khỏi project — Service gọi khi xử lý workflows.
public interface ProjectExitRequestRepository extends JpaRepository<ProjectExitRequestEntity, Long> {

    /**
     * Locks one exit request after its Project lock has been acquired.
     *
     * @param id request identifier
     * @return locked request, when present
     */
    // Khóa một yêu cầu exit đang duyệt/hủy.
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
    // Yêu cầu exit thuộc project nào, ai là người gửi.
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
    // Thành viên này đã có yêu cầu rời đang chờ chưa (mỗi người chỉ một yêu cầu pending).
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
    // Các thành viên đang có yêu cầu exit chờ duyệt (hiển thị trên workflows).
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
    // Ai đã gửi các yêu cầu exit đang chờ (để gửi thông báo đúng người).
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
    // Toàn bộ yêu cầu exit của project (tab History).
    List<ProjectExitRequestEntity> findByProject_IdOrderByCreatedAtAscIdAsc(long projectId);

    /**
     * Locks all pending requests for lifecycle supersession.
     *
     * @param projectId Project identifier
     * @return pending requests
     */
    // Khóa mọi yêu cầu exit đang chờ khi kết thúc project.
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
