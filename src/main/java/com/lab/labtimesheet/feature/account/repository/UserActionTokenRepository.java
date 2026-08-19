package com.lab.labtimesheet.feature.account.repository;

import java.time.Instant;
import java.util.Optional;

import com.lab.labtimesheet.feature.account.model.TokenPurpose;
import com.lab.labtimesheet.feature.account.model.entity.UserActionToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persistence boundary for hashed, one-time account-action tokens. */
public interface UserActionTokenRepository extends JpaRepository<UserActionToken, Long> {
    /**
     * Locks a token selected by hash and purpose for atomic single-use consumption.
     *
     * @param hash SHA-256 hash of the supplied raw bearer token
     * @param purpose expected workflow purpose
     * @return locked matching token, if present
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from UserActionToken t where t.tokenHash = :hash and t.purpose = :purpose")
    Optional<UserActionToken> findForUpdateByHashAndPurpose(
            @Param("hash") byte[] hash, @Param("purpose") TokenPurpose purpose);

    /**
     * Selects a token by hash and purpose without locking, used to render the reset form state before submission.
     *
     * @param hash SHA-256 hash of the supplied raw bearer token
     * @param purpose expected workflow purpose
     * @return matching token, if present
     */
    @Query("select t from UserActionToken t where t.tokenHash = :hash and t.purpose = :purpose")
    Optional<UserActionToken> findByHashAndPurpose(
            @Param("hash") byte[] hash, @Param("purpose") TokenPurpose purpose);

    /**
     * Locks a token by identifier for delivery-failure invalidation.
     *
     * @param id token identifier
     * @return locked token, if present
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from UserActionToken t where t.id = :id")
    Optional<UserActionToken> findForUpdateById(@Param("id") Long id);

    /**
     * Invalidates the single live token of one purpose for an account before a replacement is issued, keeping the
     * partial unique index satisfied. Executes as its own statement so the prior token leaves the live set before
     * the replacement row is inserted.
     *
     * @param userId account identifier
     * @param purpose expected workflow purpose
     * @param now server invalidation timestamp
     * @return number of live tokens invalidated
     */
    @Modifying
    @Query("""
            update UserActionToken t
            set t.invalidatedAt = :now
            where t.userId = :userId and t.purpose = :purpose
              and t.usedAt is null and t.invalidatedAt is null
            """)
    int invalidateLive(@Param("userId") Long userId, @Param("purpose") TokenPurpose purpose,
            @Param("now") Instant now);
}
