package com.lab.labtimesheet.feature.account.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;

import com.lab.labtimesheet.feature.account.model.AccountStatus;
import com.lab.labtimesheet.feature.account.model.GlobalRole;
import com.lab.labtimesheet.feature.account.model.InternshipStatus;
import com.lab.labtimesheet.feature.account.model.TokenPurpose;
import com.lab.labtimesheet.feature.account.model.dto.AccountCreation;
import com.lab.labtimesheet.feature.account.model.dto.AccountIdentity;
import com.lab.labtimesheet.feature.account.model.dto.AccountSummary;
import com.lab.labtimesheet.feature.account.model.dto.CreateAccountCommand;
import com.lab.labtimesheet.feature.account.model.dto.EligibleInternOption;
import com.lab.labtimesheet.feature.account.model.entity.AppUser;
import com.lab.labtimesheet.feature.account.model.entity.InternProfile;
import com.lab.labtimesheet.feature.account.model.entity.UserActionToken;
import com.lab.labtimesheet.feature.account.repository.AppUserRepository;
import com.lab.labtimesheet.feature.account.repository.InternProfileRepository;
import com.lab.labtimesheet.feature.account.repository.UserActionTokenRepository;
import com.lab.labtimesheet.feature.integration.service.MailDeliveryService;
import org.springframework.beans.factory.annotation.Value;
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
    private static final SecureRandom TOKEN_RANDOM = new SecureRandom();

    private final AppUserRepository users;
    private final InternProfileRepository internProfiles;
    private final UserActionTokenRepository tokens;
    private final MailDeliveryService mailDelivery;
    private final PasswordEncoder passwords;
    private final Clock clock;
    private final TransactionTemplate transactions;
    private final String publicOrigin;

    AccountService(
            AppUserRepository users,
            InternProfileRepository internProfiles,
            UserActionTokenRepository tokens,
            MailDeliveryService mailDelivery,
            PasswordEncoder passwords,
            Clock clock,
            TransactionTemplate transactions,
            @Value("${lab.public-origin}") String publicOrigin) {
        this.users = users;
        this.internProfiles = internProfiles;
        this.tokens = tokens;
        this.mailDelivery = mailDelivery;
        this.passwords = passwords;
        this.clock = clock;
        this.transactions = transactions;
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
