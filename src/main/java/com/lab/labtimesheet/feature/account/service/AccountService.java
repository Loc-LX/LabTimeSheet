package com.lab.labtimesheet.feature.account.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import com.lab.labtimesheet.feature.account.model.AccountStatus;
import com.lab.labtimesheet.feature.account.model.GlobalRole;
import com.lab.labtimesheet.feature.account.model.InternshipStatus;
import com.lab.labtimesheet.feature.account.model.TokenPurpose;
import com.lab.labtimesheet.feature.account.model.dto.AccountAdminDetail;
import com.lab.labtimesheet.feature.account.model.dto.AccountAdminListItem;
import com.lab.labtimesheet.feature.account.model.dto.AccountCreation;
import com.lab.labtimesheet.feature.account.model.dto.AccountIdentity;
import com.lab.labtimesheet.feature.account.model.dto.AccountSummary;
import com.lab.labtimesheet.feature.account.model.dto.CreateAccountCommand;
import com.lab.labtimesheet.feature.account.model.dto.EligibleInternOption;
import com.lab.labtimesheet.feature.account.model.entity.AccountAdminEditEvent;
import com.lab.labtimesheet.feature.account.model.entity.AccountAdminEditField;
import com.lab.labtimesheet.feature.account.model.entity.AppUser;
import com.lab.labtimesheet.feature.account.model.entity.InternProfile;
import com.lab.labtimesheet.feature.account.model.entity.UserActionToken;
import com.lab.labtimesheet.feature.account.repository.AccountAdminEditEventRepository;
import com.lab.labtimesheet.feature.account.repository.AppUserRepository;
import com.lab.labtimesheet.feature.account.repository.InternProfileRepository;
import com.lab.labtimesheet.feature.account.repository.UserActionTokenRepository;
import com.lab.labtimesheet.feature.integration.service.MailDeliveryService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
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
    private static final Duration RESET_LIFETIME = Duration.ofMinutes(30);
    private static final SecureRandom TOKEN_RANDOM = new SecureRandom();

    private final AppUserRepository users;
    private final InternProfileRepository internProfiles;
    private final UserActionTokenRepository tokens;
    private final AccountAdminEditEventRepository editEvents;
    private final MailDeliveryService mailDelivery;
    private final PasswordEncoder passwords;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private final SessionRegistry sessionRegistry;
    private final String publicOrigin;

    AccountService(
            AppUserRepository users,
            InternProfileRepository internProfiles,
            UserActionTokenRepository tokens,
            AccountAdminEditEventRepository editEvents,
            MailDeliveryService mailDelivery,
            PasswordEncoder passwords,
            Clock clock,
            TransactionTemplate transactions,
            SessionRegistry sessionRegistry,
            @Value("${lab.public-origin}") String publicOrigin) {
        this.users = users;
        this.internProfiles = internProfiles;
        this.tokens = tokens;
        this.editEvents = editEvents;
        this.mailDelivery = mailDelivery;
        this.passwords = passwords;
        this.clock = clock;
        this.transactions = transactions;
        this.sessionRegistry = sessionRegistry;
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

        UserActionToken token = tokens.findForUpdateByHashAndPurpose(sha256(rawToken), TokenPurpose.ACTIVATION)
                .orElse(null);
        var now = clock.instant();
        if (token == null || !token.isUsableAt(now)) {
            return false;
        }

        AppUser user = users.findForUpdateById(token.getUserId()).orElse(null);
        if (user == null || user.getAccountStatus() != AccountStatus.PENDING_ACTIVATION) {
            return false;
        }
        user.activate(passwords.encode(password), now);
        token.markUsed(now);
        return true;
    }

    /**
     * Reissues a pending account's activation email, invalidating the prior live token so only the newest link can
     * activate the account. The invalidation and issuance share one transaction, and a delivery failure invalidates
     * the fresh token without affecting the pending account row.
     *
     * @param userId pending account being resent
     * @param adminId active Admin authorizing the resend
     * @return account identifier and whether the replacement email was accepted by the configured SMTP boundary
     * @throws IllegalArgumentException when the account is not pending or the Admin is not active
     * @throws IllegalStateException when no active SMTP configuration exists or the workflow cannot complete
     */
    public AccountCreation resendActivation(long userId, long adminId) {
        long verifiedAdminId = requireActiveAdminId(adminId);
        AppUser user = users.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        if (user.getAccountStatus() != AccountStatus.PENDING_ACTIVATION) {
            throw new IllegalArgumentException("Activation can be resent only for a pending account");
        }
        if (!mailDelivery.isAvailable()) {
            throw new IllegalStateException("Active SMTP configuration is required to resend activation");
        }

        String rawToken = newRawToken();
        byte[] tokenHash = sha256(rawToken);
        PendingActivation pending = transactions.execute(status -> {
            var now = clock.instant();
            tokens.invalidateLive(userId, TokenPurpose.ACTIVATION, now);
            UserActionToken token = tokens.save(UserActionToken.activation(
                    userId, tokenHash, now.plus(ACTIVATION_LIFETIME), verifiedAdminId, now));
            return new PendingActivation(userId, token.getId());
        });
        if (pending == null) {
            throw new IllegalStateException("Activation resend did not complete");
        }

        try {
            mailDelivery.send(
                    user.getEmail(),
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
     * Requests a password-reset link for an active account. Unknown, pending, and locked accounts receive the same
     * generic no-op so an unauthenticated caller cannot enumerate accounts; no token row is written and no message
     * is sent for them. A live reset token is invalidated before its replacement is issued, and a delivery failure
     * invalidates the fresh token.
     *
     * @param email account email as supplied by the user
     * @return {@code true} when a reset link was accepted by the configured SMTP boundary
     */
    public boolean requestPasswordReset(String email) {
        if (!mailDelivery.isAvailable()) {
            return false;
        }
        AppUser user = users.findByNormalizedEmail(BootstrapService.normalizeEmail(email)).orElse(null);
        if (user == null || user.getAccountStatus() != AccountStatus.ACTIVE) {
            return false;
        }

        String rawToken = newRawToken();
        byte[] tokenHash = sha256(rawToken);
        var issued = transactions.execute(status -> {
            var now = clock.instant();
            tokens.invalidateLive(user.getId(), TokenPurpose.PASSWORD_RESET, now);
            UserActionToken token = tokens.save(UserActionToken.passwordReset(
                    user.getId(), tokenHash, now.plus(RESET_LIFETIME), user.getId(), now));
            return token.getId();
        });
        if (issued == null) {
            throw new IllegalStateException("Password reset request did not complete");
        }

        try {
            mailDelivery.send(
                    user.getEmail(),
                    "Reset your Lab Timesheet password",
                    "Reset your password using this single-use link:\n" + resetLink(rawToken));
            return true;
        } catch (RuntimeException deliveryFailure) {
            transactions.executeWithoutResult(status -> tokens.findForUpdateById(issued)
                    .orElseThrow(() -> new IllegalStateException("Password reset token is missing"))
                    .invalidate(clock.instant()));
            return false;
        }
    }

    /**
     * Consumes a valid, unexpired password-reset bearer token once, replaces the active account's credentials, and
     * expires the account's existing authenticated sessions.
     *
     * @param rawToken raw token received from the reset link
     * @param password new password, containing 12 through 128 characters
     * @return {@code true} when the reset completed; {@code false} for an invalid, expired, used, or stale token
     */
    @Transactional
    public boolean resetPassword(String rawToken, String password) {
        BootstrapService.requirePassword(password);
        if (rawToken == null || rawToken.isBlank()) {
            return false;
        }

        UserActionToken token = tokens.findForUpdateByHashAndPurpose(sha256(rawToken), TokenPurpose.PASSWORD_RESET)
                .orElse(null);
        var now = clock.instant();
        if (token == null || !token.isUsableAt(now)) {
            return false;
        }

        AppUser user = users.findForUpdateById(token.getUserId()).orElse(null);
        if (user == null || user.getAccountStatus() != AccountStatus.ACTIVE) {
            return false;
        }
        user.resetPassword(passwords.encode(password), now);
        token.markUsed(now);
        expireSessions(user.getEmail());
        return true;
    }

    /**
     * Reports whether a supplied raw reset token still maps to a live, unexpired password-reset token without
     * consuming it, so the reset form can render a direct error instead of waiting for submission.
     *
     * @param rawToken raw token received from the reset link
     * @return {@code true} when the token is present and currently usable
     */
    @Transactional(readOnly = true)
    public boolean isResetTokenUsable(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return false;
        }
        UserActionToken token = tokens.findByHashAndPurpose(sha256(rawToken), TokenPurpose.PASSWORD_RESET)
                .orElse(null);
        return token != null && token.isUsableAt(clock.instant());
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
        if (LocalDate.now(clock).isBefore(profile.getInternshipStartDate())) {
            throw new IllegalStateException("Internship cannot activate before its start date");
        }
        profile.activate(clock.instant());
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
     * Lists the non-secret Admin account rows, optionally restricted to one immutable role.
     *
     * @param role role filter, or {@code null} to include every account
     * @return deterministic listing rows without secret data
     */
    @Transactional(readOnly = true)
    public List<AccountAdminListItem> listAccounts(GlobalRole role) {
        return users.findAdminListItems(role);
    }

    /**
     * Resolves the non-secret Admin account-detail projection, including lifecycle timestamps and
     * the owning internship when the account role is Intern.
     *
     * @param userId account identifier
     * @return complete non-secret detail projection
     * @throws IllegalArgumentException when the account does not exist
     */
    @Transactional(readOnly = true)
    public AccountAdminDetail requireAccountDetail(long userId) {
        AppUser user = users.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        String createdByEmail = user.getCreatedBy() == null ? null : user.getCreatedBy().getEmail();
        InternProfile profile = internProfiles.findById(userId).orElse(null);
        return new AccountAdminDetail(
                user.getId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getGlobalRole(),
                user.getAccountStatus(),
                user.getActivatedAt(),
                user.getLockedAt(),
                user.getDeactivatedAt(),
                user.getLastLoginAt(),
                createdByEmail,
                profile == null ? null : profile.getStudentCode(),
                profile == null ? null : profile.getInternshipStartDate(),
                profile == null ? null : profile.getInternshipEndDate(),
                profile == null ? null : profile.getInternshipStatus(),
                user.getVersion(),
                profile == null ? null : profile.getVersion());
    }

    /**
     * Applies an Admin-authorized edit to the account's identity and, for Intern accounts, its profile
     * fields. The immutable role cannot change, duplicates are rejected by the database unique indexes,
     * and one append-only audit row is written per changed field naming the authorizing Admin. When the
     * optimistic-lock versions rendered to the edit form are stale, the whole edit is rejected before any
     * field or audit row changes, so a concurrent Admin edit cannot be silently overwritten.
     *
     * @param userId account being edited
     * @param adminId active Admin authorizing the edit
     * @param expectedUserVersion optimistic-lock version of the account rendered to the edit form
     * @param expectedProfileVersion optimistic-lock version of the Intern profile, {@code null} for non-Intern
     * @param email replacement email, normalized before comparison
     * @param displayName replacement display name
     * @param studentCode replacement Intern student code, required only for Intern accounts
     * @param internshipStart replacement inclusive Intern start date, required only for Intern accounts
     * @param internshipEnd replacement inclusive Intern end date, required only for Intern accounts
     * @return refreshed detail with advanced optimistic-lock versions
     * @throws IllegalArgumentException when the account is missing, the authorizer is not an active Admin,
     *         Intern fields are supplied for a non-Intern account, or Intern details are incomplete or invalid
     * @throws IllegalStateException when the supplied versions are stale
     */
    @Transactional
    public AccountAdminDetail updateAccountAdminFields(
            long userId, long adminId, long expectedUserVersion, Long expectedProfileVersion,
            String email, String displayName, String studentCode,
            LocalDate internshipStart, LocalDate internshipEnd) {
        AppUser admin = users.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("Admin not found"));
        requireActiveAdmin(admin);

        AppUser user = users.findForUpdateById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
        if (user.getVersion() != expectedUserVersion) {
            throw new IllegalStateException("Account was changed by another request");
        }

        String normalizedEmail = BootstrapService.normalizeEmail(email);
        String normalizedDisplayName = requireText(displayName, "Display name");
        var now = clock.instant();
        var changes = new ArrayList<AccountAdminEditEvent>();

        if (!user.getEmail().equals(normalizedEmail)) {
            changes.add(AccountAdminEditEvent.of(user.getId(), admin.getId(), AccountAdminEditField.EMAIL,
                    user.getEmail(), normalizedEmail, now));
            user.changeEmail(normalizedEmail, now);
        }
        if (!user.getDisplayName().equals(normalizedDisplayName)) {
            changes.add(AccountAdminEditEvent.of(user.getId(), admin.getId(), AccountAdminEditField.DISPLAY_NAME,
                    user.getDisplayName(), normalizedDisplayName, now));
            user.changeDisplayName(normalizedDisplayName, now);
        }

        if (user.getGlobalRole() == GlobalRole.INTERN) {
            String normalizedCode = requireText(studentCode, "Student code");
            if (internshipStart == null || internshipEnd == null || internshipEnd.isBefore(internshipStart)) {
                throw new IllegalArgumentException("A valid internship date range is required");
            }
            if (expectedProfileVersion == null) {
                throw new IllegalArgumentException("Internship profile version is required");
            }
            InternProfile profile = internProfiles.findForUpdateByUserId(user.getId())
                    .orElseThrow(() -> new IllegalArgumentException("Intern profile not found"));
            if (profile.getVersion() != expectedProfileVersion) {
                throw new IllegalStateException("Account was changed by another request");
            }
            boolean codeChanged = !profile.getStudentCode().equals(normalizedCode);
            boolean startChanged = !profile.getInternshipStartDate().equals(internshipStart);
            boolean endChanged = !profile.getInternshipEndDate().equals(internshipEnd);
            if (codeChanged) {
                changes.add(AccountAdminEditEvent.of(user.getId(), admin.getId(), AccountAdminEditField.STUDENT_CODE,
                        profile.getStudentCode(), normalizedCode, now));
            }
            if (startChanged) {
                changes.add(AccountAdminEditEvent.of(user.getId(), admin.getId(),
                        AccountAdminEditField.INTERNSHIP_START,
                        profile.getInternshipStartDate().toString(), internshipStart.toString(), now));
            }
            if (endChanged) {
                changes.add(AccountAdminEditEvent.of(user.getId(), admin.getId(), AccountAdminEditField.INTERNSHIP_END,
                        profile.getInternshipEndDate().toString(), internshipEnd.toString(), now));
            }
            if (codeChanged || startChanged || endChanged) {
                profile.updateFields(normalizedCode, internshipStart, internshipEnd, now);
            }
        } else if (expectedProfileVersion != null || studentCode != null || internshipStart != null
                || internshipEnd != null) {
            throw new IllegalArgumentException("Internship fields are allowed only for Intern accounts");
        }

        if (!changes.isEmpty()) {
            editEvents.saveAll(changes);
        }
        return requireAccountDetail(user.getId());
    }

    /**
     * Locks an active account and expires its existing authenticated sessions per ACC-018.
     *
     * @param userId account to lock
     * @param adminId active Admin authorizing the transition
     * @throws IllegalArgumentException when the account is missing or not active
     */
    @Transactional
    public void lockAccount(long userId, long adminId) {
        requireActiveAdmin(users.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("Admin not found")));
        AppUser user = requireMutable(userId);
        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("Only an active account can be locked");
        }
        user.lock(clock.instant());
        expireSessions(user.getEmail());
    }

    /**
     * Unlocks a locked account, preserving its role and credentials per ACC-015.
     *
     * @param userId account to unlock
     * @param adminId active Admin authorizing the transition
     * @throws IllegalArgumentException when the account is missing or not locked
     */
    @Transactional
    public void unlockAccount(long userId, long adminId) {
        requireActiveAdmin(users.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("Admin not found")));
        AppUser user = requireMutable(userId);
        if (user.getAccountStatus() != AccountStatus.LOCKED) {
            throw new IllegalArgumentException("Only a locked account can be unlocked");
        }
        user.unlock(clock.instant());
    }

    /**
     * Deactivates an active account and expires its existing authenticated sessions per ACC-018.
     * Historical attribution remains visible through the identity and detail projections.
     *
     * @param userId account to deactivate
     * @param adminId active Admin authorizing the transition
     * @throws IllegalArgumentException when the account is missing or not active
     */
    @Transactional
    public void deactivateAccount(long userId, long adminId) {
        requireActiveAdmin(users.findById(adminId)
                .orElseThrow(() -> new IllegalArgumentException("Admin not found")));
        AppUser user = requireMutable(userId);
        if (user.getAccountStatus() != AccountStatus.ACTIVE) {
            throw new IllegalArgumentException("Only an active account can be deactivated");
        }
        user.deactivate(clock.instant());
        expireSessions(user.getEmail());
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

    private AppUser requireMutable(long userId) {
        return users.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("Account not found"));
    }

    /**
     * Expires every registered Spring Security session whose principal matches the account email,
     * so a locked or deactivated account cannot keep using existing authenticated sessions.
     *
     * @param email normalized account email used as the authentication principal name
     */
    private void expireSessions(String email) {
        for (Object principal : sessionRegistry.getAllPrincipals()) {
            String principalName = principal instanceof UserDetails details
                    ? details.getUsername()
                    : String.valueOf(principal);
            if (email.equalsIgnoreCase(principalName)) {
                for (SessionInformation information : sessionRegistry.getAllSessions(principal, false)) {
                    information.expireNow();
                }
            }
        }
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
        return new PendingActivation(user.getId(), token.getId());
    }

    private String activationLink(String rawToken) {
        return publicOrigin + "/activate?token=" + rawToken;
    }

    private String resetLink(String rawToken) {
        return publicOrigin + "/reset-password?token=" + rawToken;
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

    private record PendingActivation(long userId, long tokenId) {
    }
}
