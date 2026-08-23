package com.lab.labtimesheet.feature.reporting.controller;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRole;
import com.lab.labtimesheet.feature.attendance.model.AttendancePolicyFixtures;
import com.lab.labtimesheet.feature.attendance.model.AttendanceViolations;
import com.lab.labtimesheet.feature.attendance.model.LeaveStatus;
import com.lab.labtimesheet.feature.attendance.model.dto.AttendanceHistoryItem;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionSummary;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveBalance;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveRequestSummary;
import com.lab.labtimesheet.feature.integration.service.SmtpConfigurationService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
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
    void focusedAdminPolicyUsesNativeMonthFormAndRetainedHistoryPanel() throws Exception {
        mvc.perform(get("/template-contract/attendance/policy")
                        .with(user("admin@example.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("action=\"/admin/attendance-policies\"")))
                .andExpect(content().string(containsString("id=\"effective-month\"")))
                .andExpect(content().string(containsString("Policy History")));
    }

    @Test
    void leaveAndCorrectionPagesRemainSeparateAndHumanizeStatuses() throws Exception {
        mvc.perform(get("/template-contract/attendance/leave")
                        .with(user("intern@example.test").roles("INTERN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("My Leave")))
                .andExpect(content().string(containsString("My Corrections")))
                .andExpect(content().string(containsString("3 reserved / 12 quota / 9 remaining")))
                .andExpect(content().string(containsString("Pending decision")));

        mvc.perform(get("/template-contract/attendance/corrections")
                        .with(user("mentor@example.test").roles("MENTOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Correction decisions")))
                .andExpect(content().string(containsString("Leave decisions")))
                .andExpect(content().string(containsString("Pending decision")));
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

        @GetMapping("/template-contract/attendance/policy")
        String policy(Model model) {
            model.addAttribute("minimumPolicyMonth", YearMonth.of(2026, 9));
            model.addAttribute("policyHistory", List.of());
            return "attendance/policies";
        }

        @GetMapping("/template-contract/attendance/leave")
        String leave(Model model) {
            model.addAttribute("actor", new AttendanceActor(7L, AttendanceRole.INTERN));
            model.addAttribute("intern", true);
            model.addAttribute("mentor", false);
            model.addAttribute("leaveRequests", List.of(new LeaveRequestSummary(
                    11L, 7L, LocalDate.of(2026, 8, 20), LocalDate.of(2026, 8, 21),
                    "Family", LeaveStatus.PENDING, Instant.parse("2026-08-19T00:00:00Z"))));
            model.addAttribute("selectedMonth", YearMonth.of(2026, 8));
            model.addAttribute("balance", new LeaveBalance(YearMonth.of(2026, 8), 3, 12));
            return "attendance/leave";
        }

        @GetMapping("/template-contract/attendance/corrections")
        String corrections(Model model) {
            model.addAttribute("actor", new AttendanceActor(2L, AttendanceRole.MENTOR));
            model.addAttribute("intern", false);
            model.addAttribute("mentor", true);
            model.addAttribute("correctionRequests", List.of(new CorrectionSummary(
                    12L, 55L, 7L, Instant.parse("2026-08-20T09:00:00Z"),
                    "Missed checkout", "PENDING", Instant.parse("2026-08-20T10:00:00Z"),
                    Instant.parse("2026-08-21T10:00:00Z"))));
            return "attendance/corrections";
        }
    }
}
