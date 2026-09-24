package com.lab.labtimesheet.feature.identity.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.lab.labtimesheet.feature.identity.model.AccountStatus;
import com.lab.labtimesheet.feature.identity.model.dto.AccountAdministrationView;
import com.lab.labtimesheet.feature.identity.model.dto.AccountIdentity;
import com.lab.labtimesheet.feature.identity.model.entity.AppUser;
import com.lab.labtimesheet.platform.model.GlobalRole;
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
            select new com.lab.labtimesheet.feature.identity.model.dto.AccountIdentity(
                u.id, u.email, u.displayName, u.globalRole, u.accountStatus)
            from AppUser u
            where u.globalRole = com.lab.labtimesheet.platform.model.GlobalRole.MENTOR
              and u.accountStatus = com.lab.labtimesheet.feature.identity.model.AccountStatus.ACTIVE
            order by u.id asc
            """)
    List<AccountIdentity> findActiveMentorIdentities();

    /**
     * Projects every account into the Admin-facing non-secret identity DTO.
     *
     * @return accounts in stable identifier order without credential or token material
     */
    @Query("""
            select new com.lab.labtimesheet.feature.identity.model.dto.AccountAdministrationView(
                u.id, u.email, u.displayName, u.globalRole, u.accountStatus)
            from AppUser u
            order by u.id asc
            """)
    List<AccountAdministrationView> findAdministrationViews();

    /**
     * Projects the identity-owned directory side after applying normalized text and immutable-role filters.
     *
     * @param search lower-case trimmed text, or the empty string for all accounts
     * @param role immutable global role, or {@code null} for all roles
     * @return matching non-secret account and optional Intern-profile projections in ID order
     */
    @Query("""
            select new com.lab.labtimesheet.feature.identity.model.dto.AccountAdministrationView(
                u.id, u.email, u.displayName, u.globalRole, u.accountStatus)
            from AppUser u
            where (:role is null or u.globalRole = :role)
              and (
                    :search = ''
                    or lower(trim(u.displayName)) like concat('%', :search, '%')
                    or lower(trim(u.email)) like concat('%', :search, '%')
              )
            order by u.id asc
            """)
    List<AccountAdministrationView> findAdministrationViewsByFilter(
            @Param("search") String search, @Param("role") GlobalRole role);

    /** Projects a batched set of account identities in stable identifier order. */
    @Query("""
            select new com.lab.labtimesheet.feature.identity.model.dto.AccountAdministrationView(
                u.id, u.email, u.displayName, u.globalRole, u.accountStatus)
            from AppUser u
            where u.id in :ids
            order by u.id asc
            """)
    List<AccountAdministrationView> findAdministrationViewsByIds(@Param("ids") Collection<Long> ids);

    /** Returns eligible account identities in database display-name order for cross-module composition. */
    @Query("""
            select new com.lab.labtimesheet.feature.identity.model.dto.AccountIdentity(
                u.id, u.email, u.displayName, u.globalRole, u.accountStatus)
            from AppUser u
            where u.globalRole = :role and u.accountStatus = :status
            order by u.displayName asc, u.id asc
            """)
    List<AccountIdentity> findIdentitiesByRoleAndStatusOrderByDisplayName(
            @Param("role") GlobalRole role, @Param("status") AccountStatus status);

    /** Returns account identifiers in stable order for cross-module intersection reads. */
    @Query("""
            select u.id from AppUser u
            where u.globalRole = :role and u.accountStatus = :status
            order by u.id asc
            """)
    List<Long> findIdsByRoleAndStatusOrderById(
            @Param("role") GlobalRole role, @Param("status") AccountStatus status);

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
