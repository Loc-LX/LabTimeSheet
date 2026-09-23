package com.lab.labtimesheet.feature.attendance.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lab.labtimesheet.feature.attendance.model.dto.AttendancePolicyHistoryItem;
import com.lab.labtimesheet.feature.attendance.service.AttendanceApplicationService;
import com.lab.labtimesheet.feature.attendance.service.AttendancePolicyApplicationService;
import com.lab.labtimesheet.feature.identity.service.AccountService;
import com.lab.labtimesheet.platform.service.SmtpConfigurationService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Web contract for the focused Admin Attendance Policy workflow. */
@WebMvcTest(AttendancePolicyController.class)
class AttendancePolicyControllerWebTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AccountService accounts;

    @MockitoBean
    private AttendanceApplicationService attendance;

    @MockitoBean
    private AttendancePolicyApplicationService policies;

    @MockitoBean
    private SmtpConfigurationService smtpConfiguration;

    @Test
    void nativeFutureMonthBindsToTheFirstDayAndRendersHistory() throws Exception {
        when(accounts.requireActiveAdminId("admin@example.test")).thenReturn(1L);
        when(attendance.currentBusinessDate()).thenReturn(LocalDate.of(2026, 8, 21));
        when(policies.history(1L)).thenReturn(List.of());

        mvc.perform(get("/admin/attendance-policies")
                        .with(user("admin@example.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("name=\"effectiveMonth\"")))
                .andExpect(content().string(containsString("type=\"month\"")));

        mvc.perform(post("/admin/attendance-policies")
                        .with(user("admin@example.test").roles("ADMIN"))
                        .with(csrf())
                        .param("effectiveMonth", "2026-09")
                        .param("zoneId", "Asia/Ho_Chi_Minh")
                        .param("scheduledStart", "08:30")
                        .param("scheduledEnd", "15:30")
                        .param("checkInGraceMinutes", "30")
                        .param("checkoutGraceMinutes", "30")
                        .param("monthlyLeaveQuota", "3")
                        .param("violationPenalty", "0.25")
                        .param("workdays", "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/attendance-policies"));

        verify(policies).schedule(eq(1L), argThat(command ->
                command.effectiveFrom().equals(LocalDate.of(2026, 9, 1))));
    }

    @Test
    void craftedArbitraryDateCannotBypassTheMonthBoundary() throws Exception {
        when(accounts.requireActiveAdminId("admin@example.test")).thenReturn(1L);

        mvc.perform(post("/admin/attendance-policies")
                        .with(user("admin@example.test").roles("ADMIN"))
                        .with(csrf())
                        .param("effectiveMonth", "2026-09-15")
                        .param("zoneId", "Asia/Ho_Chi_Minh")
                        .param("scheduledStart", "08:30")
                        .param("scheduledEnd", "15:30")
                        .param("checkInGraceMinutes", "30")
                        .param("checkoutGraceMinutes", "30")
                        .param("monthlyLeaveQuota", "3")
                        .param("violationPenalty", "0.25")
                        .param("workdays", "MONDAY"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/attendance-policies"));

        verifyNoInteractions(policies);
    }

    @Test
    void nonAdminCannotOpenFocusedPolicyWorkflow() throws Exception {
        when(accounts.requireActiveAdminId("mentor@example.test"))
                .thenThrow(new IllegalArgumentException("An active Admin is required"));
        mvc.perform(get("/admin/attendance-policies")
                        .with(user("mentor@example.test").roles("MENTOR")))
                .andExpect(status().isForbidden());
    }
}
