package com.lab.labtimesheet.feature.account.service;

import java.time.Clock;
import java.util.Locale;

import com.lab.labtimesheet.feature.account.model.entity.AppUser;
import com.lab.labtimesheet.feature.account.model.entity.SystemState;
import com.lab.labtimesheet.feature.account.repository.AppUserRepository;
import com.lab.labtimesheet.feature.account.repository.SystemStateRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Performs the one-time installation bootstrap guarded by the locked singleton system-state row.
 * Successful creation persists the first active Admin and initialization marker atomically.
 */
@Service
public class BootstrapService {
    private final SystemStateRepository systemStates;
    private final AppUserRepository users;
    private final PasswordEncoder passwords;
    private final Clock clock;

    BootstrapService(SystemStateRepository systemStates, AppUserRepository users, PasswordEncoder passwords,
            Clock clock) {
        this.systemStates = systemStates;
        this.users = users;
        this.passwords = passwords;
        this.clock = clock;
    }

    /**
     * Creates the first active Admin exactly once.
     *
     * @param email first Admin email, normalized by trimming and lower-casing
     * @param displayName first Admin display name
     * @param password first Admin password, containing 12 through 128 characters
     * @return {@link BootstrapOutcome#CREATED} or {@link BootstrapOutcome#ALREADY_INITIALIZED}
     */
    @Transactional
    public BootstrapOutcome bootstrap(String email, String displayName, String password) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedName = requireText(displayName, "Display name");
        requirePassword(password);

        SystemState state = systemStates.findSingletonForUpdate()
                .orElseThrow(() -> new IllegalStateException("System state is missing"));
        if (state.isInitialized()) {
            return BootstrapOutcome.ALREADY_INITIALIZED;
        }
        var now = clock.instant();
        AppUser admin = users.save(AppUser.bootstrapAdmin(
                normalizedEmail, normalizedName, passwords.encode(password), now));
        state.initialize(admin, now);
        return BootstrapOutcome.CREATED;
    }

    /**
     * Reads the durable installation state.
     *
     * @return {@code true} after the first Admin has been committed
     */
    @Transactional(readOnly = true)
    public boolean isInitialized() {
        return systemStates.findById((short) 1).map(SystemState::isInitialized).orElse(false);
    }

    /**
     * Produces the canonical account lookup form of an email address.
     *
     * @param email email supplied at a trust boundary
     * @return trimmed, locale-independent lower-case email
     */
    public static String normalizeEmail(String email) {
        return requireText(email, "Email").toLowerCase(Locale.ROOT);
    }

    /**
     * Enforces the shared account password length boundary.
     *
     * @param password cleartext request value
     * @throws IllegalArgumentException when outside 12 through 128 characters
     */
    public static void requirePassword(String password) {
        if (password == null || password.length() < 12 || password.length() > 128) {
            throw new IllegalArgumentException("Password must contain 12 through 128 characters");
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    /** Result of attempting the single allowed installation bootstrap. */
    public enum BootstrapOutcome {
        CREATED,
        ALREADY_INITIALIZED
    }
}
