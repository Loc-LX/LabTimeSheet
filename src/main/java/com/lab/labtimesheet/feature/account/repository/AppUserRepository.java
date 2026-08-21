package com.lab.labtimesheet.feature.account.repository;

import java.util.List;
import java.util.Optional;

import com.lab.labtimesheet.feature.account.model.AccountStatus;
import com.lab.labtimesheet.feature.account.model.GlobalRole;
import com.lab.labtimesheet.feature.account.model.dto.AccountIdentity;
import com.lab.labtimesheet.feature.account.model.entity.AppUser;
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
     * Resolves only the identifier for a canonical email without hydrating the account entity.
     *
     * @param email normalized email
     * @return matching account identifier, if present
     */
    @Query("select u.id from AppUser u where lower(trim(u.email)) = :email")
    Optional<Long> findAccountIdByNormalizedEmail(@Param("email") String email);

    /**
     * Projects active global Mentors directly into the immutable Account boundary DTO in account-ID order.
     *
     * @return active Mentor identities without loading {@link AppUser} entities or acquiring row locks
     */
    @Query("""
            select new com.lab.labtimesheet.feature.account.model.dto.AccountIdentity(
                u.id, u.email, u.displayName, u.globalRole, u.accountStatus)
            from AppUser u
            where u.globalRole = com.lab.labtimesheet.feature.account.model.GlobalRole.MENTOR
              and u.accountStatus = com.lab.labtimesheet.feature.account.model.AccountStatus.ACTIVE
            order by u.id asc
            """)
    List<AccountIdentity> findActiveMentorIdentities();

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
