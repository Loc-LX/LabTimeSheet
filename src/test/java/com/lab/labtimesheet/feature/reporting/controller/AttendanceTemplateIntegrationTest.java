package com.lab.labtimesheet.feature.reporting.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lab.labtimesheet.feature.attendance.model.AttendancePolicyFixtures;
import com.lab.labtimesheet.feature.attendance.model.AttendanceViolations;
import com.lab.labtimesheet.feature.attendance.model.dto.AttendanceHistoryItem;
import com.lab.labtimesheet.feature.integration.service.SmtpConfigurationService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.stereotype.Controller;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@WebMvcTest(AttendanceTemplateIntegrationTest.TemplateController.class)
@Import(AttendanceTemplateIntegrationTest.TemplateController.class)
class AttendanceTemplateIntegrationTest {

    private final MockMvc mvc;

    @MockitoBean
    private SmtpConfigurationService smtpConfiguration;

    @Autowired
    AttendanceTemplateIntegrationTest(MockMvc mvc) {
        this.mvc = mvc;
    }

    @Test
    void internHistoryUsesSharedShellAndPreservesPunchActions() throws Exception {
        mvc.perform(get("/template-contract/attendance/history")
                        .with(user("intern@example.test").roles("INTERN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"app-shell\"")))
                .andExpect(content().string(containsString("action=\"/attendance/check-in\"")))
                .andExpect(content().string(containsString("action=\"/attendance/check-out\"")))
                .andExpect(content().string(containsString("No attendance records in this period")))
                .andExpect(content().string(containsString("src=\"/assets/theme.js\"")));
    }

    @Test
    void adminCalendarUsesSharedShellAndPreservesEventForm() throws Exception {
        mvc.perform(get("/template-contract/attendance/calendar")
                        .with(user("admin@example.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"app-shell\"")))
                .andExpect(content().string(containsString("action=\"/attendance/calendar\"")))
                .andExpect(content().string(containsString("No upcoming calendar events")))
                .andExpect(content().string(containsString("src=\"/assets/theme.js\"")));
    }

    @Test
    void populatedHistoryUsesPolicyLocalPresentationAndListsEveryViolation() throws Exception {
        mvc.perform(get("/template-contract/attendance/history/populated")
                        .with(user("intern@example.test").roles("INTERN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("14/08/2026")))
                .andExpect(content().string(containsString("09:05")))
                .andExpect(content().string(containsString("16:00")))
                .andExpect(content().string(containsString("08:30–15:30 (Asia/Ho_Chi_Minh)")))
                .andExpect(content().string(containsString("/attendance/corrections?attendanceRecordId=55")))
                .andExpect(content().string(containsString("Late")))
                .andExpect(content().string(containsString("Early departure")))
                .andExpect(content().string(containsString("Missing checkout")));
    }

    @Controller
    public static class TemplateController {

        @GetMapping("/template-contract/attendance/history")
        String history(Model model) {
            model.addAttribute("ownHistory", true);
            model.addAttribute("from", LocalDate.of(2026, 8, 1));
            model.addAttribute("to", LocalDate.of(2026, 8, 31));
            model.addAttribute("items", List.of());
            return "attendance/history";
        }

        @GetMapping("/template-contract/attendance/history/populated")
        String populatedHistory(Model model) {
            model.addAttribute("ownHistory", true);
            model.addAttribute("from", LocalDate.of(2026, 8, 1));
            model.addAttribute("to", LocalDate.of(2026, 8, 31));
            model.addAttribute("items", List.of(
                    new AttendanceHistoryItem(
                            LocalDate.of(2026, 8, 14),
                            Instant.parse("2026-08-14T02:05:00Z"),
                            Instant.parse("2026-08-14T09:00:00Z"),
                            AttendancePolicyFixtures.seeded(1L),
                            new AttendanceViolations(true, true, false),
                            55L),
                    new AttendanceHistoryItem(
                            LocalDate.of(2026, 8, 13),
                            Instant.parse("2026-08-13T01:30:00Z"),
                            null,
                            AttendancePolicyFixtures.seeded(1L),
                            new AttendanceViolations(true, false, true),
                            55L),
                    new AttendanceHistoryItem(
                            LocalDate.of(2026, 8, 12),
                            Instant.parse("2026-08-12T01:30:00Z"),
                            null,
                            AttendancePolicyFixtures.seeded(1L),
                            new AttendanceViolations(false, false, true),
                            55L)));
            return "attendance/history";
        }

        @GetMapping("/template-contract/attendance/calendar")
        String calendar(Model model) {
            model.addAttribute("today", LocalDate.of(2026, 8, 15));
            model.addAttribute("events", List.of());
            return "attendance/calendar";
        }
    }
}
