package com.lab.labtimesheet.feature.integration.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.account.service.BootstrapService;
import com.lab.labtimesheet.feature.integration.model.HolidayApiFailureKind;
import com.lab.labtimesheet.feature.integration.model.dto.HolidayApiCandidate;
import com.lab.labtimesheet.feature.integration.model.dto.HolidayApiPreview;
import com.lab.labtimesheet.feature.integration.model.dto.HolidayApiResult;
import com.lab.labtimesheet.feature.integration.repository.HolidayApiConfigurationRepository;
import com.lab.labtimesheet.feature.integration.service.HolidayApiProbe;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@Import({TestcontainersConfiguration.class, HolidayApiWebIntegrationTest.ProbeConfiguration.class})
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class HolidayApiWebIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BootstrapService bootstrap;

    @Autowired
    private AccountService accounts;

    @Autowired
    private RecordingHolidayApiProbe probe;

    @Autowired
    private HolidayApiConfigurationRepository configurations;

    @BeforeEach
    void initializeAdmin() {
        bootstrap.bootstrap("admin@example.com", "Admin", "correct horse battery staple");
    }

    @Test
    void adminCanSaveTestActivateAndPreviewWithoutRedisplayingTheSecret() throws Exception {
        var admin = user("admin@example.com").roles("ADMIN");
        String secret = "holiday-secret-web";

        mockMvc.perform(get("/admin/holiday-api").with(admin))
                .andExpect(status().isOk())
                .andExpect(view().name("holiday-api/form"))
                .andExpect(content().string(containsString("HolidayAPI configuration")))
                .andExpect(content().string(not(containsString(secret))));

        mockMvc.perform(post("/admin/holiday-api/draft")
                        .with(admin)
                        .with(csrf())
                        .param("apiKey", secret))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/holiday-api?saved"));

        long adminId = accounts.requireActiveAdminId("admin@example.com");
        long draftId = configurations.findByStatus(com.lab.labtimesheet.feature.integration.model.HolidayApiStatus.DRAFT)
                .orElseThrow()
                .getId();
        assertThat(configurations.findById(draftId).orElseThrow().getApiKeyCiphertext())
                .isNotEqualTo(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8));

        mockMvc.perform(get("/admin/holiday-api").with(admin))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Test draft")))
                .andExpect(content().string(not(containsString(secret))));

        probe.result = HolidayApiResult.failure(HolidayApiFailureKind.INVALID_KEY);
        mockMvc.perform(post("/admin/holiday-api/test")
                        .with(admin)
                        .with(csrf())
                        .param("draftId", String.valueOf(draftId))
                        .param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(view().name("holiday-api/form"))
                .andExpect(content().string(containsString("rejected the configured key")))
                .andExpect(content().string(not(containsString(secret))));
        assertThat(configurations.findById(draftId).orElseThrow().getTestedAt()).isNull();

        probe.result = HolidayApiResult.success(preview(2026));
        mockMvc.perform(post("/admin/holiday-api/test")
                        .with(admin)
                        .with(csrf())
                        .param("draftId", String.valueOf(draftId))
                        .param("year", "2026"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/holiday-api?tested"));

        mockMvc.perform(post("/admin/holiday-api/activate")
                        .with(admin)
                        .with(csrf())
                        .param("draftId", String.valueOf(draftId)))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/holiday-api?activated"));

        mockMvc.perform(post("/admin/holiday-api/preview")
                        .with(admin)
                        .with(csrf())
                        .param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(view().name("holiday-api/form"))
                .andExpect(content().string(containsString("National Day")))
                .andExpect(content().string(not(containsString(secret))));

        assertThat(accounts.requireActiveAdminId("admin@example.com")).isEqualTo(adminId);
    }

    @Test
    void HolidayApiSetupIsAdminOnlyAndStateChangingPostsRequireCsrf() throws Exception {
        mockMvc.perform(get("/admin/holiday-api").with(user("mentor@example.com").roles("MENTOR")))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/admin/holiday-api/draft")
                        .with(user("admin@example.com").roles("ADMIN"))
                        .param("apiKey", "holiday-secret-no-csrf"))
                .andExpect(status().isForbidden());
    }

    private static HolidayApiPreview preview(int year) {
        return new HolidayApiPreview(year, "VN", List.of(new HolidayApiCandidate(
                "uuid-web-1", "National Day", LocalDate.of(year, 9, 2), null, true)));
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
            assertThat(apiKey).isNotBlank();
            assertThat(year).isBetween(2000, 2100);
            return result;
        }
    }
}
