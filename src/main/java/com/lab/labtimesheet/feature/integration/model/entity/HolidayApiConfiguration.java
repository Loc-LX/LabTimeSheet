package com.lab.labtimesheet.feature.integration.model.entity;

import com.lab.labtimesheet.feature.integration.model.HolidayApiStatus;
import com.lab.labtimesheet.feature.integration.model.dto.EncryptedSecret;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Retained HolidayAPI credential revision with AES-GCM material only and no cleartext key field.
 * Draft edits clear the successful-test marker; only a tested draft may activate.
 */
@Entity
@Table(name = "holiday_api_configurations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HolidayApiConfiguration {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Getter
    private HolidayApiStatus status;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "country_code", nullable = false, length = 2)
    @Getter
    private String countryCode;

    @Column(name = "api_key_ciphertext", nullable = false)
    private byte[] apiKeyCiphertext;

    @Column(name = "api_key_nonce", nullable = false)
    private byte[] apiKeyNonce;

    @Column(name = "secret_key_version", nullable = false)
    @Getter
    private Integer secretKeyVersion;

    @Column(name = "tested_at")
    @Getter
    private Instant testedAt;

    @Column(name = "tested_by_user_id")
    private Long testedByUserId;

    @Column(name = "activated_at")
    @Getter
    private Instant activatedAt;

    @Column(name = "activated_by_user_id")
    private Long activatedByUserId;

    @Column(name = "retired_at")
    @Getter
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
     * Creates a fixed-Vietnam encrypted draft revision.
     *
     * @param apiKey encrypted API key material
     * @param adminId active Admin creating the revision
     * @param now server timestamp
     * @return new draft revision
     */
    public static HolidayApiConfiguration draft(EncryptedSecret apiKey, long adminId, Instant now) {
        var configuration = new HolidayApiConfiguration();
        configuration.status = HolidayApiStatus.DRAFT;
        configuration.countryCode = "VN";
        configuration.createdByUserId = adminId;
        configuration.createdAt = now;
        configuration.updateDraft(apiKey, now);
        return configuration;
    }

    /** Replaces the encrypted key and clears any previous test marker. */
    public void updateDraft(EncryptedSecret apiKey, Instant now) {
        if (status != HolidayApiStatus.DRAFT) {
            throw new IllegalStateException("Only a HolidayAPI draft can be edited");
        }
        Objects.requireNonNull(apiKey, "apiKey");
        apiKeyCiphertext = apiKey.ciphertext();
        apiKeyNonce = apiKey.nonce();
        secretKeyVersion = apiKey.keyVersion();
        testedAt = null;
        testedByUserId = null;
        updatedAt = now;
    }

    /** Records a successful provider test for this draft. */
    public void markTested(long adminId, Instant now) {
        if (status != HolidayApiStatus.DRAFT) {
            throw new IllegalStateException("HolidayAPI draft is no longer available");
        }
        testedAt = now;
        testedByUserId = adminId;
        updatedAt = now;
    }

    /** Promotes a tested draft to the active revision. */
    public void activate(long adminId, Instant now) {
        if (status != HolidayApiStatus.DRAFT || testedAt == null) {
            throw new IllegalStateException("HolidayAPI draft must pass a test before activation");
        }
        status = HolidayApiStatus.ACTIVE;
        activatedAt = now;
        activatedByUserId = adminId;
        updatedAt = now;
    }

    /** Retains but disables a replaced active revision. */
    public void retire(long adminId, Instant now) {
        if (status != HolidayApiStatus.ACTIVE) {
            throw new IllegalStateException("Only active HolidayAPI can be retired");
        }
        status = HolidayApiStatus.RETIRED;
        retiredAt = now;
        retiredByUserId = adminId;
        updatedAt = now;
    }

    /** @return defensive copy of encrypted API-key bytes */
    public byte[] getApiKeyCiphertext() {
        return apiKeyCiphertext == null ? null : apiKeyCiphertext.clone();
    }

    /** @return defensive copy of the AES-GCM nonce */
    public byte[] getApiKeyNonce() {
        return apiKeyNonce == null ? null : apiKeyNonce.clone();
    }
}
