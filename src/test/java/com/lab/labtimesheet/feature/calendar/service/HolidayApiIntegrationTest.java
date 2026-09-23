package com.lab.labtimesheet.feature.calendar.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.identity.service.AccountService;
import com.lab.labtimesheet.feature.identity.service.BootstrapService;
import com.lab.labtimesheet.feature.calendar.model.HolidayApiStatus;
import com.lab.labtimesheet.feature.calendar.model.dto.HolidayApiCandidate;
import com.lab.labtimesheet.feature.calendar.model.dto.HolidayApiDraft;
import com.lab.labtimesheet.feature.calendar.model.dto.HolidayApiPreviewStatus;
import com.lab.labtimesheet.feature.calendar.model.dto.HolidayApiRevisionHistory;
import com.lab.labtimesheet.feature.calendar.repository.HolidayApiConfigurationRepository;
import org.springframework.web.client.RestClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

@Import({TestcontainersConfiguration.class, HolidayApiIntegrationTest.ClientConfiguration.class})
@SpringBootTest
@ActiveProfiles("test")
class HolidayApiIntegrationTest {

    @org.springframework.beans.factory.annotation.Autowired
    private BootstrapService bootstrapService;

    @org.springframework.beans.factory.annotation.Autowired
    private AccountService accountService;

    @org.springframework.beans.factory.annotation.Autowired
    private HolidayApiConfigurationService holidayApi;

    @org.springframework.beans.factory.annotation.Autowired
    private HolidayApiConfigurationRepository configurations;

    @org.springframework.beans.factory.annotation.Autowired
    private RecordingHolidayApiClient client;

    @Test
    void lifecycleEncryptsRevisionsAndPreviewReportsActionableExternalStates() {
        assertThatThrownBy(() -> holidayApi.preview(404L, 2026))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Admin not found");
        assertThatThrownBy(() -> holidayApi.setupStatus(404L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Admin not found");
        assertThatThrownBy(() -> holidayApi.history(404L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Admin not found");
        assertThatThrownBy(() -> holidayApi.testDraft(404L, 404L, 2026))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Admin not found");
        assertThatThrownBy(() -> holidayApi.activate(404L, 404L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Admin not found");

        bootstrapService.bootstrap("admin@example.com", "Admin", "correct horse battery staple");
        long adminId = accountService.requireActiveAdminId("admin@example.com");
        assertThat(holidayApi.preview(adminId, 2026).status()).isEqualTo(HolidayApiPreviewStatus.ABSENT_KEY);
        long draftId = holidayApi.saveDraft(adminId, new HolidayApiDraft("first-api-key"));

        var saved = configurations.findById(draftId).orElseThrow();
        assertThat(saved.getCountryCode()).isEqualTo("VN");
        assertThat(saved.getApiKeyCiphertext()).isNotEmpty();
        assertThat(new String(saved.getApiKeyCiphertext(), java.nio.charset.StandardCharsets.ISO_8859_1))
                .doesNotContain("first-api-key");
        assertThat(saved.getApiKeyNonce()).hasSize(12);
        assertThat(saved.getSecretKeyVersion()).isEqualTo(1);
        assertThat(saved.getStatus()).isEqualTo(HolidayApiStatus.DRAFT);

        client.mode = ClientMode.SUCCESS;
        var tested = holidayApi.testDraft(draftId, adminId, 2026);
        assertThat(tested.status()).isEqualTo(HolidayApiPreviewStatus.SUCCESS);
        assertThat(tested.retrievedAt()).isNotNull();
        assertThat(tested.candidates()).containsExactly(new HolidayApiCandidate(
                "vn-tet-2026", "Tet Holiday", LocalDate.of(2026, 2, 17), LocalDate.of(2026, 2, 17), true));
        holidayApi.activate(draftId, adminId);

        long replacementId = holidayApi.saveDraft(adminId, new HolidayApiDraft("replacement-api-key"));
        assertThat(configurations.findById(draftId).orElseThrow().getStatus()).isEqualTo(HolidayApiStatus.ACTIVE);

        client.mode = ClientMode.INVALID_KEY;
        var invalid = holidayApi.testDraft(replacementId, adminId, 2026);
        assertThat(invalid.status()).isEqualTo(HolidayApiPreviewStatus.INVALID_KEY);
        assertThat(configurations.findById(replacementId).orElseThrow().getTestedAt()).isNull();
        assertThat(configurations.findById(draftId).orElseThrow().getStatus()).isEqualTo(HolidayApiStatus.ACTIVE);

        client.mode = ClientMode.SUCCESS;
        holidayApi.testDraft(replacementId, adminId, 2026);
        holidayApi.activate(replacementId, adminId);
        assertThat(configurations.findById(draftId).orElseThrow().getStatus()).isEqualTo(HolidayApiStatus.RETIRED);
        assertThat(configurations.findById(replacementId).orElseThrow().getStatus()).isEqualTo(HolidayApiStatus.ACTIVE);
        assertThat(configurations.findAllByStatus(HolidayApiStatus.ACTIVE)).hasSize(1);

        for (ClientMode mode : List.of(ClientMode.INVALID_KEY, ClientMode.RATE_LIMITED, ClientMode.UNAVAILABLE)) {
            client.mode = mode;
            var result = holidayApi.preview(adminId, 2026);
            assertThat(result.status()).isEqualTo(mode.expectedPreviewStatus);
            assertThat(result.candidates()).isEmpty();
            assertThat(result.retrievedAt()).isNull();
            assertThat(result.toString()).doesNotContain("replacement-api-key");
        }

        assertThat(holidayApi.history(adminId))
                .extracting(HolidayApiRevisionHistory::status)
                .containsExactly(HolidayApiStatus.ACTIVE, HolidayApiStatus.RETIRED);
        assertThat(holidayApi.history(adminId).toString()).doesNotContain("api-key", "ciphertext", "nonce");
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ClientConfiguration {
        @Bean
        @Primary
        RecordingHolidayApiClient recordingHolidayApiClient() {
            return new RecordingHolidayApiClient();
        }
    }

    enum ClientMode {
        SUCCESS(HolidayApiPreviewStatus.SUCCESS),
        INVALID_KEY(HolidayApiPreviewStatus.INVALID_KEY),
        RATE_LIMITED(HolidayApiPreviewStatus.RATE_LIMITED),
        UNAVAILABLE(HolidayApiPreviewStatus.UNAVAILABLE);

        private final HolidayApiPreviewStatus expectedPreviewStatus;

        ClientMode(HolidayApiPreviewStatus expectedPreviewStatus) {
            this.expectedPreviewStatus = expectedPreviewStatus;
        }
    }

    static final class RecordingHolidayApiClient extends HolidayApiHttpClient {
        private ClientMode mode = ClientMode.SUCCESS;

        RecordingHolidayApiClient() {
            super(RestClient.builder(), "http://localhost");
        }

        @Override
        public List<HolidayApiCandidate> fetch(String apiKey, String countryCode, int year) {
            assertThat(apiKey).isNotBlank();
            assertThat(countryCode).isEqualTo("VN");
            assertThat(year).isEqualTo(2026);
            return switch (mode) {
                case SUCCESS -> List.of(new HolidayApiCandidate(
                        "vn-tet-2026", "Tet Holiday", LocalDate.of(2026, 2, 17),
                        LocalDate.of(2026, 2, 17), true));
                case INVALID_KEY -> throw HolidayApiClientException.invalidKey();
                case RATE_LIMITED -> throw HolidayApiClientException.rateLimited();
                case UNAVAILABLE -> throw HolidayApiClientException.unavailable();
            };
        }
    }
}
