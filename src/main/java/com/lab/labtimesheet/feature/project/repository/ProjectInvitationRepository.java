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
// Repository quản lý các dòng invitation của Project.
// ProjectService dùng các query route trước để biết Account nào cần khóa, sau đó mới khóa invitation khi thay đổi.
public interface ProjectInvitationRepository extends JpaRepository<ProjectInvitationEntity, Long> {

    /**
     * Locks one invitation after its Project lock has been acquired.
     *
     * @param id invitation identifier
     * @return locked invitation, when present
     */
    // [Khóa một lời mời]
    // Khóa dòng invitation đang xử lý để tránh hai thao tác accept/revoke cùng đổi trạng thái PENDING.
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
    // [Lấy route của lời mời]
    // Chỉ đọc projectId và inviteeId, đủ để ProjectService sắp xếp thứ tự lock trước mutation.
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
    // [Lấy người nhận notification của lời mời]
    // Trả ID invitee, Leader phát hành và Mentor sở hữu để Service gửi thông báo đúng người.
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
    // [Notification cho các invitation đang chờ trong batch]
    // Dùng khi thêm nhiều member để các invitee liên quan được lock trước khi Project bị khóa.
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
    // [Notification cho toàn bộ invitation đang chờ]
    // Dùng khi lifecycle Project có thể tự thu hồi mọi invitation pending.
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
    // [Tìm và khóa lời mời pending]
    // Kiểm tra trùng invitation cho đúng Project/Intern và giữ lock đến hết transaction.
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
    // [Lịch sử lời mời của Project]
    // Spring Data query theo tên method và trả cả invitation đã kết thúc để dựng màn History.
    List<ProjectInvitationEntity> findByProject_IdOrderByCreatedAtAscIdAsc(long projectId);

    /**
     * Lists only actionable invitations addressed to one Intern, newest first.
     *
     * @param invitedInternUserId authenticated target Intern
     * @param status invitation state, normally pending
     * @return addressed invitation rows in stable newest-first order
     */
    // [Inbox lời mời của Intern]
    // Chỉ lấy invitation của chính Intern và trạng thái được truyền vào (thường là PENDING).
    List<ProjectInvitationEntity> findByInvitedInternUserIdAndStatusOrderByCreatedAtDescIdDesc(
            long invitedInternUserId, InvitationStatus status);

    /**
     * Locks all pending invitations for Project completion.
     *
     * @param projectId Project identifier
     * @return pending invitations in stable identifier order
     */
    // [Khóa lời mời pending khi hoàn thành Project]
    // ProjectService sẽ resolve/thu hồi các lời mời này cùng transaction hoàn thành Project.
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
    // [Khóa lời mời do một Leader phát hành]
    // Khi Leader đổi hoặc bị loại, Service dùng query này để thu hồi lời mời cũ của term đó.
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
