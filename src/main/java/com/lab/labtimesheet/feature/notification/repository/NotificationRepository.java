package com.lab.labtimesheet.feature.notification.repository;

import java.util.List;
import java.util.Optional;

import com.lab.labtimesheet.feature.notification.model.entity.NotificationEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

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
     * Counts unread rows for one authenticated recipient.
     *
     * @param recipientUserId authenticated recipient account identifier
     * @return unread row count
     */
    long countByRecipientUserIdAndReadAtIsNull(long recipientUserId);
}
