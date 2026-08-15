package com.lab.labtimesheet.feature.attendance.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import java.util.TimeZone;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "LAB_SMTP_HOST=localhost",
        "LAB_SMTP_PORT=1025",
        "LAB_SERVER_PORT=0",
        "LAB_FORWARD_HEADERS_STRATEGY=none",
        "LAB_PUBLIC_ORIGIN=http://localhost:8080",
        "LAB_SECURITY_MASTER_KEY=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8="
})
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "test"})
@ContextConfiguration(initializers = CalendarDevelopmentProfileWebIntegrationTest.AsiaHoChiMinhTimeZoneInitializer.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CalendarDevelopmentProfileWebIntegrationTest {
    private static final String ADMIN_EMAIL = "admin@example.test";
    private static final String PASSWORD = "correct horse battery staple";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void v1SeededPolicyLetsFormAuthenticatedAdminOpenCalendarInAsiaHoChiMinhDevelopmentProfile() throws Exception {
        mockMvc.perform(post("/bootstrap")
                        .with(csrf())
                        .param("email", ADMIN_EMAIL)
                        .param("displayName", "Admin")
                        .param("password", PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/smtp?onboarding"));

        var login = mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", ADMIN_EMAIL)
                        .param("password", PASSWORD))
                .andExpect(status().is3xxRedirection())
                .andExpect(authenticated().withUsername(ADMIN_EMAIL))
                .andReturn();

        mockMvc.perform(get("/attendance/calendar")
                        .session((MockHttpSession) login.getRequest().getSession(false)))
                .andExpect(status().isOk());
    }

    @AfterAll
    static void restoreSystemDefaultTimeZone() {
        TimeZone.setDefault(AsiaHoChiMinhTimeZoneInitializer.originalDefaultTimeZone());
    }

    static final class AsiaHoChiMinhTimeZoneInitializer
            implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        private static final TimeZone ORIGINAL_DEFAULT_TIME_ZONE = TimeZone.getDefault();

        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            TimeZone.setDefault(TimeZone.getTimeZone("Asia/Ho_Chi_Minh"));
        }

        static TimeZone originalDefaultTimeZone() {
            return ORIGINAL_DEFAULT_TIME_ZONE;
        }
    }
}
