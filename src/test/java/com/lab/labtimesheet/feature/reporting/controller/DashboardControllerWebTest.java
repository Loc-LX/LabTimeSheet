package com.lab.labtimesheet.feature.reporting.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.lab.labtimesheet.feature.integration.service.SmtpConfigurationService;
import com.lab.labtimesheet.feature.reporting.model.dto.DashboardView;
import com.lab.labtimesheet.feature.reporting.service.DashboardService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DashboardController.class)
class DashboardControllerWebTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private DashboardService dashboards;

    @MockitoBean
    private SmtpConfigurationService smtpConfiguration;

    @Test
    void adminRendersAdminDashboardForAuthenticatedIdentity() throws Exception {
        var dashboard = new DashboardView.Admin(2, 1, 1, 3);
        given(dashboards.admin("admin@example.test")).willReturn(dashboard);
        given(smtpConfiguration.hasActiveConfiguration()).willReturn(false);

        mvc.perform(get("/dashboard").with(user("admin@example.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard/admin"))
                .andExpect(model().attribute("dashboard", dashboard))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "This installation remains restricted until tested SMTP is active.")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("href=\"/admin/smtp\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "data-tooltip=\"SMTP settings\"")));

        verify(dashboards).admin("admin@example.test");
    }

    @Test
    void activeSmtpKeepsAdminDashboardFreeOfTheRestrictedInstallationWarning() throws Exception {
        var dashboard = new DashboardView.Admin(2, 1, 1, 3);
        given(dashboards.admin("admin@example.test")).willReturn(dashboard);
        given(smtpConfiguration.hasActiveConfiguration()).willReturn(true);

        mvc.perform(get("/dashboard").with(user("admin@example.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(
                        "This installation remains restricted until tested SMTP is active."))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "href=\"/admin/smtp\" data-tooltip=\"SMTP settings\"")));
    }

    @Test
    void mentorRendersMentorDashboardForAuthenticatedIdentity() throws Exception {
        var dashboard = new DashboardView.Mentor("Mentor", 2, 4, 1);
        given(dashboards.mentor("mentor@example.test")).willReturn(dashboard);
        given(smtpConfiguration.hasActiveConfiguration()).willReturn(true);

        mvc.perform(get("/dashboard").with(user("mentor@example.test").roles("MENTOR")))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard/mentor"))
                .andExpect(model().attribute("dashboard", dashboard))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(
                        "This installation remains restricted until tested SMTP is active."))))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(
                        "data-tooltip=\"SMTP settings\""))));

        verify(dashboards).mentor("mentor@example.test");
    }

    @Test
    void internRendersInternDashboardWithoutClientSuppliedBusinessDate() throws Exception {
        var dashboard = new DashboardView.Intern(
                "Intern", DashboardView.AttendanceState.NOT_CHECKED_IN, 1, 0, List.of());
        given(dashboards.intern("intern@example.test")).willReturn(dashboard);
        given(smtpConfiguration.hasActiveConfiguration()).willReturn(true);

        mvc.perform(get("/dashboard").with(user("intern@example.test").roles("INTERN")))
                .andExpect(status().isOk())
                .andExpect(view().name("dashboard/intern"))
                .andExpect(model().attribute("dashboard", dashboard))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(
                        "data-tooltip=\"SMTP settings\""))));

        verify(dashboards).intern("intern@example.test");
    }

    @Test
    void unsupportedRoleIsForbiddenWithoutCallingDashboardServices() throws Exception {
        mvc.perform(get("/dashboard").with(user("user@example.test").roles("USER")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(dashboards);
    }

    @Test
    void dashboardRequiresAuthentication() throws Exception {
        mvc.perform(get("/dashboard"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(dashboards);
    }
}
