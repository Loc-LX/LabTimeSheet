package com.lab.labtimesheet.feature.integration.model.entity;

import java.time.Instant;

import com.lab.labtimesheet.feature.integration.model.SecurityMode;
import com.lab.labtimesheet.feature.integration.model.SmtpStatus;
import com.lab.labtimesheet.feature.integration.model.dto.EncryptedSecret;
import com.lab.labtimesheet.feature.integration.model.dto.SmtpDraft;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Versioned SMTP configuration entity whose credentials remain AES-GCM encrypted at rest.
 * Draft edits clear test status; only a tested draft can activate; replaced active revisions are retained as retired.
 */
@Entity
@Table(name = "smtp_configurations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SmtpConfiguration {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Getter
    private SmtpStatus status;

    @Column(nullable = false, length = 255)
    @Getter
    private String host;

    @Column(nullable = false)
    @Getter
    private int port;

    @Enumerated(EnumType.STRING)
    @Column(name = "security_mode", nullable = false, length = 16)
    @Getter
    private SecurityMode securityMode;

    @Column(length = 320)
    @Getter
    private String username;

    @Column(name = "password_ciphertext")
    private byte[] passwordCiphertext;

    @Column(name = "password_nonce")
    private byte[] passwordNonce;

    @Column(name = "secret_key_version")
    @Getter
    private Integer secretKeyVersion;

    @Column(name = "from_address", nullable = false, length = 320)
    @Getter
    private String fromAddress;

    @Column(name = "from_name", nullable = false, length = 120)
    @Getter
    private String fromName;

    @Column(name = "tested_at")
    @Getter
    private Instant testedAt;

    @Column(name = "tested_by_user_id")
    private Long testedByUserId;

    @Column(name = "activated_at")
    private Instant activatedAt;

    @Column(name = "activated_by_user_id")
    private Long activatedByUserId;

    @Column(name = "retired_at")
    private Instant retiredAt;

    @Column(name = "retired_by_user_id")
    private Long retiredByUserId;

    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private long version;

    /**
     * Creates an editable SMTP revision with encrypted credential material.
     *
     * @param draft validated SMTP settings
     * @param password encrypted password, or {@code null} for unauthenticated SMTP
     * @param adminId active Admin creating the revision
     * @param now server timestamp
     * @return new draft revision
     */
    public static SmtpConfiguration draft(SmtpDraft draft, EncryptedSecret password, long adminId, Instant now) {
        SmtpConfiguration configuration = new SmtpConfiguration();
        configuration.status = SmtpStatus.DRAFT;
        configuration.createdByUserId = adminId;
        configuration.createdAt = now;
        configuration.updateDraft(draft, password, now);
        return configuration;
    }

    /**
     * Replaces editable settings and clears any previous successful-test marker.
     *
     * @param draft validated SMTP settings
     * @param password encrypted password, or {@code null}
     * @param now server update timestamp
     * @throws IllegalStateException when this revision is no longer a draft
     */
    public void updateDraft(SmtpDraft draft, EncryptedSecret password, Instant now) {
        if (status != SmtpStatus.DRAFT) {
            throw new IllegalStateException("Only an SMTP draft can be edited");
        }
        host = draft.host().trim();
        port = draft.port();
        securityMode = draft.securityMode();
        username = clean(draft.username());
        passwordCiphertext = password == null ? null : password.ciphertext();
        passwordNonce = password == null ? null : password.nonce();
        secretKeyVersion = password == null ? null : password.keyVersion();
        fromAddress = draft.fromAddress().trim();
        fromName = draft.fromName().trim();
        testedAt = null;
        testedByUserId = null;
        updatedAt = now;
    }

    /**
     * Records a successful external probe after its delivery adapter returns.
     *
     * @param adminId active Admin who performed the test
     * @param now server success timestamp
     * @throws IllegalStateException when this revision is no longer a draft
     */
    public void markTested(long adminId, Instant now) {
        if (status != SmtpStatus.DRAFT) {
            throw new IllegalStateException("SMTP draft is no longer available");
        }
        testedAt = now;
        testedByUserId = adminId;
        updatedAt = now;
    }

    /**
     * Promotes a tested draft to the active delivery configuration.
     *
     * @param adminId active Admin authorizing activation
     * @param now server activation timestamp
     * @throws IllegalStateException when the draft has not passed a test
     */
    public void activate(long adminId, Instant now) {
        if (status != SmtpStatus.DRAFT || testedAt == null) {
            throw new IllegalStateException("SMTP draft must pass a test before activation");
        }
        status = SmtpStatus.ACTIVE;
        activatedAt = now;
        activatedByUserId = adminId;
        updatedAt = now;
    }

    /**
     * Retains but disables a replaced active revision.
     *
     * @param adminId active Admin activating its successor
     * @param now server retirement timestamp
     * @throws IllegalStateException when this revision is not active
     */
    public void retire(long adminId, Instant now) {
        if (status != SmtpStatus.ACTIVE) {
            throw new IllegalStateException("Only active SMTP can be retired");
        }
        status = SmtpStatus.RETIRED;
        retiredAt = now;
        retiredByUserId = adminId;
        updatedAt = now;
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** @return a defensive copy of encrypted password bytes, or {@code null} */
    public byte[] getPasswordCiphertext() {
        return passwordCiphertext == null ? null : passwordCiphertext.clone();
    }

    /** @return a defensive copy of the AES-GCM nonce, or {@code null} */
    public byte[] getPasswordNonce() {
        return passwordNonce == null ? null : passwordNonce.clone();
    }

}
