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
public interface ProjectExitRequestRepository extends JpaRepository<ProjectExitRequestEntity, Long> {

    /**
     * Locks one exit request after its Project lock has been acquired.
     *
     * @param id request identifier
     * @return locked request, when present
     */
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
    @Query("""
            select new com.lab.labtimesheet.feature.project.model.dto.ProjectExitRequestRoute(
                    request.project.id)
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
    @Query("""
            select request.targetMembership.id
            from ProjectExitRequestEntity request
            where request.project.id = :projectId
              and request.status = com.lab.labtimesheet.feature.project.model.ProjectExitRequestStatus.PENDING
            """)
    Set<Long> findPendingTargetMembershipIdsByProjectId(@Param("projectId") long projectId);

    /**
     * Lists a Project's exit-request history in creation order.
     *
     * @param projectId Project identifier
     * @return retained request records
     */
    List<ProjectExitRequestEntity> findByProject_IdOrderByCreatedAtAscIdAsc(long projectId);

    /**
     * Locks all pending requests for lifecycle supersession.
     *
     * @param projectId Project identifier
     * @return pending requests
     */
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
