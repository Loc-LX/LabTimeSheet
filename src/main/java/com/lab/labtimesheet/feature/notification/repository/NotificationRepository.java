package com.lab.labtimesheet.feature.notification.repository;

import java.util.List;
import java.util.Optional;

import com.lab.labtimesheet.feature.notification.model.entity.Notification;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistence boundary for recipient-scoped notifications and delivery state.
 */
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * Lists one recipient's notifications newest first with deterministic ID tie-breaking.
     *
     * @param recipientUserId recipient account identifier
     * @return recipient-owned notifications
     */
    List<Notification> findByRecipientUserIdOrderByCreatedAtDescIdDesc(long recipientUserId);

    /**
     * Counts unread notifications for one recipient only.
     *
     * @param recipientUserId recipient account identifier
     * @return unread row count
     */
    long countByRecipientUserIdAndReadAtIsNull(long recipientUserId);

    /**
     * Locks a recipient-owned row before a mark-read mutation.
     *
     * @param id notification identifier
     * @param recipientUserId authenticated recipient account identifier
     * @return the locked row when it belongs to the recipient
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select notification from Notification notification "
            + "where notification.id = :id and notification.recipientUserId = :recipientUserId")
    Optional<Notification> findForUpdateByIdAndRecipientUserId(
            @Param("id") long id, @Param("recipientUserId") long recipientUserId);

    /**
     * Locks one notification before an after-commit delivery transition.
     *
     * @param id notification identifier
     * @return locked row, if present
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select notification from Notification notification where notification.id = :id")
    Optional<Notification> findForUpdateById(@Param("id") long id);
}
