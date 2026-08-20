package com.lab.labtimesheet.feature.account.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.lab.labtimesheet.feature.account.model.AccountStatus;
import com.lab.labtimesheet.feature.account.model.GlobalRole;
import com.lab.labtimesheet.feature.account.model.InternshipStatus;
import com.lab.labtimesheet.feature.account.model.TokenPurpose;
import com.lab.labtimesheet.feature.account.model.dto.AccountCreation;
import com.lab.labtimesheet.feature.account.model.dto.AccountIdentity;
import com.lab.labtimesheet.feature.account.model.dto.AccountSummary;
import com.lab.labtimesheet.feature.account.model.dto.CreateAccountCommand;
import com.lab.labtimesheet.feature.account.model.dto.EligibleInternOption;
import com.lab.labtimesheet.feature.account.model.dto.InternshipLifecycleGuard;
import com.lab.labtimesheet.feature.account.model.dto.LockedAccountMutationEligibility;
import com.lab.labtimesheet.feature.account.model.dto.InternWorkWindow;
import com.lab.labtimesheet.feature.account.model.entity.AppUser;
import com.lab.labtimesheet.feature.account.model.entity.InternProfile;
import com.lab.labtimesheet.feature.account.model.entity.UserActionToken;
import com.lab.labtimesheet.feature.account.repository.AppUserRepository;
import com.lab.labtimesheet.feature.account.repository.InternProfileRepository;
import com.lab.labtimesheet.feature.account.repository.UserActionTokenRepository;
import com.lab.labtimesheet.feature.integration.service.MailDeliveryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Owns account creation, activation, identity lookup, and Intern eligibility boundaries.
 * Mutations use JPA transactions and expose DTOs rather than account entities to other features.
 */
@Service
public class AccountService {
    private static final Duration ACTIVATION_LIFETIME = Duration.ofHours(24);
    private static final SecureRandom TOKEN_RANDOM = new SecureRandom();

    private final AppUserRepository users;
    private final InternProfileRepository internProfiles;
    private final UserActionTokenRepository tokens;
    private final MailDeliveryService mailDelivery;
    private final PasswordEncoder passwords;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private final SessionRegistry sessions;
    private final String publicOrigin;

    AccountService(
            AppUserRepository users,
            InternProfileRepository internProfiles,
            UserActionTokenRepository tokens,
            MailDeliveryService mailDelivery,
            PasswordEncoder passwords,
            Clock clock,
            TransactionTemplate transactions,
            SessionRegistry sessions,
            @Value("${lab.public-origin}") String publicOrigin) {
        this.users = users;
        this.internProfiles = internProfiles;
        this.tokens = tokens;
        this.mailDelivery = mailDelivery;
        this.passwords = passwords;
        this.clock = clock;
        this.transactions = transactions;
        this.sessions = sessions;
        this.publicOrigin = normalizeOrigin(publicOrigin);
    }

    /**
     * Creates a pending immutable-role account and sends its one-time activation link immediately.
     * Only the SHA-256 token hash is persisted; the raw token remains in memory for this delivery call. If delivery
     * fails, the token is invalidated in a separate transaction and the pending account remains for audit history.
     *
     * @param command validated account details
     * @param adminId active Admin creating the account
     * @return created account identifier and whether activation delivery succeeded
     */
    public AccountCreation create(CreateAccountCommand command, long adminId) {
        ValidatedAccount account = validate(command);
        if (!mailDelivery.isAvailable()) {
            throw new IllegalStateException("Active SMTP configuration is required for account creation");
        }

        String rawToken = newRawToken();
        byte[] tokenHash = sha256(rawToken);
        PendingActivation pending = transactions.execute(status -> createPending(account, adminId, tokenHash));
        if (pending == null) {
            throw new IllegalStateException("Account creation did not complete");
        }

        try {
            mailDelivery.send(
                    account.email(),
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
     * Moves an active Intern's internship from {@code NOT_STARTED} to {@code ACTIVE} once the configured
     * internship start date has arrived in the application's business timezone.
     *
     * @param internUserId Intern account whose internship should start
     * @param adminId active Admin authorizing the state transition
     * @throws IllegalStateException when the internship start date has not arrived
     */
    @Transactional
    public void activateInternship(long internUserId, long adminId) {
        AppUser admin = users.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("Admin not found"));
        requireActiveAdmin(admin);

        AppUser intern = users.findForUpdateById(internUserId)
                .orElseThrow(() -> new IllegalArgumentException("Intern not found"));
        if (intern.getGlobalRole() != GlobalRole.INTERN || intern.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("An active Intern account is required");
        }
        InternProfile profile = internProfiles.findForUpdateByUserId(internUserId)
                .orElseThrow(() -> new IllegalArgumentException("Intern profile not found"));
        if (businessDate().isBefore(profile.getInternshipStartDate())) {
            throw new IllegalStateException("Internship cannot activate before its start date");
        }
        profile.activate(clock.instant());
    }

    /**
     * Activates every due active Intern exactly once using a post-lock server timestamp.
     *
     * <p>Candidate selection is intentionally non-locking and may use a stale selection date; each candidate is then
     * rechecked while locking its account before its profile. Only after both locks are held does this method read one
     * {@link Instant}, derive the current server business date, and apply the inclusive start/end guard. This stable
     * order makes repeated scheduler runs and request-time activation safe when they race, and prevents a lock wait
     * from activating a profile after its configured window has closed.</p>
     *
     * @return number of profiles transitioned from {@code NOT_STARTED} to {@code ACTIVE}
     */
    @Transactional
    public int activateDueInternships() {
        LocalDate selectionDate = businessDate();
        int activated = 0;
        for (Long userId : internProfiles.findDueUserIds(
                GlobalRole.INTERN, AccountStatus.ACTIVE, InternshipStatus.NOT_STARTED, selectionDate)) {
            AppUser intern = users.findForUpdateById(userId).orElse(null);
            if (intern == null || intern.getGlobalRole() != GlobalRole.INTERN
                    || intern.getAccountStatus() != AccountStatus.ACTIVE) {
                continue;
            }
            InternProfile profile = internProfiles.findForUpdateByUserId(userId).orElse(null);
            if (profile == null) {
                continue;
            }
            Instant now = clock.instant();
            LocalDate currentDate = now.atZone(clock.getZone()).toLocalDate();
            if (profile.getInternshipStatus() == InternshipStatus.NOT_STARTED
                    && !currentDate.isBefore(profile.getInternshipStartDate())
                    && !currentDate.isAfter(profile.getInternshipEndDate())) {
                profile.activate(now);
                activated++;
            }
        }
        return activated;
    }

    /**
     * Completes an Intern after Project and Task producers confirm terminal readiness.
     *
     * @param internUserId Intern account identifier
     * @param adminId active Admin performing the action
     * @param guard producer-owned current-Leader and unfinished-Task facts
     * @throws IllegalArgumentException when the account, role, or guard is invalid
     * @throws IllegalStateException when readiness or the account/profile state rejects completion
     */
    @Transactional
    public void completeInternship(long internUserId, long adminId, InternshipLifecycleGuard guard) {
        requireTerminalGuard(guard);
        requireActiveAdminId(adminId);
        AppUser intern = lockInternAccount(internUserId);
        if (intern.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException("Only an active Intern account can complete");
        }
        InternProfile profile = lockInternProfile(internUserId);
        profile.complete(clock.instant());
    }

    /**
     * Withdraws an Intern after Project and Task producers confirm terminal readiness and expires all sessions.
     *
     * @param internUserId Intern account identifier
     * @param adminId active Admin performing the action
     * @param guard producer-owned current-Leader and unfinished-Task facts
     * @throws IllegalArgumentException when the account, role, or guard is invalid
     * @throws IllegalStateException when readiness or the account/profile state rejects withdrawal
     */
    @Transactional
    public void withdrawInternship(long internUserId, long adminId, InternshipLifecycleGuard guard) {
        requireTerminalGuard(guard);
        requireActiveAdminId(adminId);
        AppUser intern = lockInternAccount(internUserId);
        if (intern.getAccountStatus() == AccountStatus.PENDING_ACTIVATION
                || intern.getAccountStatus() == AccountStatus.DEACTIVATED) {
            throw new IllegalStateException("Only an activated Intern account can withdraw");
        }
        InternProfile profile = lockInternProfile(internUserId);
        profile.withdraw(clock.instant());
        intern.deactivate(clock.instant());
        invalidateSessions(intern);
    }

    /**
     * Summarizes current account and internship state for dashboard consumers.
     *
     * @return active account, pending activation, and active internship counts
     */
    @Transactional(readOnly = true)
    public AccountSummary summary() {
        return new AccountSummary(
                users.countByAccountStatus(AccountStatus.ACTIVE),
                users.countByAccountStatus(AccountStatus.PENDING_ACTIVATION),
                internProfiles.countByInternshipStatus(InternshipStatus.ACTIVE));
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
        return users.findById(userId).map(AccountService::identity)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
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
     * Checks whether the account and its internship are both currently active.
     *
     * @param userId account identifier
     * @return {@code true} only for an active Intern with an active internship
     */
    @Transactional(readOnly = true)
    public boolean isEligibleIntern(long userId) {
        return users.findById(userId)
                .filter(user -> user.getGlobalRole() == GlobalRole.INTERN)
                .filter(user -> user.getAccountStatus() == AccountStatus.ACTIVE)
                .filter(user -> internProfiles.existsByUserIdAndInternshipStatus(
                        user.getId(), InternshipStatus.ACTIVE))
                .isPresent();
    }

    /**
     * Checks active Intern eligibility on an inclusive internship date range.
     *
     * @param userId account identifier
     * @param workDate server-derived business date being authorized
     * @return {@code true} only when account and internship are active and the date is within the internship
     * @throws IllegalArgumentException when {@code workDate} is {@code null}
     */
    @Transactional(readOnly = true)
    public boolean isEligibleIntern(long userId, LocalDate workDate) {
        if (workDate == null) {
            throw new IllegalArgumentException("Work date is required");
        }
        return users.findById(userId)
                .filter(user -> user.getGlobalRole() == GlobalRole.INTERN)
                .filter(user -> user.getAccountStatus() == AccountStatus.ACTIVE)
                .filter(user -> internProfiles
                        .existsByUserIdAndInternshipStatusAndInternshipStartDateLessThanEqualAndInternshipEndDateGreaterThanEqual(
                                user.getId(), InternshipStatus.ACTIVE, workDate, workDate))
                .isPresent();
    }

    /**
     * Locks the account and Intern profile, then exposes their non-persistence work-window snapshot.
     *
     * <p>The pessimistic locks remain held until the surrounding transaction ends. A consumer that performs an
     * attendance or Task mutation in its own transaction should call this method inside that transaction; callers
     * that only need a read decision may use the existing non-locking eligibility methods.</p>
     *
     * @param userId Intern account identifier
     * @param businessDate requested work/allocation date to evaluate in the returned DTO; it does not control
     *        request-time lifecycle activation
     * @return immutable state and inclusive date-window snapshot
     * @throws IllegalArgumentException when the account is missing, not an Intern, has no profile, or the date is
     *         null
     */
    @Transactional
    public InternWorkWindow lockedInternWorkWindow(long userId, LocalDate businessDate) {
        if (userId <= 0) {
            throw new IllegalArgumentException("Intern user ID must be positive");
        }
        if (businessDate == null) {
            throw new IllegalArgumentException("Business date is required");
        }
        AppUser user = users.findForUpdateById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Intern account not found"));
        if (user.getGlobalRole() != GlobalRole.INTERN) {
            throw new IllegalArgumentException("An Intern account is required");
        }
        InternProfile profile = internProfiles.findForUpdateByUserId(userId)
                .orElseThrow(() -> new IllegalArgumentException("Intern profile not found"));
        Instant now = clock.instant();
        LocalDate currentDate = now.atZone(clock.getZone()).toLocalDate();
        if (user.getAccountStatus() == AccountStatus.ACTIVE
                && profile.getInternshipStatus() == InternshipStatus.NOT_STARTED
                && !currentDate.isBefore(profile.getInternshipStartDate())
                && !currentDate.isAfter(profile.getInternshipEndDate())) {
            profile.activate(now);
        }
        return new InternWorkWindow(
                userId,
                businessDate,
                profile.getInternshipStartDate(),
                profile.getInternshipEndDate(),
                user.getAccountStatus(),
                profile.getInternshipStatus());
    }

    /**
     * Locks all accounts participating in one Project mutation in ascending account-ID order, then locks each
     * requested Intern profile in that same order. Due NOT_STARTED profiles are activated only after all requested
     * rows are locked, using one server timestamp and its server-local date; requested Project dates never drive
     * this guard.
     *
     * <p>The returned list is ordered by the same IDs regardless of input order, and duplicate IDs are collapsed.
     * Account rows are all locked before any profile row. Because the method joins an existing transaction when
     * called by Project, those pessimistic locks remain held through the caller's invitation or membership-exit
     * authorization and mutation. Non-Intern actor accounts have an empty profile status; completed, withdrawn,
     * pending, or locked Interns are returned as explicit ineligible facts so the consumer can enforce role-specific
     * rules without importing Account persistence.</p>
     *
     * @param userIds account IDs participating in one mutation decision, including the actor and affected Interns
     * @return immutable ascending snapshots with account/profile locks retained by the surrounding transaction
     * @throws IllegalArgumentException when the collection or an ID is malformed, an account or Intern profile is
     *         missing
     */
    @Transactional
    public List<LockedAccountMutationEligibility> lockedAccountMutationEligibility(Collection<Long> userIds) {
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
        Map<Long, AppUser> lockedUsers = new LinkedHashMap<>(orderedIds.size());
        for (Long userId : orderedIds) {
            AppUser user = users.findForUpdateById(userId)
                    .orElseThrow(() -> new IllegalArgumentException("Account not found"));
            lockedUsers.put(userId, user);
        }
        Map<Long, InternProfile> lockedProfiles = new LinkedHashMap<>();
        for (Long userId : orderedIds) {
            AppUser user = lockedUsers.get(userId);
            if (user.getGlobalRole() == GlobalRole.INTERN) {
                lockedProfiles.put(userId, internProfiles.findForUpdateByUserId(userId)
                        .orElseThrow(() -> new IllegalArgumentException("Intern profile not found")));
            }
        }
        Instant now = clock.instant();
        LocalDate currentDate = now.atZone(clock.getZone()).toLocalDate();
        for (Map.Entry<Long, InternProfile> entry : lockedProfiles.entrySet()) {
            AppUser user = lockedUsers.get(entry.getKey());
            InternProfile profile = entry.getValue();
            if (user.getAccountStatus() == AccountStatus.ACTIVE
                    && profile.getInternshipStatus() == InternshipStatus.NOT_STARTED
                    && !currentDate.isBefore(profile.getInternshipStartDate())
                    && !currentDate.isAfter(profile.getInternshipEndDate())) {
                profile.activate(now);
            }
        }
        List<LockedAccountMutationEligibility> snapshots = new ArrayList<>(orderedIds.size());
        for (Long userId : orderedIds) {
            AppUser user = lockedUsers.get(userId);
            var internshipStatus = user.getGlobalRole() == GlobalRole.INTERN
                    ? Optional.of(lockedProfiles.get(userId).getInternshipStatus())
                    : Optional.<InternshipStatus>empty();
            snapshots.add(new LockedAccountMutationEligibility(
                    user.getId(), user.getGlobalRole(), user.getAccountStatus(), internshipStatus));
        }
        return List.copyOf(snapshots);
    }

    /**
     * Lists non-secret Intern selection options eligible on an explicit business date.
     * The result requires active account and internship states plus inclusive internship dates, but it does not
     * authorize a consuming Project operation; that operation must recheck its own ownership and membership rules.
     *
     * @param businessDate server-derived business date to evaluate inclusively
     * @return deterministic options ordered by display name, student code, then account ID
     * @throws IllegalArgumentException when {@code businessDate} is {@code null}
     */
    @Transactional(readOnly = true)
    public List<EligibleInternOption> eligibleInternOptions(LocalDate businessDate) {
        if (businessDate == null) {
            throw new IllegalArgumentException("Business date is required");
        }
        return internProfiles.findEligibleInternOptions(
                GlobalRole.INTERN, AccountStatus.ACTIVE, InternshipStatus.ACTIVE, businessDate);
    }

    /**
     * Resolves the cross-feature identity of a currently eligible Intern.
     *
     * @param userId account identifier
     * @return non-secret account identity
     * @throws IllegalArgumentException when the account or internship is not active
     */
    @Transactional(readOnly = true)
    public AccountIdentity requireEligibleIntern(long userId) {
        if (!isEligibleIntern(userId)) {
            throw new IllegalArgumentException("An active Intern account and internship are required");
        }
        return requireIdentityById(userId);
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

    private AppUser lockInternAccount(long internUserId) {
        AppUser intern = users.findForUpdateById(internUserId)
                .orElseThrow(() -> new IllegalArgumentException("Intern account not found"));
        if (intern.getGlobalRole() != GlobalRole.INTERN) {
            throw new IllegalArgumentException("An Intern account is required");
        }
        return intern;
    }

    private InternProfile lockInternProfile(long internUserId) {
        return internProfiles.findForUpdateByUserId(internUserId)
                .orElseThrow(() -> new IllegalArgumentException("Intern profile not found"));
    }

    private static void requireTerminalGuard(InternshipLifecycleGuard guard) {
        if (guard == null) {
            throw new IllegalArgumentException("Internship lifecycle guard is required");
        }
        if (guard.currentLeader()) {
            throw new IllegalStateException("Intern is still a current Leader");
        }
        if (guard.unfinishedTaskCount() > 0) {
            throw new IllegalStateException("Intern still owns unfinished Tasks");
        }
    }

    private LocalDate businessDate() {
        return clock.instant().atZone(clock.getZone()).toLocalDate();
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
        sessions.getAllPrincipals().stream()
                .filter(principal -> principalMatches(principal, user.getEmail()))
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

    private PendingActivation createPending(ValidatedAccount account, long adminId, byte[] tokenHash) {
        AppUser admin = users.findForUpdateById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("Admin not found"));
        requireActiveAdmin(admin);

        var now = clock.instant();
        AppUser user = users.save(AppUser.pending(
                account.email(), account.displayName(), account.role(), admin, now));
        if (account.role() == GlobalRole.INTERN) {
            internProfiles.save(InternProfile.notStarted(
                    user.getId(), account.studentCode(), account.internshipStart(), account.internshipEnd(), now));
        }
        UserActionToken token = tokens.save(UserActionToken.activation(
                user.getId(), tokenHash, now.plus(ACTIVATION_LIFETIME), admin.getId(), now));
        return new PendingActivation(user.getId(), user.getEmail(), token.getId());
    }

    private String activationLink(String rawToken) {
        return publicOrigin + "/activate?token=" + rawToken;
    }

    private static ValidatedAccount validate(CreateAccountCommand command) {
        if (command == null || command.role() == null) {
            throw new IllegalArgumentException("Account role is required");
        }
        String email = BootstrapService.normalizeEmail(command.email());
        String displayName = requireText(command.displayName(), "Display name");
        if (command.role() != GlobalRole.INTERN) {
            if (command.studentCode() != null || command.internshipStart() != null || command.internshipEnd() != null) {
                throw new IllegalArgumentException("Internship fields are allowed only for Intern accounts");
            }
            return new ValidatedAccount(email, displayName, command.role(), null, null, null);
        }

        String studentCode = requireText(command.studentCode(), "Student code");
        if (command.internshipStart() == null || command.internshipEnd() == null
                || command.internshipEnd().isBefore(command.internshipStart())) {
            throw new IllegalArgumentException("A valid internship date range is required");
        }
        return new ValidatedAccount(
                email, displayName, command.role(), studentCode, command.internshipStart(), command.internshipEnd());
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

    private record ValidatedAccount(
            String email,
            String displayName,
            GlobalRole role,
            String studentCode,
            LocalDate internshipStart,
            LocalDate internshipEnd) {
    }

    private record PendingActivation(long userId, String email, long tokenId) {
    }

    private record PendingReset(long userId, String email, long tokenId) {
    }
}
