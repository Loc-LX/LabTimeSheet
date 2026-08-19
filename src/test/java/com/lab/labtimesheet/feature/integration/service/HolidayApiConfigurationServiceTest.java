package com.lab.labtimesheet.feature.integration.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.integration.model.HolidayApiFailureKind;
import com.lab.labtimesheet.feature.integration.model.HolidayApiStatus;
import com.lab.labtimesheet.feature.integration.model.dto.EncryptedSecret;
import com.lab.labtimesheet.feature.integration.model.dto.HolidayApiCandidate;
import com.lab.labtimesheet.feature.integration.model.dto.HolidayApiDraft;
import com.lab.labtimesheet.feature.integration.model.dto.HolidayApiPreview;
import com.lab.labtimesheet.feature.integration.model.dto.HolidayApiResult;
import com.lab.labtimesheet.feature.integration.model.entity.HolidayApiConfiguration;
import com.lab.labtimesheet.feature.integration.repository.HolidayApiConfigurationRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class HolidayApiConfigurationServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-20T00:00:00Z");

    @Mock
    private HolidayApiConfigurationRepository configurations;

    @Mock
    private AccountService accounts;

    @Mock
    private SecretCipher secrets;

    @Mock
    private HolidayApiProbe probe;

    @Mock
    private Clock clock;

    @InjectMocks
    private HolidayApiConfigurationService service;

    @Test
    void saveTestAndActivateEncryptsSecretAndRetiresPreviousActiveRevision() {
        var encrypted = new EncryptedSecret(new byte[32], new byte[12], 1);
        given(clock.instant()).willReturn(NOW);
        given(accounts.requireActiveAdminId(7L)).willReturn(7L);
        given(secrets.encrypt("new-api-key")).willReturn(encrypted);
        given(configurations.save(any(HolidayApiConfiguration.class))).willAnswer(invocation -> {
            var value = invocation.getArgument(0, HolidayApiConfiguration.class);
            ReflectionTestUtils.setField(value, "id", 11L);
            return value;
        });
        given(configurations.findByStatus(HolidayApiStatus.DRAFT)).willReturn(Optional.empty());
        var testedDraft = HolidayApiConfiguration.draft(encrypted, 7L, NOW);
        ReflectionTestUtils.setField(testedDraft, "id", 11L);
        given(configurations.findById(11L)).willReturn(Optional.of(testedDraft));
        given(secrets.decrypt(encrypted.ciphertext(), encrypted.nonce())).willReturn("new-api-key");
        var probeResult = HolidayApiResult.success(new HolidayApiPreview(
                2026, "VN", List.of(new HolidayApiCandidate(
                        "uuid-1", "National Day", LocalDate.of(2026, 9, 2), null, true))));
        given(probe.preview("new-api-key", 2026)).willReturn(probeResult);

        long draftId = service.saveDraft(7L, new HolidayApiDraft("new-api-key"));
        var tested = service.testDraft(draftId, 7L, 2026);

        assertThat(draftId).isEqualTo(11L);
        assertThat(tested.successful()).isTrue();
        assertThat(service.setupStatus().countryCode()).isEqualTo("VN");
        verify(secrets).encrypt("new-api-key");
    }

    @Test
    void failedProviderTestDoesNotMarkDraftAndReturnsTypedFailure() {
        var encrypted = new EncryptedSecret(new byte[32], new byte[12], 1);
        var draft = HolidayApiConfiguration.draft(encrypted, 7L, NOW);
        ReflectionTestUtils.setField(draft, "id", 11L);
        given(configurations.findById(11L)).willReturn(Optional.of(draft));
        given(accounts.requireActiveAdminId(7L)).willReturn(7L);
        given(secrets.decrypt(encrypted.ciphertext(), encrypted.nonce())).willReturn("new-api-key");
        given(probe.preview("new-api-key", 2026))
                .willReturn(HolidayApiResult.failure(HolidayApiFailureKind.RATE_LIMITED));

        var result = service.testDraft(11L, 7L, 2026);

        assertThat(result.failure()).isEqualTo(HolidayApiFailureKind.RATE_LIMITED);
        assertThat(draft.getTestedAt()).isNull();
    }

    @Test
    void previewWithoutActiveRevisionDoesNotCallTheProvider() {
        given(configurations.findByStatus(HolidayApiStatus.ACTIVE)).willReturn(Optional.empty());

        var result = service.preview(2026);

        assertThat(result.failure()).isEqualTo(HolidayApiFailureKind.NOT_CONFIGURED);
        verifyNoInteractions(probe);
    }
}
