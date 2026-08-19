package com.lab.labtimesheet.feature.integration.service;

import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.integration.model.HolidayApiFailureKind;
import com.lab.labtimesheet.feature.integration.model.HolidayApiStatus;
import com.lab.labtimesheet.feature.integration.model.dto.EncryptedSecret;
import com.lab.labtimesheet.feature.integration.model.dto.HolidayApiDraft;
import com.lab.labtimesheet.feature.integration.model.dto.HolidayApiResult;
import com.lab.labtimesheet.feature.integration.model.dto.HolidayApiSetupStatus;
import com.lab.labtimesheet.feature.integration.model.entity.HolidayApiConfiguration;
import com.lab.labtimesheet.feature.integration.repository.HolidayApiConfigurationRepository;
import java.time.Clock;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns encrypted HolidayAPI draft/test/activate/retire revisions and the active DTO-only client.
 * Provider calls happen only from explicit test/preview methods; local calendar reads do not use this service.
 */
@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class HolidayApiConfigurationService implements HolidayApiClient {

    private final HolidayApiConfigurationRepository configurations;
    private final AccountService accounts;
    private final SecretCipher secrets;
    private final HolidayApiProbe probe;
    private final Clock clock;

    /**
     * Creates or replaces the single editable encrypted draft.
     *
     * @param adminId active Admin saving the draft
     * @param draft request-local API key
     * @return persisted draft identifier
     */
    @Transactional
    public long saveDraft(long adminId, HolidayApiDraft draft) {
        long verifiedAdminId = accounts.requireActiveAdminId(adminId);
        validate(draft);
        EncryptedSecret encrypted = secrets.encrypt(draft.apiKey());
        var now = clock.instant();
        HolidayApiConfiguration configuration = configurations.findByStatus(HolidayApiStatus.DRAFT)
                .map(existing -> {
                    existing.updateDraft(encrypted, now);
                    return existing;
                })
                .orElseGet(() -> HolidayApiConfiguration.draft(encrypted, verifiedAdminId, now));
        return configurations.save(configuration).getId();
    }

    /**
     * Explicitly tests a draft and marks it tested only when the provider result is successful.
     *
     * @param draftId draft revision identifier
     * @param adminId active Admin performing the test
     * @param year requested preview/test year
     * @return safe provider result
     */
    public HolidayApiResult testDraft(long draftId, long adminId, int year) {
        validateYear(year);
        HolidayApiConfiguration draft = configurations.findById(draftId)
                .filter(configuration -> configuration.getStatus() == HolidayApiStatus.DRAFT)
                .orElseThrow(() -> new IllegalStateException("HolidayAPI draft is not available"));
        long verifiedAdminId = accounts.requireActiveAdminId(adminId);
        HolidayApiResult result = probe.preview(
                secrets.decrypt(draft.getApiKeyCiphertext(), draft.getApiKeyNonce()), year);
        if (result != null && result.successful()) {
            draft.markTested(verifiedAdminId, clock.instant());
            configurations.save(draft);
        }
        return result == null
                ? HolidayApiResult.failure(HolidayApiFailureKind.UNAVAILABLE)
                : result;
    }

    /**
     * Activates a tested draft and retires the previous active revision atomically.
     *
     * @param draftId tested draft identifier
     * @param adminId active Admin authorizing activation
     */
    @Transactional
    public void activate(long draftId, long adminId) {
        HolidayApiConfiguration draft = configurations.findWithLockByIdAndStatus(
                        draftId, HolidayApiStatus.DRAFT)
                .orElseThrow(() -> new IllegalStateException("HolidayAPI draft must pass a test before activation"));
        long verifiedAdminId = accounts.requireActiveAdminId(adminId);
        var now = clock.instant();
        configurations.findByStatus(HolidayApiStatus.ACTIVE)
                .ifPresent(active -> {
                    active.retire(verifiedAdminId, now);
                    configurations.saveAndFlush(active);
                });
        draft.activate(verifiedAdminId, now);
    }

    /**
     * Returns non-secret state for the setup page.
     *
     * @return safe active/draft/test metadata with fixed Vietnam country
     */
    @Transactional(readOnly = true)
    public HolidayApiSetupStatus setupStatus() {
        boolean active = configurations.existsByStatus(HolidayApiStatus.ACTIVE);
        return configurations.findByStatus(HolidayApiStatus.DRAFT)
                .map(draft -> new HolidayApiSetupStatus(
                        active, draft.getId(), draft.getTestedAt() != null, draft.getCountryCode()))
                .orElseGet(() -> new HolidayApiSetupStatus(active, null, false, "VN"));
    }

    /**
     * Makes an explicit preview call through the active encrypted revision.
     *
     * @param year requested year
     * @return preview or typed not-configured/provider failure
     */
    @Override
    @Transactional(readOnly = true)
    public HolidayApiResult preview(int year) {
        validateYear(year);
        HolidayApiConfiguration active = configurations.findByStatus(HolidayApiStatus.ACTIVE).orElse(null);
        if (active == null) {
            return HolidayApiResult.failure(HolidayApiFailureKind.NOT_CONFIGURED);
        }
        HolidayApiResult result = probe.preview(
                secrets.decrypt(active.getApiKeyCiphertext(), active.getApiKeyNonce()), year);
        return result == null
                ? HolidayApiResult.failure(HolidayApiFailureKind.UNAVAILABLE)
                : result;
    }

    private static void validate(HolidayApiDraft draft) {
        if (draft == null || draft.apiKey() == null || draft.apiKey().isBlank()
                || draft.apiKey().length() > 1024) {
            throw new IllegalArgumentException("A valid HolidayAPI key is required");
        }
    }

    private static void validateYear(int year) {
        if (year < 2000 || year > 2100) {
            throw new IllegalArgumentException("HolidayAPI year must be between 2000 and 2100");
        }
    }
}
