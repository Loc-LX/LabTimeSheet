package com.lab.labtimesheet.platform.service;

import java.time.Clock;
import java.util.List;

import com.lab.labtimesheet.platform.model.SecurityMode;
import com.lab.labtimesheet.platform.model.SmtpStatus;
import com.lab.labtimesheet.platform.model.dto.EncryptedSecret;
import com.lab.labtimesheet.platform.model.dto.SmtpConnection;
import com.lab.labtimesheet.platform.model.dto.SmtpDraft;
import com.lab.labtimesheet.platform.model.dto.SmtpRevisionHistory;
import com.lab.labtimesheet.platform.model.dto.SmtpSetupStatus;
import com.lab.labtimesheet.platform.model.entity.SmtpConfiguration;
import com.lab.labtimesheet.platform.repository.SmtpConfigurationRepository;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the Admin SMTP revision workflow: save an encrypted draft, test it, then atomically activate it.
 * A changed draft loses prior test status, and an active revision is retired when its tested successor activates.
 *
 * <p>Every operation takes the actor the caller has already verified, and the test probe takes the recipient the
 * caller resolved, as {@code R4} of {@code D28} requires: this service is shared code and reads no other
 * module, so it cannot look an Admin up in {@code identity} itself.
 */
@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class SmtpConfigurationService {
    private final SmtpConfigurationRepository configurations;
    private final SecretCipher secrets;
    private final SmtpProbe probe;
    private final Environment environment;
    private final Clock clock;
    private final MailDeliveryService mailDelivery;

    /**
     * Creates or replaces the editable draft after validating environment transport rules.
     * Any supplied password is encrypted before persistence and prior test status is cleared.
     *
     * @param verifiedAdminId identity id of the active Admin the caller has verified
     * @param draft SMTP settings and optional request-local password
     * @return persisted draft identifier
     */
    @Transactional
    public long saveDraft(long verifiedAdminId, SmtpDraft draft) {
        validate(draft);
        EncryptedSecret password = draft.password() == null ? null : secrets.encrypt(draft.password());
        var now = clock.instant();
        SmtpConfiguration configuration = configurations.findByStatus(SmtpStatus.DRAFT)
                .map(existing -> {
                    existing.updateDraft(draft, password, now);
                    return existing;
                })
                .orElseGet(() -> SmtpConfiguration.draft(draft, password, verifiedAdminId, now));
        return configurations.save(configuration).getId();
    }

    /**
     * Sends a real probe using a draft to the recipient the caller resolved for the authenticated Admin, and records
     * success only after the adapter returns successfully. The destination is never taken from request input.
     *
     * @param draftId draft revision to test
     * @param verifiedAdminId identity id of the active Admin the caller has verified
     * @param recipient address the caller resolved for that Admin
     */
    public void testDraft(long draftId, long verifiedAdminId, String recipient) {
        SmtpConfiguration draft = configurations.findById(draftId)
                .filter(configuration -> configuration.getStatus() == SmtpStatus.DRAFT)
                .orElseThrow(() -> new IllegalStateException("SMTP configuration is not available"));
        probe.send(connection(draft), recipient, "Lab Timesheet SMTP test", "SMTP configuration test succeeded.");
        draft.markTested(verifiedAdminId, clock.instant());
        configurations.save(draft);
    }

    /**
     * Activates a previously tested draft under a pessimistic lock and retires the prior active revision.
     *
     * @param draftId tested draft revision
     * @param verifiedAdminId identity id of the active Admin the caller has verified
     */
    @Transactional
    public void activate(long draftId, long verifiedAdminId) {
        SmtpConfiguration draft = configurations.findWithLockByIdAndStatus(draftId, SmtpStatus.DRAFT)
                .orElseThrow(() -> new IllegalStateException("SMTP draft must pass a test before activation"));
        var now = clock.instant();
        configurations.findByStatus(SmtpStatus.ACTIVE)
                .ifPresent(active -> {
                    active.retire(verifiedAdminId, now);
                    configurations.saveAndFlush(active);
                });
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
     * @param verifiedAdminId identity id of the active Admin the caller has verified
     * @return current active flag and editable draft metadata
     */
    @Transactional(readOnly = true)
    public SmtpSetupStatus setupStatus(long verifiedAdminId) {
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
     * Returns retained SMTP lifecycle metadata newest first without exposing any credential material.
     *
     * @param verifiedAdminId identity id of the active Admin the caller has verified
     * @return immutable non-secret revision history
     */
    @Transactional(readOnly = true)
    public List<SmtpRevisionHistory> history(long verifiedAdminId) {
        return configurations.findAllByOrderByCreatedAtDescIdDesc().stream()
                .map(configuration -> new SmtpRevisionHistory(
                        configuration.getId(), configuration.getStatus(), configuration.getHost(),
                        configuration.getPort(), configuration.getSecurityMode(), configuration.getUsername(),
                        configuration.getFromAddress(), configuration.getFromName(), configuration.getTestedAt(),
                        configuration.getTestedByUserId(), configuration.getActivatedAt(),
                        configuration.getActivatedByUserId(), configuration.getRetiredAt(),
                        configuration.getRetiredByUserId(), configuration.getCreatedByUserId(),
                        configuration.getCreatedAt(), configuration.getUpdatedAt()))
                .toList();
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
