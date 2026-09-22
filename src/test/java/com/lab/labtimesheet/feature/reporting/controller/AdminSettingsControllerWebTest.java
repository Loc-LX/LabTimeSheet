package com.lab.labtimesheet.feature.reporting.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lab.labtimesheet.feature.identity.service.AccountService;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.AttendancePolicy;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRole;
import com.lab.labtimesheet.feature.attendance.model.dto.AttendancePolicyHistoryItem;
import com.lab.labtimesheet.feature.attendance.model.dto.CalendarHistoryItem;
import com.lab.labtimesheet.feature.attendance.model.dto.CalendarImportSelection;
import com.lab.labtimesheet.feature.attendance.model.dto.CalendarPreviewItem;
import com.lab.labtimesheet.feature.attendance.service.AttendanceApplicationService;
import com.lab.labtimesheet.feature.attendance.service.AttendanceCurrentUserService;
import com.lab.labtimesheet.feature.attendance.service.AttendancePolicyApplicationService;
import com.lab.labtimesheet.feature.attendance.service.CalendarApplicationService;
import com.lab.labtimesheet.feature.integration.model.HolidayApiStatus;
import com.lab.labtimesheet.feature.integration.model.dto.HolidayApiCandidate;
import com.lab.labtimesheet.feature.integration.model.dto.HolidayApiPreview;
import com.lab.labtimesheet.feature.integration.model.dto.HolidayApiPreviewStatus;
import com.lab.labtimesheet.feature.integration.model.dto.HolidayApiRevisionHistory;
import com.lab.labtimesheet.feature.integration.model.dto.HolidayApiSetupStatus;
import com.lab.labtimesheet.feature.integration.service.HolidayApiConfigurationService;
import com.lab.labtimesheet.platform.model.SecurityMode;
import com.lab.labtimesheet.platform.model.SmtpStatus;
import com.lab.labtimesheet.platform.model.dto.SmtpRevisionHistory;
import com.lab.labtimesheet.platform.service.SmtpConfigurationService;
import java.time.Instant;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Admin-only web contract for policy, calendar, SMTP, and HolidayAPI setup History. */
@WebMvcTest(AdminSettingsController.class)
class AdminSettingsControllerWebTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean private AccountService accounts;
    @MockitoBean private AttendanceCurrentUserService currentUsers;
    @MockitoBean private AttendanceApplicationService attendance;
    @MockitoBean private AttendancePolicyApplicationService policies;
    @MockitoBean private CalendarApplicationService calendar;
    @MockitoBean private HolidayApiConfigurationService holidayApi;
    @MockitoBean private SmtpConfigurationService smtp;

    @Test
    void adminOpensAllFourRedactedHistoriesAndNonAdminIsDenied() throws Exception {
        AttendanceActor actor = adminActor();
        when(currentUsers.actor(org.mockito.ArgumentMatchers.any())).thenReturn(actor);
        when(accounts.requireActiveAdminId("admin@example.test")).thenReturn(1L);
        when(attendance.currentBusinessDate()).thenReturn(LocalDate.of(2026, 8, 21));
        when(holidayApi.setupStatus(1L)).thenReturn(new HolidayApiSetupStatus(false, null, false, "VN"));
        Instant now = Instant.parse("2026-08-21T00:00:00Z");
        AttendancePolicy policy = new AttendancePolicy(
                1L, LocalDate.of(2026, 8, 1), ZoneId.of("Asia/Ho_Chi_Minh"),
                LocalTime.of(8, 0), LocalTime.of(17, 0), 15, 15, 3,
                BigDecimal.valueOf(0.1), Set.of(DayOfWeek.MONDAY));
        when(policies.history(actor)).thenReturn(List.of(new AttendancePolicyHistoryItem(
                1L, LocalDate.of(2026, 8, 1), policy, 1L, now, now, 0L)));
        when(calendar.history(actor)).thenReturn(List.of(new CalendarHistoryItem(
                1L, LocalDate.of(2026, 9, 2), "National Day", "HOLIDAY_API", "uuid-1",
                LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 2), true, true, now,
                1L, 1L, now, now, 0L)));
        when(smtp.history(1L)).thenReturn(List.of(new SmtpRevisionHistory(
                1L, SmtpStatus.ACTIVE, "mailpit", 1025, SecurityMode.NONE, null,
                "sender@example.test", "Lab", now, 1L, now, 1L, null, null, 1L, now, now)));
        when(holidayApi.history(1L)).thenReturn(List.of(new HolidayApiRevisionHistory(
                1L, HolidayApiStatus.ACTIVE, "VN", now, 1L, now, 1L,
                null, null, 1L, now, now)));

        TimeZone previousZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        try {
            mvc.perform(get("/admin/settings").with(user("admin@example.test").roles("ADMIN")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("Policy History")))
                    .andExpect(content().string(containsString("Calendar History")))
                    .andExpect(content().string(containsString("SMTP History")))
                    .andExpect(content().string(containsString("HolidayAPI History")))
                    .andExpect(content().string(containsString("01/08/2026")))
                    .andExpect(content().string(containsString("02/09/2026")))
                    .andExpect(content().string(containsString("21/08/2026 07:00")))
                    .andExpect(content().string(not(containsString("super-secret"))));
        } finally {
            TimeZone.setDefault(previousZone);
        }

        when(currentUsers.actor(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new AttendanceActor(2L, AttendanceRole.MENTOR));
        mvc.perform(get("/admin/settings").with(user("mentor@example.test").roles("MENTOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    void explicitPreviewRendersProviderCandidateWithIndependentLocalDecision() throws Exception {
        AttendanceActor actor = adminActor();
        when(currentUsers.actor(org.mockito.ArgumentMatchers.any())).thenReturn(actor);
        when(accounts.requireActiveAdminId("admin@example.test")).thenReturn(1L);
        when(attendance.currentBusinessDate()).thenReturn(LocalDate.of(2026, 8, 21));
        when(holidayApi.setupStatus(1L)).thenReturn(new HolidayApiSetupStatus(true, null, false, "VN"));
        HolidayApiCandidate candidate = new HolidayApiCandidate(
                "uuid-1", "National Day", LocalDate.of(2026, 9, 2), LocalDate.of(2026, 9, 2), true);
        HolidayApiPreview preview = new HolidayApiPreview(
                HolidayApiPreviewStatus.SUCCESS, List.of(candidate), "Available", Instant.parse("2026-08-21T00:00:00Z"));
        when(calendar.previewFromProvider(actor, 2026)).thenReturn(preview);
        when(calendar.preview(actor, 2026, preview)).thenReturn(List.of(
                new CalendarPreviewItem(candidate, true, preview.retrievedAt())));

        mvc.perform(post("/admin/settings/calendar/preview")
                        .with(user("admin@example.test").roles("ADMIN")).with(csrf())
                        .param("year", "2026"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("National Day")))
                .andExpect(content().string(containsString("Import as day off")))
                .andExpect(content().string(containsString("Import as working day")));

        verify(calendar).previewFromProvider(actor, 2026);
    }

    @Test
    void calendarImportCarriesOnlyExplicitProviderIdentitiesAndLocalDecisions() throws Exception {
        AttendanceActor actor = adminActor();
        when(currentUsers.actor(org.mockito.ArgumentMatchers.any())).thenReturn(actor);

        mvc.perform(post("/admin/settings/calendar/import")
                        .with(user("admin@example.test").roles("ADMIN"))
                        .with(csrf())
                        .param("year", "2026")
                        .param("decision_uuid-1", "true")
                        .param("decision_uuid-2", "false")
                        .param("decision_uuid-3", "skip"))
                .andExpect(status().is3xxRedirection());

        verify(calendar).importSelected(actor, 2026, List.of(
                new CalendarImportSelection("uuid-1", true),
                new CalendarImportSelection("uuid-2", false)));
    }

    @Test
    void malformedPolicyAndCalendarDecisionsReturnSafeValidationRedirects() throws Exception {
        when(currentUsers.actor(org.mockito.ArgumentMatchers.any())).thenReturn(adminActor());

        mvc.perform(post("/admin/settings/policy")
                        .with(user("admin@example.test").roles("ADMIN"))
                        .with(csrf())
                        .param("effectiveFrom", "2026-09-01")
                        .param("zoneId", "Asia/Ho_Chi_Minh")
                        .param("scheduledStart", "08:00")
                        .param("scheduledEnd", "17:00")
                        .param("checkInGraceMinutes", "15")
                        .param("checkoutGraceMinutes", "15")
                        .param("monthlyLeaveQuota", "2")
                        .param("violationPenalty", "0.1"))
                .andExpect(status().is3xxRedirection());

        mvc.perform(post("/admin/settings/calendar/import")
                        .with(user("admin@example.test").roles("ADMIN"))
                        .with(csrf())
                        .param("year", "2026")
                        .param("decision_uuid-1", "unexpected"))
                .andExpect(status().is3xxRedirection());

        verifyNoInteractions(policies, calendar);
    }

    @Test
    void malformedPolicyValuesRetainRawSafeInputInsteadOfReturningBadRequest() throws Exception {
        when(currentUsers.actor(org.mockito.ArgumentMatchers.any())).thenReturn(adminActor());

        mvc.perform(post("/admin/settings/policy")
                        .with(user("admin@example.test").roles("ADMIN"))
                        .with(csrf())
                        .param("effectiveFrom", "not-a-date")
                        .param("zoneId", "Asia/Ho_Chi_Minh")
                        .param("scheduledStart", "not-a-time")
                        .param("scheduledEnd", "17:00")
                        .param("checkInGraceMinutes", "many")
                        .param("checkoutGraceMinutes", "15")
                        .param("monthlyLeaveQuota", "2")
                        .param("violationPenalty", "not-a-decimal")
                        .param("workdays", "MONDAY"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/settings#policy-history"))
                .andExpect(flash().attribute("settingsError", "Enter valid attendance policy values."))
                .andExpect(flash().attribute("policyInput", org.hamcrest.Matchers.hasEntry(
                        "effectiveFrom", "not-a-date")))
                .andExpect(flash().attribute("policyInput", org.hamcrest.Matchers.hasEntry(
                        "checkInGraceMinutes", "many")));
    }

    @Test
    void malformedCalendarYearReturnsSafeValidationRedirect() throws Exception {
        mvc.perform(post("/admin/settings/calendar/preview")
                        .with(user("admin@example.test").roles("ADMIN"))
                        .with(csrf())
                        .param("year", "not-a-year"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/settings#calendar-history"))
                .andExpect(flash().attribute("settingsError", "Enter a valid calendar year."));
    }

    @Test
    void invalidPolicyRetainsSafeInputWithAnInlineError() throws Exception {
        AttendanceActor actor = adminActor();
        when(currentUsers.actor(org.mockito.ArgumentMatchers.any())).thenReturn(actor);
        when(accounts.requireActiveAdminId("admin@example.test")).thenReturn(1L);
        when(attendance.currentBusinessDate()).thenReturn(LocalDate.of(2026, 8, 21));
        when(policies.history(actor)).thenReturn(List.of());
        when(calendar.history(actor)).thenReturn(List.of());
        when(smtp.history(1L)).thenReturn(List.of());
        when(holidayApi.history(1L)).thenReturn(List.of());
        when(holidayApi.setupStatus(1L)).thenReturn(new HolidayApiSetupStatus(false, null, false, "VN"));

        mvc.perform(post("/admin/settings/policy")
                        .with(user("admin@example.test").roles("ADMIN")).with(csrf())
                        .param("effectiveFrom", "2026-09-01")
                        .param("zoneId", "Asia/Ho_Chi_Minh")
                        .param("scheduledStart", "08:00")
                        .param("scheduledEnd", "17:00")
                        .param("checkInGraceMinutes", "15")
                        .param("checkoutGraceMinutes", "15")
                        .param("monthlyLeaveQuota", "2")
                        .param("violationPenalty", "0.1"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("policyInput", org.hamcrest.Matchers.hasEntry(
                        "effectiveFrom", "2026-09-01")));

        mvc.perform(get("/admin/settings")
                        .with(user("admin@example.test").roles("ADMIN"))
                        .flashAttr("settingsError", "At least one workday is required")
                        .flashAttr("policyInput", Map.of(
                                "effectiveFrom", "2026-09-01",
                                "zoneId", "Asia/Ho_Chi_Minh",
                                "scheduledStart", "08:00",
                                "scheduledEnd", "17:00",
                                "checkInGraceMinutes", 15,
                                "checkoutGraceMinutes", 15,
                                "monthlyLeaveQuota", 2,
                                "violationPenalty", "0.1",
                                "workdays", Set.of())))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"2026-09-01\"")))
                .andExpect(content().string(containsString("id=\"policy-form-error\"")));
    }

    private static AttendanceActor adminActor() {
        return new AttendanceActor(1L, AttendanceRole.ADMIN);
    }
}
