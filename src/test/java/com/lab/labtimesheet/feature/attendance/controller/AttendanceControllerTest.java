package com.lab.labtimesheet.feature.attendance.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;

import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.AttendancePolicy;
import com.lab.labtimesheet.feature.attendance.model.AttendancePolicyFixtures;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRole;
import com.lab.labtimesheet.feature.attendance.model.AttendanceViolations;
import com.lab.labtimesheet.feature.attendance.model.dto.AttendanceHistoryItem;
import com.lab.labtimesheet.feature.attendance.model.dto.GlobalCalendarEvent;
import com.lab.labtimesheet.feature.attendance.service.AttendanceApplicationService;
import com.lab.labtimesheet.feature.attendance.service.AttendanceCurrentUserService;
import com.lab.labtimesheet.feature.attendance.service.CalendarApplicationService;
import com.lab.labtimesheet.platform.service.SmtpConfigurationService;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest({AttendanceController.class, CalendarController.class})
class AttendanceControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AttendanceApplicationService attendance;

    @MockitoBean
    private CalendarApplicationService calendar;

    @MockitoBean
    private AttendanceCurrentUserService currentUsers;

    @MockitoBean
    private SmtpConfigurationService smtpConfiguration;

    @Test
    void internPunchesOnlyForAuthenticatedSelf() throws Exception {
        AttendanceActor actor = new AttendanceActor(42L, AttendanceRole.INTERN);
        when(currentUsers.actor(any())).thenReturn(actor);

        mockMvc.perform(post("/attendance/check-in")
                        .with(user("intern@example.test").roles("INTERN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/attendance"));

        verify(attendance).checkIn(42L);
    }

    @Test
    void ownHistoryRendersAttachedHistoricalPolicy() throws Exception {
        AttendanceActor actor = new AttendanceActor(42L, AttendanceRole.INTERN);
        when(currentUsers.actor(any())).thenReturn(actor);
        when(attendance.history(eq(actor), eq(42L), any(), any())).thenReturn(List.of(new AttendanceHistoryItem(
                LocalDate.of(2026, 8, 14),
                Instant.parse("2026-08-14T02:00:00Z"),
                Instant.parse("2026-08-14T09:00:00Z"),
                AttendancePolicyFixtures.seeded(1L),
                new AttendanceViolations(false, false, false))));

        mockMvc.perform(get("/attendance")
                        .with(user("intern@example.test").roles("INTERN"))
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-31"))
                .andExpect(status().isOk())
                .andExpect(view().name("attendance/history"))
                .andExpect(model().attribute("targetInternId", 42L))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("30 min")));
    }

    @Test
    void historyRendersPolicyLocalDisplayValuesAndEveryViolation() throws Exception {
        AttendanceActor actor = new AttendanceActor(42L, AttendanceRole.INTERN);
        when(currentUsers.actor(any())).thenReturn(actor);
        when(attendance.history(eq(actor), eq(42L), any(), any())).thenReturn(List.of(new AttendanceHistoryItem(
                LocalDate.of(2026, 8, 14),
                Instant.parse("2026-08-14T02:00:00.001Z"),
                Instant.parse("2026-08-14T08:00:00Z"),
                AttendancePolicyFixtures.seeded(1L),
                new AttendanceViolations(true, true, false))));

        mockMvc.perform(get("/attendance")
                        .with(user("intern@example.test").roles("INTERN"))
                        .param("from", "2026-08-01")
                        .param("to", "2026-08-31"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("14/08/2026")))
                .andExpect(content().string(containsString("09:00")))
                .andExpect(content().string(containsString("15:00")))
                .andExpect(content().string(containsString("Late")))
                .andExpect(content().string(containsString("Early departure")))
                .andExpect(content().string(not(containsString("On time"))));
    }

    @Test
    void mentorCanInspectInternHistory() throws Exception {
        AttendanceActor mentor = new AttendanceActor(7L, AttendanceRole.MENTOR);
        when(currentUsers.actor(any())).thenReturn(mentor);
        when(attendance.currentBusinessDate()).thenReturn(LocalDate.of(2026, 8, 14));
        when(attendance.history(eq(mentor), eq(42L), any(), any())).thenReturn(List.of());

        mockMvc.perform(get("/attendance/interns/42")
                        .with(user("mentor@example.test").roles("MENTOR")))
                .andExpect(status().isOk())
                .andExpect(view().name("attendance/history"));

        verify(attendance).history(
                mentor, 42L, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 14));
    }

    @Test
    void onlyAdminCanOpenCalendarManagement() throws Exception {
        when(currentUsers.actor(any())).thenReturn(new AttendanceActor(7L, AttendanceRole.MENTOR));

        mockMvc.perform(get("/attendance/calendar")
                        .with(user("mentor@example.test").roles("MENTOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCreatesManualDayOffFromServerAuthorizedIdentity() throws Exception {
        AttendanceActor admin = new AttendanceActor(1L, AttendanceRole.ADMIN);
        when(currentUsers.actor(any())).thenReturn(admin);

        mockMvc.perform(post("/attendance/calendar")
                        .with(user("admin@example.test").roles("ADMIN"))
                        .with(csrf())
                        .param("date", "2026-08-20")
                        .param("name", "Lab closure")
                        .param("dayOff", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/attendance/calendar"));

        verify(calendar).createManual(admin, LocalDate.of(2026, 8, 20), "Lab closure", true);
    }

    @Test
    void adminCalendarRendersEditableVersionedEvents() throws Exception {
        AttendanceActor admin = new AttendanceActor(1L, AttendanceRole.ADMIN);
        when(currentUsers.actor(any())).thenReturn(admin);
        when(attendance.currentBusinessDate()).thenReturn(LocalDate.of(2026, 8, 14));
        when(calendar.list(LocalDate.of(2026, 8, 14), LocalDate.of(2027, 8, 14)))
                .thenReturn(List.of(new GlobalCalendarEvent(
                        9L, LocalDate.of(2026, 8, 20), "Lab closure", true, 3L)));

        mockMvc.perform(get("/attendance/calendar")
                        .with(user("admin@example.test").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(view().name("attendance/calendar"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Lab closure")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("value=\"3\"")));
    }

    @Test
    void adminUpdateCarriesOptimisticVersion() throws Exception {
        AttendanceActor admin = new AttendanceActor(1L, AttendanceRole.ADMIN);
        when(currentUsers.actor(any())).thenReturn(admin);

        mockMvc.perform(post("/attendance/calendar/9")
                        .with(user("admin@example.test").roles("ADMIN"))
                        .with(csrf())
                        .param("version", "3")
                        .param("date", "2026-08-20")
                        .param("name", "Lab closure")
                        .param("dayOff", "true"))
                .andExpect(status().is3xxRedirection());

        verify(calendar).updateManual(
                admin, 9L, 3L, LocalDate.of(2026, 8, 20), "Lab closure", true);
    }
}
