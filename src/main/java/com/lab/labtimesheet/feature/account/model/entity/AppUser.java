package com.lab.labtimesheet.feature.account.model.entity;

import java.time.Instant;

import com.lab.labtimesheet.feature.account.model.AccountStatus;
import com.lab.labtimesheet.feature.account.model.GlobalRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Persistent global account with immutable role, authentication lifecycle, creator attribution, and optimistic
 * locking. Password hashes are absent until a pending account consumes its activation token.
 */
@Entity
@Table(name = "app_users")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    private Long id;

    @Column(nullable = false, length = 320)
    @Getter
    private String email;

    @Column(name = "display_name", nullable = false, length = 120)
    @Getter
    private String displayName;

    @Column(name = "password_hash", length = 255)
    @Getter
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "global_role", nullable = false, length = 16, updatable = false)
    @Getter
    private GlobalRole globalRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "account_status", nullable = false, length = 32)
    @Getter
    private AccountStatus accountStatus;

    @Column(name = "activated_at")
    @Getter
    private Instant activatedAt;

    @Column(name = "locked_at")
    @Getter
    private Instant lockedAt;

    @Column(name = "deactivated_at")
    @Getter
    private Instant deactivatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id")
    private AppUser createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    private AppUser(String email, String displayName, String passwordHash, GlobalRole globalRole,
            AccountStatus accountStatus, Instant activatedAt, AppUser createdBy, Instant now) {
        this.email = email;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.globalRole = globalRole;
        this.accountStatus = accountStatus;
        this.activatedAt = activatedAt;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.updatedAt = now;
    }

    /**
     * Creates the first already-active Admin used to initialize an installation.
     *
     * @param email normalized email
     * @param displayName user-facing name
     * @param passwordHash encoded password
     * @param now server timestamp
     * @return new active Admin entity without a creator
     */
    public static AppUser bootstrapAdmin(String email, String displayName, String passwordHash, Instant now) {
        return new AppUser(email, displayName, passwordHash, GlobalRole.ADMIN, AccountStatus.ACTIVE, now, null, now);
    }

    /**
     * Creates a role-bearing account that cannot authenticate until activation assigns its password hash.
     *
     * @param email normalized email
     * @param displayName user-facing name
     * @param globalRole immutable global role
     * @param createdBy Admin creating the account
     * @param now server timestamp
     * @return new pending account entity
     */
    public static AppUser pending(
            String email, String displayName, GlobalRole globalRole, AppUser createdBy, Instant now) {
        return new AppUser(
                email, displayName, null, globalRole, AccountStatus.PENDING_ACTIVATION, null, createdBy, now);
    }

    /**
     * Transitions a pending account to active and records its encoded first password atomically.
     *
     * @param encodedPassword password-encoder output, never cleartext
     * @param now server activation timestamp
     * @throws IllegalStateException when the account is not pending activation
     */
    public void activate(String encodedPassword, Instant now) {
        if (accountStatus != AccountStatus.PENDING_ACTIVATION) {
            throw new IllegalStateException("Only a pending account can activate");
        }
        passwordHash = encodedPassword;
        accountStatus = AccountStatus.ACTIVE;
        activatedAt = now;
        updatedAt = now;
    }

    /**
     * Locks an active account while retaining its role, password, and attribution.
     *
     * @param now server timestamp recorded for the lock and update
     * @throws IllegalStateException when this account is not active
     */
    public void lock(Instant now) {
        if (accountStatus != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Only an active account can be locked");
        }
        accountStatus = AccountStatus.LOCKED;
        lockedAt = now;
        updatedAt = now;
    }

    /**
     * Unlocks a manually locked account without recreating credentials.
     *
     * @param now server timestamp recorded for the unlock and update
     * @throws IllegalStateException when this account is not manually locked
     */
    public void unlock(Instant now) {
        if (accountStatus != AccountStatus.LOCKED) {
            throw new IllegalStateException("Only a locked account can be unlocked");
        }
        accountStatus = AccountStatus.ACTIVE;
        lockedAt = null;
        updatedAt = now;
    }

    /**
     * Deactivates a credential-bearing account and retains its historical identity.
     *
     * @param now server timestamp recorded for deactivation and update
     * @throws IllegalStateException when the account is pending activation or already deactivated
     */
    public void deactivate(Instant now) {
        if (accountStatus == AccountStatus.PENDING_ACTIVATION || accountStatus == AccountStatus.DEACTIVATED) {
            throw new IllegalStateException("Account cannot be deactivated in this state");
        }
        accountStatus = AccountStatus.DEACTIVATED;
        lockedAt = null;
        deactivatedAt = now;
        updatedAt = now;
    }

    /**
     * Replaces the encoded password for an account with existing credentials.
     *
     * @param encodedPassword password-encoder output, never cleartext
     * @param now server timestamp recorded for the update
     * @throws IllegalStateException when the account is pending activation or deactivated
     */
    public void changePassword(String encodedPassword, Instant now) {
        if (accountStatus == AccountStatus.PENDING_ACTIVATION || accountStatus == AccountStatus.DEACTIVATED) {
            throw new IllegalStateException("Account cannot change its password in this state");
        }
        passwordHash = encodedPassword;
        updatedAt = now;
    }

}
