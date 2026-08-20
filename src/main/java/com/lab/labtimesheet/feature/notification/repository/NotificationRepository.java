package com.lab.labtimesheet.feature.notification.repository;

import java.util.List;

import com.lab.labtimesheet.feature.notification.model.entity.NotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persistence boundary for Platform-owned in-app notification rows. */
public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {
    /**
     * Loads one recipient's retained notifications in newest-first order for a future consumer/UI boundary.
     *
     * @param recipientUserId authenticated recipient account identifier
     * @return retained rows for that recipient only
     */
    List<NotificationEntity> findByRecipientUserIdOrderByCreatedAtDescIdDesc(long recipientUserId);

    /**
     * Counts unread rows for one authenticated recipient.
     *
     * @param recipientUserId authenticated recipient account identifier
     * @return unread row count
     */
    long countByRecipientUserIdAndReadAtIsNull(long recipientUserId);
}
