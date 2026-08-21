package com.lab.labtimesheet.feature.account.repository;

import java.util.List;

import com.lab.labtimesheet.feature.account.model.entity.AccountAdminEditEvent;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Append-only persistence boundary for the Admin account-edit audit trail. Only insertion and read
 * operations are exposed so audit rows cannot be changed or removed from application code.
 */
public interface AccountAdminEditEventRepository extends JpaRepository<AccountAdminEditEvent, Long> {
    /**
     * Returns the deterministic chronological edit trail for one account.
     *
     * @param targetUserId account whose edits are inspected
     * @return edit rows ordered by occurrence then insertion order
     */
    List<AccountAdminEditEvent> findByTargetUserIdOrderByOccurredAtAscIdAsc(Long targetUserId);
}