package com.lab.labtimesheet.feature.reporting.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lab.labtimesheet.feature.integration.service.SmtpConfigurationService;
import com.lab.labtimesheet.feature.reporting.model.dto.DashboardView;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.stereotype.Controller;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@WebMvcTest(DashboardTemplateWebTest.TemplateController.class)
@Import(DashboardTemplateWebTest.TemplateController.class)
class DashboardTemplateWebTest {

    private final MockMvc mvc;

    @MockitoBean
    private SmtpConfigurationService smtpConfiguration;

    @Autowired
    DashboardTemplateWebTest(MockMvc mvc) {
        this.mvc = mvc;
    }

    @Test
    @WithMockUser(username = "admin@example.test", roles = "ADMIN")
    void adminTemplateRendersSystemMetricsAndOnlyAdminAction() throws Exception {
        mvc.perform(get("/template-contract/dashboard/admin"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("System overview")))
                .andExpect(content().string(containsString("Active accounts</div><div class=\"metric-value\">3")))
                .andExpect(content().string(containsString("Create account")))
                .andExpect(content().string(not(containsString("Create Project"))))
                .andExpect(content().string(not(containsString("Check in"))));
    }

    @Test
    @WithMockUser(username = "mentor@example.test", roles = "MENTOR")
    void mentorTemplateRendersOwnedScopeAndOnlyMentorAction() throws Exception {
        mvc.perform(get("/template-contract/dashboard/mentor"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Good morning, Minh Mentor")))
                .andExpect(content().string(containsString("Active owned Projects</div><div class=\"metric-value\">1")))
                .andExpect(content().string(containsString("Blocked Tasks</div><div class=\"metric-value\">1")))
                .andExpect(content().string(not(containsString("Pending decisions"))))
                .andExpect(content().string(containsString("Create Project")))
                .andExpect(content().string(not(containsString("Create account"))))
                .andExpect(content().string(not(containsString("Check in"))));
    }

    @Test
    @WithMockUser(username = "intern@example.test", roles = "INTERN")
    void internTemplateRendersOwnWorkAndAttendanceAction() throws Exception {
        mvc.perform(get("/template-contract/dashboard/intern"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Today")))
                .andExpect(content().string(containsString("Checked in")))
                .andExpect(content().string(containsString("My real task")))
                .andExpect(content().string(containsString("18/08/2026")))
                .andExpect(content().string(containsString("Check out")))
                .andExpect(content().string(not(containsString("Unread notifications"))))
                .andExpect(content().string(not(containsString("Create Project"))))
                .andExpect(content().string(not(containsString("Create account"))));
    }

    @Controller
    public static class TemplateController {

        @GetMapping("/template-contract/dashboard/admin")
        String admin(Model model) {
            model.addAttribute("dashboard", new DashboardView.Admin(3, 1, 2, 1));
            return "dashboard/admin";
        }

        @GetMapping("/template-contract/dashboard/mentor")
        String mentor(Model model) {
            model.addAttribute("dashboard", new DashboardView.Mentor("Minh Mentor", 1, 2, 1));
            return "dashboard/mentor";
        }

        @GetMapping("/template-contract/dashboard/intern")
        String intern(Model model) {
            model.addAttribute("dashboard", new DashboardView.Intern(
                    "Mai Intern",
                    DashboardView.AttendanceState.CHECKED_IN,
                    1,
                    1,
                    List.of(new DashboardView.AssignedTask(
                            "My real task", "Intern Portal", "IN_PROGRESS", LocalDate.of(2026, 8, 18)))));
            return "dashboard/intern";
        }
    }
}
