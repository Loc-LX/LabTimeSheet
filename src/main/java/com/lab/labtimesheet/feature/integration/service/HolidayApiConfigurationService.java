package com.lab.labtimesheet.feature.integration.service;

import java.time.Clock;
import java.util.List;

import com.lab.labtimesheet.feature.identity.service.AccountService;
import com.lab.labtimesheet.feature.integration.model.HolidayApiStatus;
import com.lab.labtimesheet.feature.integration.model.dto.HolidayApiDraft;
import com.lab.labtimesheet.feature.integration.model.dto.HolidayApiPreview;
import com.lab.labtimesheet.feature.integration.model.dto.HolidayApiPreviewStatus;
import com.lab.labtimesheet.feature.integration.model.dto.HolidayApiRevisionHistory;
import com.lab.labtimesheet.feature.integration.model.dto.HolidayApiSetupStatus;
import com.lab.labtimesheet.feature.integration.model.entity.HolidayApiConfiguration;
import com.lab.labtimesheet.feature.integration.repository.HolidayApiConfigurationRepository;
import com.lab.labtimesheet.platform.model.dto.EncryptedSecret;
import com.lab.labtimesheet.platform.service.SecretCipher;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns encrypted HolidayAPI revision lifecycle and the optional VN preview boundary.
 * Attendance and calendar consumers receive candidates only through the immutable preview DTO and never trigger
 * provider calls from local reads or reports.
 */
@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class HolidayApiConfigurationService {
    private static final String ABSENT_KEY_MESSAGE = "HolidayAPI is not configured; local calendar remains usable.";
    private static final String SUCCESS_MESSAGE = "HolidayAPI preview loaded.";

    private final HolidayApiConfigurationRepository configurations;
    private final AccountService accounts;
    private final SecretCipher secrets;
    private final HolidayApiHttpClient client;
    private final Clock clock;

    /**
     * Creates or replaces the sole editable VN draft after Admin authorization.
     * The request-local key is encrypted before persistence and prior test status is cleared.
     *
     * @param adminId active Admin saving the draft
     * @param draft request-local provider key
     * @return persisted draft identifier
     */
    @Transactional
    public long saveDraft(long adminId, HolidayApiDraft draft) {
        long verifiedAdminId = accounts.requireActiveAdminId(adminId);
        EncryptedSecret apiKey = secrets.encrypt(draft.apiKey());
        var now = clock.instant();
        HolidayApiConfiguration configuration = configurations.findByStatus(HolidayApiStatus.DRAFT)
                .map(existing -> {
                    existing.updateDraft(draft, apiKey, now);
                    return existing;
                })
                .orElseGet(() -> HolidayApiConfiguration.draft(draft, apiKey, verifiedAdminId, now));
        return configurations.save(configuration).getId();
    }

    /**
     * Tests a draft against the provider and records success only after a valid response.
     * Failure returns an actionable status and leaves the draft untested.
     *
     * @param draftId draft revision to test
     * @param adminId active Admin performing the test
     * @param year requested preview year
     * @return safe success candidates or actionable failure status
     */
    public HolidayApiPreview testDraft(long draftId, long adminId, int year) {
        long verifiedAdminId = accounts.requireActiveAdminId(adminId);
        HolidayApiConfiguration draft = configurations.findById(draftId)
                .filter(configuration -> configuration.getStatus() == HolidayApiStatus.DRAFT)
                .orElseThrow(() -> new IllegalStateException("HolidayAPI configuration is not available"));
        HolidayApiPreview result = safeFetch(draft, year);
        if (result.status() == HolidayApiPreviewStatus.SUCCESS) {
            draft.markTested(verifiedAdminId, clock.instant());
            configurations.save(draft);
        }
        return result;
    }

    /**
     * Activates a previously tested VN draft and retires the previous active revision atomically.
     *
     * @param draftId tested draft revision
     * @param adminId active Admin authorizing activation
     */
    @Transactional
    public void activate(long draftId, long adminId) {
        long verifiedAdminId = accounts.requireActiveAdminId(adminId);
        HolidayApiConfiguration draft = configurations.findWithLockByIdAndStatus(draftId, HolidayApiStatus.DRAFT)
                .orElseThrow(() -> new IllegalStateException("HolidayAPI draft must pass a test before activation"));
        var now = clock.instant();
        configurations.findByStatus(HolidayApiStatus.ACTIVE)
                .ifPresent(active -> {
                    active.retire(verifiedAdminId, now);
                    configurations.saveAndFlush(active);
                });
        draft.activate(verifiedAdminId, now);
    }

    /**
     * Requests an optional provider preview through the active encrypted VN key.
     * The caller must be an Admin-authorized preview action; local calendar reads never call this method.
     *
     * @param adminId active Admin authorizing the preview
     * @param year requested four-digit calendar year
     * @return immutable candidates or a secret-free actionable status
     */
    public HolidayApiPreview preview(long adminId, int year) {
        accounts.requireActiveAdminId(adminId);
        HolidayApiConfiguration active = configurations.findByStatus(HolidayApiStatus.ACTIVE).orElse(null);
        if (active == null) {
            return new HolidayApiPreview(HolidayApiPreviewStatus.ABSENT_KEY, List.of(), ABSENT_KEY_MESSAGE, null);
        }
        return safeFetch(active, year);
    }

    /**
     * Returns the non-secret setup state used by the Admin configuration page.
     *
     * @param adminId active Admin requesting setup state
     * @return active flag, draft id, test state, and fixed country code
     */
    @Transactional(readOnly = true)
    public HolidayApiSetupStatus setupStatus(long adminId) {
        accounts.requireActiveAdminId(adminId);
        boolean active = configurations.existsByStatus(HolidayApiStatus.ACTIVE);
        return configurations.findByStatus(HolidayApiStatus.DRAFT)
                .map(draft -> new HolidayApiSetupStatus(active, draft.getId(), draft.getTestedAt() != null,
                        HolidayApiConfiguration.COUNTRY_CODE))
                .orElseGet(() -> new HolidayApiSetupStatus(active, null, false,
                        HolidayApiConfiguration.COUNTRY_CODE));
    }

    /**
     * Returns retained lifecycle metadata newest first without exposing API-key material.
     *
     * @param adminId active Admin requesting History
     * @return immutable non-secret History rows
     */
    @Transactional(readOnly = true)
    public List<HolidayApiRevisionHistory> history(long adminId) {
        accounts.requireActiveAdminId(adminId);
        return configurations.findAllByOrderByCreatedAtDescIdDesc().stream()
                .map(configuration -> new HolidayApiRevisionHistory(
                        configuration.getId(), configuration.getStatus(), configuration.getCountryCode(),
                        configuration.getTestedAt(), configuration.getTestedByUserId(),
                        configuration.getActivatedAt(), configuration.getActivatedByUserId(),
                        configuration.getRetiredAt(), configuration.getRetiredByUserId(),
                        configuration.getCreatedByUserId(), configuration.getCreatedAt(),
                        configuration.getUpdatedAt()))
                .toList();
    }

    private HolidayApiPreview fetch(String apiKey, int year) {
        if (year < 1 || year > 9999) {
            throw new IllegalArgumentException("HolidayAPI year must be between 1 and 9999");
        }
        try {
            return new HolidayApiPreview(HolidayApiPreviewStatus.SUCCESS,
                    client.fetch(apiKey, HolidayApiConfiguration.COUNTRY_CODE, year), SUCCESS_MESSAGE, clock.instant());
        } catch (HolidayApiClientException failure) {
            return new HolidayApiPreview(failure.status(), List.of(), failure.getMessage(), null);
        }
    }

    private HolidayApiPreview safeFetch(HolidayApiConfiguration configuration, int year) {
        try {
            return fetch(decrypt(configuration), year);
        } catch (HolidayApiClientException failure) {
            return new HolidayApiPreview(failure.status(), List.of(), failure.getMessage(), null);
        }
    }

    private String decrypt(HolidayApiConfiguration configuration) {
        try {
            return secrets.decrypt(configuration.getApiKeyCiphertext(), configuration.getApiKeyNonce());
        } catch (IllegalStateException failure) {
            throw throwUnavailable();
        }
    }

    private HolidayApiClientException throwUnavailable() {
        return HolidayApiClientException.unavailable();
    }
}
