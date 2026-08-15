package com.lab.labtimesheet.feature.account.repository;

import java.util.Optional;

import com.lab.labtimesheet.feature.account.model.AccountStatus;
import com.lab.labtimesheet.feature.account.model.entity.AppUser;
import com.lab.labtimesheet.feature.account.model.GlobalRole;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Account-feature persistence boundary for global users. */
public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    /**
     * Finds an account by its canonical lower-case, trimmed email.
     *
     * @param email normalized email
     * @return matching account, if present
     */
    @Query("select u from AppUser u where lower(trim(u.email)) = :email")
    Optional<AppUser> findByNormalizedEmail(@Param("email") String email);

    /**
     * Locks an account row for a lifecycle mutation until the current transaction completes.
     *
     * @param id account identifier
     * @return locked account, if present
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AppUser u where u.id = :id")
    Optional<AppUser> findForUpdateById(@Param("id") Long id);

    /** Counts accounts matching an immutable role and lifecycle state. */
    long countByGlobalRoleAndAccountStatus(GlobalRole role, AccountStatus status);

    /** Counts accounts in a lifecycle state. */
    long countByAccountStatus(AccountStatus status);
}
