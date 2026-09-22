package com.lab.labtimesheet.feature.integration.model.entity;

import java.time.Instant;

import com.lab.labtimesheet.feature.integration.model.HolidayApiStatus;
import com.lab.labtimesheet.feature.integration.model.dto.HolidayApiDraft;
import com.lab.labtimesheet.platform.model.dto.EncryptedSecret;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Versioned encrypted HolidayAPI credential fixed to country VN.
 * Draft edits clear test status; only a tested draft can activate and an old active revision is retained retired.
 */
@Entity
@Table(name = "holiday_api_configurations")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HolidayApiConfiguration {
    /** The only country supported by this product integration. */
    public static final String COUNTRY_CODE = "VN";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Getter
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Getter
    private HolidayApiStatus status;

    @Column(name = "country_code", nullable = false, columnDefinition = "char(2)")
    @JdbcTypeCode(SqlTypes.CHAR)
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
    @Getter
    private Long testedByUserId;

    @Column(name = "activated_at")
    @Getter
    private Instant activatedAt;

    @Column(name = "activated_by_user_id")
    @Getter
    private Long activatedByUserId;

    @Column(name = "retired_at")
    @Getter
    private Instant retiredAt;

    @Column(name = "retired_by_user_id")
    @Getter
    private Long retiredByUserId;

    @Column(name = "created_by_user_id", nullable = false)
    @Getter
    private Long createdByUserId;

    @Column(name = "created_at", nullable = false)
    @Getter
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    @Getter
    private Instant updatedAt;

    @Version
    private long version;

    /**
     * Creates a VN draft with encrypted request-local key material.
     *
     * @param draft validated request-local key
     * @param apiKey encrypted key envelope
     * @param adminId active Admin creating the revision
     * @param now server creation timestamp
     * @return new editable draft
     */
    public static HolidayApiConfiguration draft(HolidayApiDraft draft, EncryptedSecret apiKey,
            long adminId, Instant now) {
        HolidayApiConfiguration configuration = new HolidayApiConfiguration();
        configuration.status = HolidayApiStatus.DRAFT;
        configuration.countryCode = COUNTRY_CODE;
        configuration.createdByUserId = adminId;
        configuration.createdAt = now;
        configuration.updateDraft(draft, apiKey, now);
        return configuration;
    }

    /**
     * Replaces an editable key and clears any previous successful-test marker.
     *
     * @param draft validated request-local key
     * @param apiKey encrypted key envelope
     * @param now server update timestamp
     * @throws IllegalStateException when this revision is no longer a draft
     */
    public void updateDraft(HolidayApiDraft draft, EncryptedSecret apiKey, Instant now) {
        if (status != HolidayApiStatus.DRAFT) {
            throw new IllegalStateException("Only a HolidayAPI draft can be edited");
        }
        countryCode = COUNTRY_CODE;
        apiKeyCiphertext = apiKey.ciphertext();
        apiKeyNonce = apiKey.nonce();
        secretKeyVersion = apiKey.keyVersion();
        testedAt = null;
        testedByUserId = null;
        updatedAt = now;
    }

    /**
     * Records a successful external probe after the adapter returns successfully.
     *
     * @param adminId active Admin who performed the test
     * @param now server success timestamp
     * @throws IllegalStateException when this revision is no longer a draft
     */
    public void markTested(long adminId, Instant now) {
        if (status != HolidayApiStatus.DRAFT) {
            throw new IllegalStateException("HolidayAPI draft is no longer available");
        }
        testedAt = now;
        testedByUserId = adminId;
        updatedAt = now;
    }

    /**
     * Promotes a successfully tested draft to active.
     *
     * @param adminId active Admin authorizing activation
     * @param now server activation timestamp
     * @throws IllegalStateException when the draft has not passed a test
     */
    public void activate(long adminId, Instant now) {
        if (status != HolidayApiStatus.DRAFT || testedAt == null) {
            throw new IllegalStateException("HolidayAPI draft must pass a test before activation");
        }
        status = HolidayApiStatus.ACTIVE;
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
        if (status != HolidayApiStatus.ACTIVE) {
            throw new IllegalStateException("Only active HolidayAPI configuration can be retired");
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
