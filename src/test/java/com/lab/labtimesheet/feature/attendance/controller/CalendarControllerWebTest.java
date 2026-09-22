package com.lab.labtimesheet.feature.attendance.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lab.labtimesheet.feature.attendance.exception.CalendarException;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRole;
import com.lab.labtimesheet.feature.attendance.service.AttendanceApplicationService;
import com.lab.labtimesheet.feature.attendance.service.AttendanceCurrentUserService;
import com.lab.labtimesheet.feature.attendance.service.CalendarApplicationService;
import com.lab.labtimesheet.platform.service.SmtpConfigurationService;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Browser binding contract for retained manual-calendar input and safe conflicts. */
@WebMvcTest(CalendarController.class)
class CalendarControllerWebTest {

    private static final AttendanceActor ADMIN = new AttendanceActor(1L, AttendanceRole.ADMIN);

    @Autowired
    private MockMvc mvc;

    @MockitoBean private CalendarApplicationService calendar;
    @MockitoBean private AttendanceApplicationService attendance;
    @MockitoBean private AttendanceCurrentUserService currentUsers;
    @MockitoBean private SmtpConfigurationService smtpConfiguration;

    @Test
    void malformedCreateDateRetainsSafeInputWithoutCallingTheService() throws Exception {
        when(currentUsers.actor(org.mockito.ArgumentMatchers.any())).thenReturn(ADMIN);

        mvc.perform(post("/attendance/calendar")
                        .with(user("admin@example.test").roles("ADMIN"))
                        .with(csrf())
                        .param("date", "not-a-date")
                        .param("name", "Retained event")
                        .param("dayOff", "true"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/attendance/calendar"))
                .andExpect(flash().attribute("calendarError", "Enter valid calendar event values."))
                .andExpect(flash().attribute("calendarInput", org.hamcrest.Matchers.hasEntry(
                        "name", "Retained event")));

        verifyNoInteractions(calendar);
    }

    @Test
    void serviceConflictRetainsSafeUpdateInputForARetry() throws Exception {
        when(currentUsers.actor(org.mockito.ArgumentMatchers.any())).thenReturn(ADMIN);
        when(calendar.updateManual(
                        ADMIN, 9L, 3L, LocalDate.of(2026, 9, 2), "Retained rename", false))
                .thenThrow(new CalendarException("Calendar event was changed by another request"));

        mvc.perform(post("/attendance/calendar/9")
                        .with(user("admin@example.test").roles("ADMIN"))
                        .with(csrf())
                        .param("version", "3")
                        .param("date", "2026-09-02")
                        .param("name", "Retained rename")
                        .param("dayOff", "false"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/attendance/calendar"))
                .andExpect(flash().attribute(
                        "calendarError", "Calendar event was changed by another request"))
                .andExpect(flash().attribute("calendarEditInput", org.hamcrest.Matchers.hasEntry(
                        "name", "Retained rename")));
    }
}
