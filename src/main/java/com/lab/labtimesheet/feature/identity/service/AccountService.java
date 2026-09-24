package com.lab.labtimesheet.feature.identity.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.LongConsumer;

import com.lab.labtimesheet.feature.identity.model.AccountStatus;
import com.lab.labtimesheet.feature.identity.model.TokenPurpose;
import com.lab.labtimesheet.feature.identity.model.dto.AccountAdministrationView;
import com.lab.labtimesheet.feature.identity.model.dto.AccountCreation;
import com.lab.labtimesheet.feature.identity.model.dto.AccountDirectoryFilter;
import com.lab.labtimesheet.feature.identity.model.dto.AccountIdentity;
import com.lab.labtimesheet.feature.identity.model.entity.AppUser;
import com.lab.labtimesheet.feature.identity.model.entity.UserActionToken;
import com.lab.labtimesheet.feature.identity.repository.AppUserRepository;
import com.lab.labtimesheet.feature.identity.repository.UserActionTokenRepository;
import com.lab.labtimesheet.platform.model.GlobalRole;
import com.lab.labtimesheet.platform.service.MailDeliveryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Owns account creation, activation, lifecycle mutations, and identity lookup boundaries.
 * Mutations use JPA transactions and expose DTOs rather than account entities to other features.
 */
@Service
public class AccountService {
    private static final Duration ACTIVATION_LIFETIME = Duration.ofHours(24);
    private static final SecureRandom TOKEN_RANDOM = new SecureRandom();

    private final AppUserRepository users;
    private final UserActionTokenRepository tokens;
    private final MailDeliveryService mailDelivery;
    private final PasswordEncoder passwords;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private final SessionRegistry sessions;
    private final String publicOrigin;

    AccountService(
            AppUserRepository users,
            UserActionTokenRepository tokens,
            MailDeliveryService mailDelivery,
            PasswordEncoder passwords,
            Clock clock,
            TransactionTemplate transactions,
            SessionRegistry sessions,
            @Value("${lab.public-origin}") String publicOrigin) {
        this.users = users;
        this.tokens = tokens;
        this.mailDelivery = mailDelivery;
        this.passwords = passwords;
        this.clock = clock;
        this.transactions = transactions;
        this.sessions = sessions;
        this.publicOrigin = normalizeOrigin(publicOrigin);
    }

    /**
     * Creates one pending identity and lets the composing module persist its dependent row in the same transaction.
     * The callback runs after the account receives its identifier and before the activation token commits, so a
     * dependent uniqueness or invariant failure rolls back the whole creation before delivery.
     *
     * @param email account email
     * @param displayName user-facing name
     * @param role immutable global role
     * @param adminId active Admin creating the account
     * @param dependentProvisioner transaction-bound dependent-row provisioner receiving the new account ID
     * @return created account identifier and activation-delivery outcome
     */
    public AccountCreation createIdentity(
            String email,
            String displayName,
            GlobalRole role,
            long adminId,
            LongConsumer dependentProvisioner) {
        ValidatedIdentity identity = validateIdentity(email, displayName, role);
        if (dependentProvisioner == null) {
            throw new IllegalArgumentException("Dependent account provisioner is required");
        }
        if (!mailDelivery.isAvailable()) {
            throw new IllegalStateException("Active SMTP configuration is required for account creation");
        }

        String rawToken = newRawToken();
        byte[] tokenHash = sha256(rawToken);
        PendingActivation pending = transactions.execute(status ->
                createPendingIdentity(identity, adminId, tokenHash, dependentProvisioner));
        if (pending == null) {
            throw new IllegalStateException("Account creation did not complete");
        }
        try {
            mailDelivery.send(
                    identity.email(),
                    "Activate your Lab Timesheet account",
                    "Activate your account using this single-use link:\n" + activationLink(rawToken));
            return new AccountCreation(pending.userId(), true);
        } catch (RuntimeException deliveryFailure) {
            transactions.executeWithoutResult(status -> tokens.findForUpdateById(pending.tokenId())
                    .orElseThrow(() -> new IllegalStateException("Activation token is missing"))
                    .invalidate(clock.instant()));
            return new AccountCreation(pending.userId(), false);
        }
    }

    /**
     * Replaces a pending account's unused activation token and attempts immediate delivery. A delivery failure
     * invalidates the newly issued token while retaining the pending account for an explicit later retry.
     *
     * @param userId pending account identifier
     * @param adminId active Admin requesting the resend
     * @return identifier and delivery outcome
     */
    public AccountCreation resendActivation(long userId, long adminId) {
        if (!mailDelivery.isAvailable()) {
            throw new IllegalStateException("Active SMTP configuration is required for activation resend");
        }
        String rawToken = newRawToken();
        PendingActivation pending = transactions.execute(status -> issueActivation(userId, adminId, sha256(rawToken)));
        if (pending == null) {
            throw new IllegalStateException("Activation resend did not complete");
        }
        try {
            mailDelivery.send(
                    pending.email(),
                    "Activate your Lab Timesheet account",
                    "Activate your account using this single-use link:\n" + activationLink(rawToken));
            return new AccountCreation(userId, true);
        } catch (RuntimeException deliveryFailure) {
            invalidateTokenAfterDeliveryFailure(pending.tokenId());
            return new AccountCreation(userId, false);
        }
    }

    /**
     * Consumes a valid, unexpired activation bearer token once and assigns the first encoded password.
     * The token and user rows are locked in the surrounding transaction.
     *
     * @param rawToken raw token received from the activation link
     * @param password first password, containing 12 through 128 characters
     * @return {@code true} when activation completed; {@code false} for an invalid, expired, used, or stale token
     */
    @Transactional
    public boolean activate(String rawToken, String password) {
        BootstrapService.requirePassword(password);
        if (rawToken == null || rawToken.isBlank()) {
            return false;
        }

        byte[] tokenHash = sha256(rawToken);
        UserActionToken candidate = tokens.findByHashAndPurpose(tokenHash, TokenPurpose.ACTIVATION)
                .orElse(null);
        if (candidate == null) {
            return false;
        }

        AppUser user = users.findForUpdateById(candidate.getUserId()).orElse(null);
        if (user == null || user.getAccountStatus() != AccountStatus.PENDING_ACTIVATION) {
            return false;
        }
        UserActionToken token = tokens.findForUpdateByHashAndPurpose(tokenHash, TokenPurpose.ACTIVATION)
                .orElse(null);
        var now = clock.instant();
        if (token == null || !user.getId().equals(token.getUserId()) || !token.isUsableAt(now)) {
            return false;
        }
        user.activate(passwords.encode(password), now);
        token.markUsed(now);
        return true;
    }

    /**
     * Requests an enumeration-safe password reset. No token is created for unknown or ineligible accounts, and
     * SMTP absence fails closed before token issuance.
     *
     * @param email submitted login email
     * @return {@code false} only when SMTP is inactive; callers must use the same generic response either way
     */
    public boolean requestPasswordReset(String email) {
        if (!mailDelivery.isAvailable()) {
            return false;
        }
        String normalized = BootstrapService.normalizeEmail(email);
        AppUser user = users.findByNormalizedEmail(normalized).orElse(null);
        if (user == null || user.getAccountStatus() == AccountStatus.PENDING_ACTIVATION
                || user.getAccountStatus() == AccountStatus.DEACTIVATED) {
            return true;
        }
        String rawToken = newRawToken();
        PendingReset pending = transactions.execute(status -> issuePasswordReset(user.getId(), sha256(rawToken)));
        if (pending == null) {
            return true;
        }
        try {
            mailDelivery.send(
                    pending.email(),
                    "Reset your Lab Timesheet password",
                    "Reset your password using this single-use link:\n" + resetLink(rawToken));
        } catch (RuntimeException deliveryFailure) {
            invalidateTokenAfterDeliveryFailure(pending.tokenId());
        }
        return true;
    }

    /**
     * Consumes one unexpired reset token, replaces the password, and expires existing sessions.
     *
     * <p>The account row is locked before the token row so issuance and consumption share one PostgreSQL lock
     * order. The hash and usability checks are repeated after the token lock, making replacement and single-use
     * outcomes deterministic under concurrent requests.</p>
     *
     * @param rawToken opaque bearer value received from the reset link; it is never persisted
     * @param password replacement password containing 12 through 128 characters
     * @return {@code true} only when this request consumed the currently usable token
     */
    @Transactional
    public boolean resetPassword(String rawToken, String password) {
        BootstrapService.requirePassword(password);
        if (rawToken == null || rawToken.isBlank()) {
            return false;
        }
        byte[] tokenHash = sha256(rawToken);
        UserActionToken candidate = tokens.findByHashAndPurpose(tokenHash, TokenPurpose.PASSWORD_RESET)
                .orElse(null);
        if (candidate == null) {
            return false;
        }
        AppUser user = users.findForUpdateById(candidate.getUserId()).orElse(null);
        if (user == null || user.getAccountStatus() == AccountStatus.PENDING_ACTIVATION
                || user.getAccountStatus() == AccountStatus.DEACTIVATED) {
            return false;
        }
        UserActionToken token = tokens.findForUpdateByHashAndPurpose(tokenHash, TokenPurpose.PASSWORD_RESET)
                .orElse(null);
        Instant now = clock.instant();
        if (token == null || !user.getId().equals(token.getUserId()) || !token.isUsableAt(now)) {
            return false;
        }
        user.changePassword(passwords.encode(password), now);
        token.markUsed(now);
        invalidateSessions(user);
        return true;
    }

    /**
     * Locks an active account under Admin authorization and expires all registered sessions.
     *
     * @param targetUserId account to lock
     * @param adminId active Admin performing the mutation
     * @throws IllegalArgumentException when the actor or target account is missing or the actor is not an active Admin
     * @throws IllegalStateException when the target account is not active
     */
    @Transactional
    public void lockAccount(long targetUserId, long adminId) {
        requireActiveAdminId(adminId);
        AppUser target = users.findForUpdateById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        target.lock(clock.instant());
        invalidateSessions(target);
    }

    /**
     * Unlocks a manually locked account while retaining its role and password.
     *
     * @param targetUserId account to unlock
     * @param adminId active Admin performing the mutation
     * @throws IllegalArgumentException when the actor or target account is missing or the actor is not an active Admin
     * @throws IllegalStateException when the target account is not manually locked
     */
    @Transactional
    public void unlockAccount(long targetUserId, long adminId) {
        requireActiveAdminId(adminId);
        AppUser target = users.findForUpdateById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        target.unlock(clock.instant());
    }

    /**
     * Deactivates an account under Admin authorization and expires all registered sessions.
     *
     * @param targetUserId account to deactivate
     * @param adminId active Admin performing the mutation
     * @throws IllegalArgumentException when the actor or target account is missing or the actor is not an active Admin
     * @throws IllegalStateException when the target account cannot be deactivated from its current state
     */
    @Transactional
    public void deactivateAccount(long targetUserId, long adminId) {
        requireActiveAdminId(adminId);
        AppUser target = users.findForUpdateById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        target.deactivate(clock.instant());
        invalidateSessions(target);
    }

    /**
     * Lists non-secret identity facts for active Admin administration.
     *
     * @param adminId active Admin account identifier
     * @return stable account-ID ordered administration projections
     * @throws IllegalArgumentException when the actor is missing or not an active Admin
     */
    @Transactional(readOnly = true)
    public List<AccountAdministrationView> administrationViews(long adminId) {
        return administrationViews(adminId, new AccountDirectoryFilter(null, null));
    }

    /**
     * Lists the Admin directory with normalized text and immutable-role filtering.
     *
     * @param adminId active Admin account identifier
     * @param filter normalized directory search and role filter
     * @return non-secret identity facts in stable ID order
     * @throws IllegalArgumentException when the actor is not an active Admin or the filter is malformed
     */
    @Transactional(readOnly = true)
    public List<AccountAdministrationView> administrationViews(long adminId, AccountDirectoryFilter filter) {
        requireActiveAdminId(adminId);
        if (filter == null) {
            throw new IllegalArgumentException("Account directory filter is required");
        }
        return users.findAdministrationViewsByFilter(filter.search(), filter.role());
    }

    /**
     * Resolves one Admin-authorized account administration projection without exposing persistence types.
     *
     * @param targetUserId account being inspected
     * @param adminId active Admin account identifier
     * @return non-secret identity facts
     * @throws IllegalArgumentException when the actor or target is unavailable
     */
    @Transactional(readOnly = true)
    public AccountAdministrationView administrationView(long targetUserId, long adminId) {
        return administrationViews(adminId).stream()
                .filter(view -> view.id() == targetUserId)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
    }

    /**
     * Projects a batched set of non-secret account facts after Admin authorization.
     *
     * @param adminId active Admin account identifier
     * @param accountIds account identifiers to project
     * @return matching accounts in ascending identifier order
     */
    @Transactional(readOnly = true)
    public List<AccountAdministrationView> administrationViewsByIds(
            long adminId, Collection<Long> accountIds) {
        requireActiveAdminId(adminId);
        if (accountIds == null) {
            throw new IllegalArgumentException("Account IDs are required");
        }
        return users.findAdministrationViewsByIds(accountIds);
    }

    /**
     * Applies an Admin-authorized email correction while a composing module changes dependent data in the same
     * transaction. The dependent callback runs after the Admin and target account locks and before constraint flush
     * and required delivery, retaining the original lock and rollback semantics.
     *
     * @param targetUserId account being corrected
     * @param adminId active Admin authorizing the correction
     * @param replacementEmail replacement email, or {@code null}
     * @param dependentChange whether the composing module has a dependent field change
     * @param dependentCorrector transaction-bound dependent correction callback
     */
    @Transactional
    public void correctIdentity(
            long targetUserId,
            long adminId,
            String replacementEmail,
            boolean dependentChange,
            Consumer<AccountIdentity> dependentCorrector) {
        if (dependentCorrector == null) {
            throw new IllegalArgumentException("Dependent account corrector is required");
        }
        AppUser admin = users.findForUpdateById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("Admin not found"));
        requireActiveAdmin(admin);
        AppUser target = users.findForUpdateById(targetUserId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        if (target.getAccountStatus() == AccountStatus.DEACTIVATED) {
            throw new IllegalStateException("Deactivated account is read-only");
        }

        String correctedEmail = replacementEmail == null
                ? null
                : BootstrapService.normalizeEmail(replacementEmail);
        boolean emailChanged = correctedEmail != null && !correctedEmail.equals(target.getEmail());
        if (!emailChanged && !dependentChange) {
            throw new IllegalArgumentException("At least one account field must change");
        }
        if (dependentChange && target.getGlobalRole() != GlobalRole.INTERN) {
            throw new IllegalArgumentException("Internship fields are allowed only for Intern accounts");
        }
        if (correctedEmail != null) {
            users.findByNormalizedEmail(correctedEmail)
                    .filter(existing -> !existing.getId().equals(targetUserId))
                    .ifPresent(existing -> {
                        throw new IllegalArgumentException("An account with this email already exists");
                    });
        }

        if (dependentChange) {
            dependentCorrector.accept(identity(target));
        }
        if (!emailChanged) {
            return;
        }
        if (!mailDelivery.isAvailable()) {
            throw new IllegalStateException("Tested active SMTP configuration is required for email correction");
        }

        String previousEmail = target.getEmail();
        Instant now = clock.instant();
        target.correctEmail(correctedEmail, now);
        // EntityManager.flush() behind this repository flushes dependent managed entities in the same persistence unit.
        users.flush();
        try {
            if (target.getAccountStatus() == AccountStatus.PENDING_ACTIVATION) {
                String rawToken = newRawToken();
                invalidateLiveTokens(targetUserId, TokenPurpose.ACTIVATION, now);
                UserActionToken token = tokens.save(UserActionToken.activation(
                        targetUserId, sha256(rawToken), now.plus(ACTIVATION_LIFETIME), adminId, now));
                tokens.flush();
                mailDelivery.send(
                        correctedEmail,
                        "Activate your Lab Timesheet account",
                        "Activate your account using this single-use link:\n" + activationLink(rawToken));
                if (token.getId() == null) {
                    throw new IllegalStateException("Activation token was not created");
                }
            } else {
                mailDelivery.send(
                        correctedEmail,
                        "Your Lab Timesheet email was corrected",
                        "An administrator corrected the email identity for your Lab Timesheet account.");
                invalidateSessionsForEmail(previousEmail);
            }
        } catch (RuntimeException deliveryFailure) {
            throw new IllegalStateException("Identity correction delivery failed", deliveryFailure);
        }
    }

    /**
     * Resolves an account boundary DTO by database identifier regardless of lifecycle state.
     *
     * @param userId account identifier
     * @return non-secret identity and lifecycle state
     * @throws IllegalArgumentException when the account does not exist
     */
    @Transactional(readOnly = true)
    public AccountIdentity requireIdentityById(long userId) {
        return identityById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
    }

    /**
     * Resolves an optional account identity without using an exception as a cross-module absence signal.
     *
     * @param userId account identifier
     * @return non-secret identity when the account exists
     */
    @Transactional(readOnly = true)
    public java.util.Optional<AccountIdentity> identityById(long userId) {
        return users.findById(userId).map(AccountService::identity);
    }

    /**
     * Resolves an account boundary DTO by normalized email regardless of lifecycle state.
     *
     * @param email email address, normalized by trimming and lower-casing
     * @return non-secret identity and lifecycle state
     * @throws IllegalArgumentException when the account does not exist
     */
    @Transactional(readOnly = true)
    public AccountIdentity requireIdentityByEmail(String email) {
        // Principal từ Spring Security chỉ cung cấp tên/email; AccountService là boundary đổi nó thành identity
        // của hệ thống. Repository chuẩn hóa email và chỉ lấy account đang tồn tại; không load ProjectEntity và
        // không lock row vì đây mới là bước định danh actor, chưa phải bước authorize/mutate.
        return users.findByNormalizedEmail(BootstrapService.normalizeEmail(email)).map(AccountService::identity)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
    }

    /**
     * Lists every active global Mentor as immutable non-secret identities for cross-feature decision queues.
     * The repository performs one scalar constructor projection ordered by account ID; this boundary does not load
     * Account entities or acquire lifecycle locks, so consumers must perform their own authorization and locking
     * before mutating a request.
     *
     * @return active global Mentor identities in ascending account-ID order
     */
    @Transactional(readOnly = true)
    public List<AccountIdentity> activeGlobalMentorIdentities() {
        return users.findActiveMentorIdentities();
    }

    /** Returns account identities filtered by role and status in database display-name order. */
    @Transactional(readOnly = true)
    public List<AccountIdentity> identitiesByRoleAndStatusOrderedByDisplayName(
            GlobalRole role, AccountStatus status) {
        if (role == null || status == null) {
            throw new IllegalArgumentException("Account role and status are required");
        }
        return users.findIdentitiesByRoleAndStatusOrderByDisplayName(role, status);
    }

    /** Returns account IDs filtered by role and status in ascending identifier order. */
    @Transactional(readOnly = true)
    public List<Long> accountIdsByRoleAndStatusOrderedById(GlobalRole role, AccountStatus status) {
        if (role == null || status == null) {
            throw new IllegalArgumentException("Account role and status are required");
        }
        return users.findIdsByRoleAndStatusOrderById(role, status);
    }

    /**
     * Locks a normalized set of accounts in ascending ID order and returns immutable identity snapshots. Locks are
     * retained by the caller's surrounding transaction.
     */
    @Transactional
    public List<AccountIdentity> lockedIdentities(Collection<Long> userIds) {
        if (userIds == null) {
            throw new IllegalArgumentException("Account IDs are required");
        }
        List<Long> orderedIds = userIds.stream()
                .map(id -> {
                    if (id == null || id <= 0) {
                        throw new IllegalArgumentException("Account IDs must be positive");
                    }
                    return id;
                })
                .distinct()
                .sorted()
                .toList();
        return orderedIds.stream()
                .map(id -> users.findForUpdateById(id)
                        .map(AccountService::identity)
                        .orElseThrow(() -> new IllegalArgumentException("Account not found")))
                .toList();
    }

    /** Counts accounts in one authentication lifecycle state. */
    @Transactional(readOnly = true)
    public long countByAccountStatus(AccountStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("Account status is required");
        }
        return users.countByAccountStatus(status);
    }

    /**
     * Resolves only an Account identifier for an authenticated principal's email as a routing operation.
     * This method deliberately performs no lifecycle authorization, entity hydration, or row lock. Consumers must
     * pass the identifier to a subsequent Account-owned operation that acquires the required lifecycle locks and
     * authorizes the requested mutation inside its surrounding transaction.
     *
     * @param email authenticated principal email, normalized by trimming and lower-casing
     * @return matching Account identifier
     * @throws IllegalArgumentException when the email is blank or no account matches it
     */
    @Transactional(readOnly = true)
    public long requireAccountIdByEmail(String email) {
        return users.findAccountIdByNormalizedEmail(BootstrapService.normalizeEmail(email))
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
    }

    /**
     * Resolves an authenticated active Admin by normalized email.
     *
     * @param email authenticated principal name
     * @return Admin account identifier
     * @throws IllegalArgumentException when the account is not an active Admin
     */
    @Transactional(readOnly = true)
    public long requireActiveAdminId(String email) {
        AppUser user = users.findByNormalizedEmail(BootstrapService.normalizeEmail(email))
                .orElseThrow(() -> new IllegalStateException("Authenticated Admin is missing"));
        return requireActiveAdmin(user);
    }

    /**
     * Requires the identified account to be an active Admin.
     *
     * @param userId account identifier
     * @return the same identifier after authorization
     * @throws IllegalArgumentException when the account is missing or not an active Admin
     */
    @Transactional(readOnly = true)
    public long requireActiveAdminId(long userId) {
        AppUser user = users.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Admin not found"));
        return requireActiveAdmin(user);
    }

    private static long requireActiveAdmin(AppUser user) {
        if (user.getGlobalRole() != GlobalRole.ADMIN || user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("An active Admin is required");
        }
        return user.getId();
    }

    private PendingReset issuePasswordReset(long userId, byte[] tokenHash) {
        AppUser user = users.findForUpdateById(userId).orElse(null);
        if (user == null || user.getAccountStatus() == AccountStatus.PENDING_ACTIVATION
                || user.getAccountStatus() == AccountStatus.DEACTIVATED) {
            return null;
        }
        Instant now = clock.instant();
        for (UserActionToken previous : tokens.findByUserIdAndPurposeOrderByCreatedAtDesc(
                userId, TokenPurpose.PASSWORD_RESET)) {
            if (previous.getUsedAt() == null && previous.getInvalidatedAt() == null) {
                previous.invalidate(now);
            }
        }
        tokens.flush();
        UserActionToken token = tokens.save(UserActionToken.passwordReset(
                userId, tokenHash, now.plus(Duration.ofMinutes(30)), now));
        return new PendingReset(userId, user.getEmail(), token.getId());
    }

    private PendingActivation issueActivation(long userId, long adminId, byte[] tokenHash) {
        AppUser user = users.findForUpdateById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        if (user.getAccountStatus() != AccountStatus.PENDING_ACTIVATION) {
            throw new IllegalStateException("Only a pending account can receive activation");
        }
        AppUser admin = users.findForUpdateById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("Admin not found"));
        requireActiveAdmin(admin);
        Instant now = clock.instant();
        for (UserActionToken previous : tokens.findByUserIdAndPurposeOrderByCreatedAtDesc(
                userId, TokenPurpose.ACTIVATION)) {
            if (previous.getUsedAt() == null && previous.getInvalidatedAt() == null) {
                previous.invalidate(now);
            }
        }
        tokens.flush();
        UserActionToken token = tokens.save(UserActionToken.activation(
                userId, tokenHash, now.plus(Duration.ofHours(24)), adminId, now));
        return new PendingActivation(userId, user.getEmail(), token.getId());
    }

    private void invalidateLiveTokens(long userId, TokenPurpose purpose, Instant now) {
        for (UserActionToken previous : tokens.findByUserIdAndPurposeOrderByCreatedAtDesc(userId, purpose)) {
            if (previous.getUsedAt() == null && previous.getInvalidatedAt() == null) {
                previous.invalidate(now);
            }
        }
        tokens.flush();
    }

    private void invalidateTokenAfterDeliveryFailure(long tokenId) {
        transactions.executeWithoutResult(status -> tokens.findForUpdateById(tokenId)
                .ifPresent(token -> {
                    if (token.getUsedAt() == null && token.getInvalidatedAt() == null) {
                        token.invalidate(clock.instant());
                    }
                }));
    }

    private String resetLink(String rawToken) {
        return publicOrigin + "/reset-password?token=" + rawToken;
    }

    private void invalidateSessions(AppUser user) {
        invalidateSessionsForEmail(user.getEmail());
    }

    private void invalidateSessionsForEmail(String email) {
        sessions.getAllPrincipals().stream()
                .filter(principal -> principalMatches(principal, email))
                .flatMap(principal -> sessions.getAllSessions(principal, false).stream())
                .forEach(SessionInformation::expireNow);
    }

    private static boolean principalMatches(Object principal, String email) {
        return principal instanceof UserDetails details
                ? email.equals(details.getUsername())
                : email.equals(principal);
    }

    private static AccountIdentity identity(AppUser user) {
        return new AccountIdentity(
                user.getId(), user.getEmail(), user.getDisplayName(), user.getGlobalRole(), user.getAccountStatus());
    }

    private PendingActivation createPendingIdentity(
            ValidatedIdentity account,
            long adminId,
            byte[] tokenHash,
            LongConsumer dependentProvisioner) {
        AppUser admin = users.findForUpdateById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("Admin not found"));
        requireActiveAdmin(admin);

        var now = clock.instant();
        AppUser user = users.save(AppUser.pending(
                account.email(), account.displayName(), account.role(), admin, now));
        dependentProvisioner.accept(user.getId());
        UserActionToken token = tokens.save(UserActionToken.activation(
                user.getId(), tokenHash, now.plus(ACTIVATION_LIFETIME), admin.getId(), now));
        return new PendingActivation(user.getId(), user.getEmail(), token.getId());
    }

    private String activationLink(String rawToken) {
        return publicOrigin + "/activate?token=" + rawToken;
    }

    private static ValidatedIdentity validateIdentity(String email, String displayName, GlobalRole role) {
        if (role == null) {
            throw new IllegalArgumentException("Account role is required");
        }
        return new ValidatedIdentity(
                BootstrapService.normalizeEmail(email), requireText(displayName, "Display name"), role);
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    private static String normalizeOrigin(String value) {
        String origin = requireText(value, "Public origin");
        while (origin.endsWith("/")) {
            origin = origin.substring(0, origin.length() - 1);
        }
        if (!origin.startsWith("http://") && !origin.startsWith("https://")) {
            throw new IllegalArgumentException("Public origin must use HTTP or HTTPS");
        }
        return origin;
    }

    private static String newRawToken() {
        byte[] bytes = new byte[32];
        TOKEN_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static byte[] sha256(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private record ValidatedIdentity(String email, String displayName, GlobalRole role) {
    }

    private record PendingActivation(long userId, String email, long tokenId) {
    }

    private record PendingReset(long userId, String email, long tokenId) {
    }
}
