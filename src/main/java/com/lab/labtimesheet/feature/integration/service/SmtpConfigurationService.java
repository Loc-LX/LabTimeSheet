package com.lab.labtimesheet.feature.integration.service;

import java.time.Clock;

import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.integration.model.SecurityMode;
import com.lab.labtimesheet.feature.integration.model.SmtpStatus;
import com.lab.labtimesheet.feature.integration.model.dto.EncryptedSecret;
import com.lab.labtimesheet.feature.integration.model.dto.SmtpConnection;
import com.lab.labtimesheet.feature.integration.model.dto.SmtpDraft;
import com.lab.labtimesheet.feature.integration.model.dto.SmtpSetupStatus;
import com.lab.labtimesheet.feature.integration.model.entity.SmtpConfiguration;
import com.lab.labtimesheet.feature.integration.repository.SmtpConfigurationRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the Admin SMTP revision workflow: save an encrypted draft, test it, then atomically activate it.
 * A changed draft loses prior test status, and an active revision is retired when its tested successor activates.
 */
@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class SmtpConfigurationService {
    private final SmtpConfigurationRepository configurations;
    private final AccountService accounts;
    private final SecretCipher secrets;
    private final SmtpProbe probe;
    private final Environment environment;
    private final Clock clock;
    private final MailDeliveryService mailDelivery;

    /**
     * Creates or replaces the editable draft after validating Admin authority and environment transport rules.
     * Any supplied password is encrypted before persistence and prior test status is cleared.
     *
     * @param adminId active Admin saving the draft
     * @param draft SMTP settings and optional request-local password
     * @return persisted draft identifier
     */
    @Transactional
    public long saveDraft(long adminId, SmtpDraft draft) {
        validate(draft);
        EncryptedSecret password = draft.password() == null ? null : secrets.encrypt(draft.password());
        var now = clock.instant();
        long verifiedAdminId = accounts.requireActiveAdminId(adminId);
        SmtpConfiguration configuration = configurations.findByStatus(SmtpStatus.DRAFT)
                .map(existing -> {
                    existing.updateDraft(draft, password, now);
                    return existing;
                })
                .orElseGet(() -> SmtpConfiguration.draft(draft, password, verifiedAdminId, now));
        return configurations.save(configuration).getId();
    }

    /**
     * Sends a real probe using a draft and records success only after the adapter returns successfully.
     *
     * @param draftId draft revision to test
     * @param adminId active Admin performing the test
     * @param recipient Admin email receiving the test message
     */
    public void testDraft(long draftId, long adminId, String recipient) {
        SmtpConfiguration draft = configurations.findById(draftId)
                .filter(configuration -> configuration.getStatus() == SmtpStatus.DRAFT)
                .orElseThrow(() -> new IllegalStateException("SMTP configuration is not available"));
        probe.send(connection(draft), recipient, "Lab Timesheet SMTP test", "SMTP configuration test succeeded.");
        long verifiedAdminId = accounts.requireActiveAdminId(adminId);
        draft.markTested(verifiedAdminId, clock.instant());
        configurations.save(draft);
    }

    /**
     * Activates a previously tested draft under a pessimistic lock and retires the prior active revision.
     *
     * @param draftId tested draft revision
     * @param adminId active Admin authorizing activation
     */
    @Transactional
    public void activate(long draftId, long adminId) {
        SmtpConfiguration draft = configurations.findWithLockByIdAndStatus(draftId, SmtpStatus.DRAFT)
                .orElseThrow(() -> new IllegalStateException("SMTP draft must pass a test before activation"));
        long verifiedAdminId = accounts.requireActiveAdminId(adminId);
        var now = clock.instant();
        configurations.findByStatus(SmtpStatus.ACTIVE)
                .ifPresent(active -> active.retire(verifiedAdminId, now));
        draft.activate(verifiedAdminId, now);
    }

    /** @return {@code true} when a tested SMTP revision is currently active */
    @Transactional(readOnly = true)
    public boolean hasActiveConfiguration() {
        return mailDelivery.isAvailable();
    }

    /**
     * Returns the non-secret SMTP state needed by the Admin setup page.
     * Password ciphertext, nonce, and decrypted credentials are never included.
     *
     * @return current active flag and editable draft metadata
     */
    @Transactional(readOnly = true)
    public SmtpSetupStatus setupStatus() {
        boolean active = configurations.existsByStatus(SmtpStatus.ACTIVE);
        return configurations.findByStatus(SmtpStatus.DRAFT)
                .map(draft -> new SmtpSetupStatus(
                        active,
                        draft.getId(),
                        draft.getTestedAt() != null,
                        draft.getHost(),
                        draft.getPort(),
                        draft.getSecurityMode(),
                        draft.getUsername(),
                        draft.getFromAddress(),
                        draft.getFromName()))
                .orElseGet(() -> new SmtpSetupStatus(active, null, false, null, 587,
                        SecurityMode.STARTTLS, null, null, null));
    }

    /**
     * Resolves the active SMTP connection for an immediate integration call.
     *
     * @return transient connection values, including a decrypted password when configured
     */
    @Transactional(readOnly = true)
    public SmtpConnection activeConnection() {
        return mailDelivery.activeConnection();
    }

    /**
     * Sends a plain-text message through the active SMTP revision.
     *
     * @param recipient destination email address
     * @param subject message subject
     * @param body message body
     */
    public void sendWithActiveConfiguration(String recipient, String subject, String body) {
        mailDelivery.send(recipient, subject, body);
    }

    private SmtpConnection connection(SmtpConfiguration configuration) {
        return mailDelivery.connection(configuration);
    }

    private void validate(SmtpDraft draft) {
        if (draft.host() == null || draft.host().isBlank() || draft.port() < 1 || draft.port() > 65535
                || draft.securityMode() == null || draft.fromAddress() == null || draft.fromAddress().isBlank()
                || draft.fromName() == null || draft.fromName().isBlank()) {
            throw new IllegalArgumentException("Valid SMTP host, port, security mode, From address and name are required");
        }
        if ((clean(draft.username()) == null) != (draft.password() == null || draft.password().isEmpty())) {
            throw new IllegalArgumentException("SMTP username and password must be supplied together");
        }
        if (draft.securityMode() == SecurityMode.NONE
                && !environment.acceptsProfiles(Profiles.of("dev", "test"))) {
            throw new IllegalArgumentException("Plaintext SMTP is allowed only in dev and test");
        }
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
