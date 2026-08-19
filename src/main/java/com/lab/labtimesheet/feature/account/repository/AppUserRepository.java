package com.lab.labtimesheet.feature.account.repository;

import java.util.List;
import java.util.Optional;

import com.lab.labtimesheet.feature.account.model.AccountStatus;
import com.lab.labtimesheet.feature.account.model.dto.AccountAdminListItem;
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

    /**
     * Projects the non-secret Admin listing rows, including each Intern's internship summary.
     * When {@code role} is supplied the result is restricted to that immutable role.
     *
     * @param role optional role filter, or {@code null} for every role
     * @return deterministic rows ordered by display name then account ID
     */
    @Query("""
            select new com.lab.labtimesheet.feature.account.model.dto.AccountAdminListItem(
                u.id, u.email, u.displayName, u.globalRole, u.accountStatus,
                p.internshipStatus, p.internshipEndDate, u.lastLoginAt)
            from AppUser u
            left join InternProfile p on p.userId = u.id
            where :role is null or u.globalRole = :role
            order by u.displayName asc, u.id asc
            """)
    List<AccountAdminListItem> findAdminListItems(@Param("role") GlobalRole role);
}
