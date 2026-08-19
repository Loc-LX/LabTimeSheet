package com.lab.labtimesheet.feature.integration.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.account.service.BootstrapService;
import com.lab.labtimesheet.feature.integration.model.HolidayApiFailureKind;
import com.lab.labtimesheet.feature.integration.model.HolidayApiStatus;
import com.lab.labtimesheet.feature.integration.model.dto.HolidayApiCandidate;
import com.lab.labtimesheet.feature.integration.model.dto.HolidayApiPreview;
import com.lab.labtimesheet.feature.integration.model.dto.HolidayApiResult;
import com.lab.labtimesheet.feature.integration.model.dto.HolidayApiDraft;
import com.lab.labtimesheet.feature.integration.repository.HolidayApiConfigurationRepository;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

@Import({TestcontainersConfiguration.class, HolidayApiIntegrationTest.ProbeConfiguration.class})
@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class HolidayApiIntegrationTest {

    @Autowired
    private BootstrapService bootstrap;

    @Autowired
    private AccountService accounts;

    @Autowired
    private HolidayApiConfigurationService holidayApi;

    @Autowired
    private HolidayApiConfigurationRepository configurations;

    @Autowired
    private RecordingHolidayApiProbe probe;

    @Test
    void failedTestKeepsDraftUnactivatedThenSuccessfulRevisionRetiresThePriorActiveRevision() {
        bootstrap.bootstrap("admin@example.com", "Admin", "correct horse battery staple");
        long adminId = accounts.requireActiveAdminId("admin@example.com");

        long firstDraftId = holidayApi.saveDraft(adminId, new HolidayApiDraft("holiday-secret-1"));
        var firstDraft = configurations.findById(firstDraftId).orElseThrow();
        assertThat(firstDraft.getCountryCode()).isEqualTo("VN");
        assertThat(firstDraft.getApiKeyCiphertext())
                .isNotEqualTo("holiday-secret-1".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(firstDraft.getApiKeyNonce()).hasSize(12);
        assertThat(firstDraft.getSecretKeyVersion()).isEqualTo(1);

        probe.result = HolidayApiResult.failure(HolidayApiFailureKind.INVALID_KEY);
        assertThat(holidayApi.testDraft(firstDraftId, adminId, 2026).failure())
                .isEqualTo(HolidayApiFailureKind.INVALID_KEY);
        assertThat(configurations.findById(firstDraftId).orElseThrow().getTestedAt()).isNull();

        probe.result = HolidayApiResult.success(preview(2026));
        assertThat(holidayApi.testDraft(firstDraftId, adminId, 2026).successful()).isTrue();
        holidayApi.activate(firstDraftId, adminId);
        assertThat(configurations.findById(firstDraftId).orElseThrow().getStatus())
                .isEqualTo(HolidayApiStatus.ACTIVE);

        long replacementDraftId = holidayApi.saveDraft(adminId, new HolidayApiDraft("holiday-secret-2"));
        assertThat(configurations.findById(firstDraftId).orElseThrow().getStatus())
                .isEqualTo(HolidayApiStatus.ACTIVE);
        assertThat(configurations.findById(replacementDraftId).orElseThrow().getStatus())
                .isEqualTo(HolidayApiStatus.DRAFT);

        assertThat(holidayApi.preview(2026).successful()).isTrue();
        holidayApi.testDraft(replacementDraftId, adminId, 2026);
        holidayApi.activate(replacementDraftId, adminId);

        assertThat(configurations.findById(firstDraftId).orElseThrow().getStatus())
                .isEqualTo(HolidayApiStatus.RETIRED);
        assertThat(configurations.findById(replacementDraftId).orElseThrow().getStatus())
                .isEqualTo(HolidayApiStatus.ACTIVE);
        assertThat(configurations.findAll().stream()
                .filter(configuration -> configuration.getStatus() == HolidayApiStatus.ACTIVE))
                .hasSize(1);
    }

    private static HolidayApiPreview preview(int year) {
        return new HolidayApiPreview(year, "VN", List.of(new HolidayApiCandidate(
                "uuid-1", "National Day", LocalDate.of(year, 9, 2), null, true)));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ProbeConfiguration {
        @Bean
        @Primary
        RecordingHolidayApiProbe recordingHolidayApiProbe() {
            return new RecordingHolidayApiProbe();
        }
    }

    static final class RecordingHolidayApiProbe implements HolidayApiProbe {
        private HolidayApiResult result = HolidayApiResult.failure(HolidayApiFailureKind.UNAVAILABLE);

        @Override
        public HolidayApiResult preview(String apiKey, int year) {
            assertThat(year).isBetween(2000, 2100);
            return result;
        }
    }
}
