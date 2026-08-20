package com.lab.labtimesheet.feature.project.repository;

import com.lab.labtimesheet.feature.project.model.InvitationStatus;
import com.lab.labtimesheet.feature.project.model.dto.ProjectInvitationNotificationRoute;
import com.lab.labtimesheet.feature.project.model.dto.ProjectInvitationRoute;
import com.lab.labtimesheet.feature.project.model.entity.ProjectInvitationEntity;
import jakarta.persistence.LockModeType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persists retained Project invitation records and their transaction locks.
 */
public interface ProjectInvitationRepository extends JpaRepository<ProjectInvitationEntity, Long> {

    /**
     * Locks one invitation after its Project lock has been acquired.
     *
     * @param id invitation identifier
     * @return locked invitation, when present
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select invitation from ProjectInvitationEntity invitation where invitation.id = :id")
    Optional<ProjectInvitationEntity> findLockedById(@Param("id") long id);

    /**
     * Reads only the Project and intended-Intern identifiers needed to establish mutation lock
     * order. The invitation entity is first loaded under its write lock after those locks.
     *
     * @param id invitation identifier
     * @return scalar route, or empty when the invitation does not exist
     */
    @Query("""
            select new com.lab.labtimesheet.feature.project.model.dto.ProjectInvitationRoute(
                    invitation.project.id, invitation.invitedInternUserId)
            from ProjectInvitationEntity invitation
            where invitation.id = :id
            """)
    Optional<ProjectInvitationRoute> findRouteById(@Param("id") long id);

    /**
     * Reads all Account identifiers that can receive a notification for one invitation before
     * any Project lock is acquired.
     *
     * @param id invitation identifier
     * @return scalar recipient route, or empty when the invitation does not exist
     */
    @Query("""
            select new com.lab.labtimesheet.feature.project.model.dto.ProjectInvitationNotificationRoute(
                    invitation.project.id,
                    invitation.invitedInternUserId,
                    invitation.issuingLeadershipTerm.membership.internUserId,
                    invitation.project.mentorUserId)
            from ProjectInvitationEntity invitation
            where invitation.id = :id
            """)
    Optional<ProjectInvitationNotificationRoute> findNotificationRouteById(@Param("id") long id);

    /**
     * Reads notification recipient routes for pending invitations in a selected add batch. The
     * scalar result lets the caller lock invitees, issuing Leaders, and the owning Mentor before
     * its Project write lock.
     *
     * @param projectId owning Project identifier
     * @param invitedInternUserIds selected Intern account identifiers
     * @return stable scalar recipient routes
     */
    @Query("""
            select new com.lab.labtimesheet.feature.project.model.dto.ProjectInvitationNotificationRoute(
                    invitation.project.id,
                    invitation.invitedInternUserId,
                    invitation.issuingLeadershipTerm.membership.internUserId,
                    invitation.project.mentorUserId)
            from ProjectInvitationEntity invitation
            where invitation.project.id = :projectId
              and invitation.invitedInternUserId in :invitedInternUserIds
              and invitation.status = com.lab.labtimesheet.feature.project.model.InvitationStatus.PENDING
            order by invitation.id asc
            """)
    List<ProjectInvitationNotificationRoute> findPendingNotificationRoutesByProjectIdAndInvitedInternUserIds(
            @Param("projectId") long projectId,
            @Param("invitedInternUserIds") Collection<Long> invitedInternUserIds);

    /**
     * Reads notification recipient routes for every pending invitation in a Project. This is a
     * bounded scalar pre-lock read used by lifecycle mutations that may revoke those rows.
     *
     * @param projectId owning Project identifier
     * @return stable scalar recipient routes
     */
    @Query("""
            select new com.lab.labtimesheet.feature.project.model.dto.ProjectInvitationNotificationRoute(
                    invitation.project.id,
                    invitation.invitedInternUserId,
                    invitation.issuingLeadershipTerm.membership.internUserId,
                    invitation.project.mentorUserId)
            from ProjectInvitationEntity invitation
            where invitation.project.id = :projectId
              and invitation.status = com.lab.labtimesheet.feature.project.model.InvitationStatus.PENDING
            order by invitation.id asc
            """)
    List<ProjectInvitationNotificationRoute> findPendingNotificationRoutesByProjectId(
            @Param("projectId") long projectId);

    /**
     * Finds the pending invitation for one Project/Intern pair under a write lock.
     *
     * @param projectId Project identifier
     * @param invitedInternUserId intended Intern account identifier
     * @return pending invitation, when present
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select invitation
            from ProjectInvitationEntity invitation
            where invitation.project.id = :projectId
              and invitation.invitedInternUserId = :invitedInternUserId
              and invitation.status = com.lab.labtimesheet.feature.project.model.InvitationStatus.PENDING
            """)
    Optional<ProjectInvitationEntity> findLockedPending(
            @Param("projectId") long projectId,
            @Param("invitedInternUserId") long invitedInternUserId);

    /**
     * Lists a Project's invitation history in creation order.
     *
     * @param projectId Project identifier
     * @return retained invitation records
     */
    List<ProjectInvitationEntity> findByProject_IdOrderByCreatedAtAscIdAsc(long projectId);

    /**
     * Locks all pending invitations for Project completion.
     *
     * @param projectId Project identifier
     * @return pending invitations in stable identifier order
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select invitation
            from ProjectInvitationEntity invitation
            where invitation.project.id = :projectId
              and invitation.status = com.lab.labtimesheet.feature.project.model.InvitationStatus.PENDING
            order by invitation.id
            """)
    List<ProjectInvitationEntity> findLockedPendingByProjectId(@Param("projectId") long projectId);

    /**
     * Locks pending invitations issued by one leadership term for lifecycle revocation.
     *
     * @param projectId Project identifier
     * @param issuingLeadershipTermId term identifier
     * @return pending invitations issued by that term
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select invitation
            from ProjectInvitationEntity invitation
            where invitation.project.id = :projectId
              and invitation.issuingLeadershipTerm.id = :issuingLeadershipTermId
              and invitation.status = com.lab.labtimesheet.feature.project.model.InvitationStatus.PENDING
            order by invitation.id
            """)
    List<ProjectInvitationEntity> findLockedPendingByTerm(
            @Param("projectId") long projectId,
            @Param("issuingLeadershipTermId") long issuingLeadershipTermId);
}
