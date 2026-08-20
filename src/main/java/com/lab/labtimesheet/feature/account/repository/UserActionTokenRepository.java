package com.lab.labtimesheet.feature.account.repository;

import java.util.List;
import java.util.Optional;

import com.lab.labtimesheet.feature.account.model.TokenPurpose;
import com.lab.labtimesheet.feature.account.model.entity.UserActionToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
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
     * Reads the token owner without locking so the service can acquire the account lock first.
     *
     * @param hash SHA-256 hash of the supplied raw bearer token
     * @param purpose expected workflow purpose
     * @return matching token without a database row lock, if present
     */
    @Query("select t from UserActionToken t where t.tokenHash = :hash and t.purpose = :purpose")
    Optional<UserActionToken> findByHashAndPurpose(
            @Param("hash") byte[] hash, @Param("purpose") TokenPurpose purpose);

    /**
     * Locks prior tokens for one account and purpose before issuing a replacement.
     *
     * @param userId owning account identifier, whose account row is locked first by the service
     * @param purpose workflow whose previous live token must be invalidated
     * @return prior tokens ordered newest first
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<UserActionToken> findByUserIdAndPurposeOrderByCreatedAtDesc(Long userId, TokenPurpose purpose);

    /**
     * Locks a token by identifier for delivery-failure invalidation.
     *
     * @param id token identifier
     * @return locked token, if present
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from UserActionToken t where t.id = :id")
    Optional<UserActionToken> findForUpdateById(@Param("id") Long id);
}
