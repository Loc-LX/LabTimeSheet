package com.lab.labtimesheet.feature.account.model.entity;

import java.time.Instant;
import java.util.Arrays;

import com.lab.labtimesheet.feature.account.model.TokenPurpose;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Persistent one-time user-action token state. Only a defensive copy of the SHA-256 token hash is stored; raw
 * bearer tokens never enter this entity.
 */
@Entity
@Table(name = "user_action_tokens")
public class UserActionToken {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private TokenPurpose purpose;

    @Column(name = "token_hash", nullable = false, columnDefinition = "bytea")
    private byte[] tokenHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "invalidated_at")
    private Instant invalidatedAt;

    @Column(name = "issued_by_user_id")
    private Long issuedByUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** Required by JPA; domain instances are created through named factories. */
    protected UserActionToken() {
    }

    private UserActionToken(long userId, byte[] tokenHash, Instant expiresAt, long issuedByUserId, Instant now) {
        this.userId = userId;
        this.purpose = TokenPurpose.ACTIVATION;
        this.tokenHash = Arrays.copyOf(tokenHash, tokenHash.length);
        this.expiresAt = expiresAt;
        this.issuedByUserId = issuedByUserId;
        this.createdAt = now;
    }

    /**
     * Creates an unused activation-token record from a cryptographic hash.
     *
     * @param userId account being activated
     * @param tokenHash 32-byte SHA-256 hash of the raw bearer token
     * @param expiresAt exclusive expiry instant
     * @param issuedByUserId Admin issuing the token
     * @param now server creation timestamp
     * @return new activation-token entity
     */
    public static UserActionToken activation(
            long userId, byte[] tokenHash, Instant expiresAt, long issuedByUserId, Instant now) {
        return new UserActionToken(userId, tokenHash, expiresAt, issuedByUserId, now);
    }

    /**
     * Checks single-use and exclusive-expiry state at a server timestamp.
     *
     * @param now server timestamp
     * @return {@code true} only before expiry and before use or invalidation
     */
    public boolean isUsableAt(Instant now) {
        return usedAt == null && invalidatedAt == null && now.isBefore(expiresAt);
    }

    /**
     * Consumes the token once.
     *
     * @param now server consumption timestamp
     * @throws IllegalStateException when expired, invalidated, or already used
     */
    public void markUsed(Instant now) {
        if (!isUsableAt(now)) {
            throw new IllegalStateException("Activation token is not usable");
        }
        usedAt = now;
    }

    /**
     * Invalidates an unused token, idempotently, after its delivery fails.
     *
     * @param now server invalidation timestamp
     * @throws IllegalStateException when the token was already consumed
     */
    public void invalidate(Instant now) {
        if (usedAt != null) {
            throw new IllegalStateException("A used token cannot be invalidated");
        }
        if (invalidatedAt == null) {
            invalidatedAt = now;
        }
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public TokenPurpose getPurpose() {
        return purpose;
    }

    /**
     * Returns a defensive copy of the persisted token hash.
     *
     * @return copied SHA-256 hash bytes
     */
    public byte[] getTokenHash() {
        return Arrays.copyOf(tokenHash, tokenHash.length);
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public Instant getInvalidatedAt() {
        return invalidatedAt;
    }
}
