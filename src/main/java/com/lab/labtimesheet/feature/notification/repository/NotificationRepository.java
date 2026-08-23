package com.lab.labtimesheet.feature.notification.repository;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

import com.lab.labtimesheet.feature.notification.model.NotificationEmailStatus;
import com.lab.labtimesheet.feature.notification.model.entity.NotificationEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence boundary for Platform-owned in-app notification rows. */
public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {
    /**
     * Loads one recipient's retained notifications in newest-first order for the inbox boundary.
     *
     * @param recipientUserId authenticated recipient account identifier
     * @return retained rows for that recipient only
     */
    List<NotificationEntity> findByRecipientUserIdOrderByCreatedAtDescIdDesc(long recipientUserId);

    /**
     * Finds a notification only when it belongs to the supplied authenticated recipient.
     *
     * <p>The pessimistic write lock serializes concurrent mark-read requests while retaining the
     * recipient predicate, so a duplicate request observes the first committed {@code read_at}
     * rather than failing on the entity version.
     *
     * @param id requested notification identifier
     * @param recipientUserId authenticated recipient account identifier
     * @return the recipient's row, or empty for a missing or foreign identifier
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<NotificationEntity> findByIdAndRecipientUserId(long id, long recipientUserId);

    /**
     * Locks one notification row for a delivery transition.
     *
     * @param id notification identifier
     * @return locked notification, if present
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select n from NotificationEntity n where n.id = :id")
    Optional<NotificationEntity> findForUpdateById(@Param("id") long id);

    /**
     * Selects a bounded due-email batch in retry order while locking each row for one worker.
     *
     * @param status pending delivery state
     * @param now server instant used for the due boundary
     * @param page bounded batch request
     * @return locked due rows, oldest due first
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select n from NotificationEntity n
            where n.emailStatus = :status
              and n.emailNextAttemptAt <= :now
            order by n.emailNextAttemptAt asc, n.id asc
            """)
    List<NotificationEntity> findDueEmailRetries(
            @Param("status") NotificationEmailStatus status,
            @Param("now") Instant now,
            Pageable page);

    /** Returns failed ordinary-email rows for the Admin operational view. */
    List<NotificationEntity> findByEmailStatusOrderByUpdatedAtDescIdDesc(NotificationEmailStatus status);

    /**
     * Counts unread rows for one authenticated recipient.
     *
     * @param recipientUserId authenticated recipient account identifier
     * @return unread row count
     */
    long countByRecipientUserIdAndReadAtIsNull(long recipientUserId);
}
