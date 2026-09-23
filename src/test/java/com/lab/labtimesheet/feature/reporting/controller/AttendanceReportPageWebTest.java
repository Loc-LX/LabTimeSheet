package com.lab.labtimesheet.feature.reporting.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.lab.labtimesheet.feature.identity.model.AccountStatus;
import com.lab.labtimesheet.feature.identity.model.dto.AccountIdentity;
import com.lab.labtimesheet.feature.identity.service.AccountService;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.platform.model.GlobalRole;
import com.lab.labtimesheet.feature.attendance.model.dto.AttendanceReport;
import com.lab.labtimesheet.feature.attendance.model.dto.AttendanceReportClassification;
import com.lab.labtimesheet.feature.attendance.model.dto.AttendanceReportDay;
import com.lab.labtimesheet.feature.attendance.service.AttendanceApplicationService;
import com.lab.labtimesheet.feature.calendar.service.CalendarApplicationService;
import com.lab.labtimesheet.feature.attendance.service.AttendanceCurrentUserService;
import com.lab.labtimesheet.feature.attendance.service.AttendanceReportQueryService;
import com.lab.labtimesheet.feature.reporting.service.AttendanceReportService;
import com.lab.labtimesheet.platform.model.GlobalRole;
import com.lab.labtimesheet.platform.service.SmtpConfigurationService;
import java.math.BigDecimal;
import java.security.Principal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AttendanceReportController.class)
@Import(AttendanceReportService.class)
class AttendanceReportPageWebTest {

    private static final LocalDate FROM = LocalDate.of(2026, 8, 3);
    private static final LocalDate TO = LocalDate.of(2026, 8, 7);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AttendanceCurrentUserService currentUsers;

    @MockitoBean
    private AttendanceApplicationService attendance;

    @MockitoBean
    private CalendarApplicationService calendar;

    @MockitoBean
    private AttendanceReportQueryService reportQueries;

    @MockitoBean
    private AccountService accounts;

    @MockitoBean
    private SmtpConfigurationService smtpConfiguration;

    @Test
    void internRendersOwnClassifiedDaysSummaryAndComplianceChart() throws Exception {
        given(currentUsers.actor(any(Principal.class)))
                .willReturn(new AttendanceActor(5, GlobalRole.INTERN));
        given(calendar.currentBusinessDate()).willReturn(TO);
        given(accounts.requireIdentityById(5L)).willReturn(intern(5L, "Mai Intern"));
        given(reportQueries.query(new AttendanceActor(5, GlobalRole.INTERN), 5, FROM, TO))
                .willReturn(new AttendanceReport(
                        5,
                        FROM,
                        TO,
                        List.of(
                                present(3, "2026-08-03T01:30:00Z", "2026-08-03T08:30:00Z", false, false,
                                        Optional.of(BigDecimal.ONE)),
                                present(4, "2026-08-04T01:45:00Z", "2026-08-04T08:30:00Z", true, false,
                                        Optional.of(new BigDecimal("0.75"))),
                                day(5, AttendanceReportClassification.APPROVED_LEAVE, Optional.empty()),
                                day(6, AttendanceReportClassification.ABSENT, Optional.of(BigDecimal.ZERO)),
                                present(7, "2026-08-07T01:30:00Z", "2026-08-07T08:15:00Z", false, true,
                                        Optional.of(new BigDecimal("0.75")))),
                        3,
                        4,
                        Optional.of(new BigDecimal("75.00")),
                        Optional.of(new BigDecimal("62.50"))));

        mvc.perform(get("/reports/attendance").with(user("intern@example.test").roles("INTERN"))
                        .param("from", "2026-08-03").param("to", "2026-08-07"))
                .andExpect(status().isOk())
                .andExpect(view().name("reports/attendance"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Attendance report")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("75.00%")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("62.50%")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Approved leave")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Absent")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Late")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Early departure")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Attendance compliance trend")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-report-chart")));

        verify(reportQueries).query(new AttendanceActor(5, GlobalRole.INTERN), 5, FROM, TO);
    }

    @Test
    void mentorInspectsTargetInternAndSeesPerDayScores() throws Exception {
        given(currentUsers.actor(any(Principal.class)))
                .willReturn(new AttendanceActor(2, GlobalRole.MENTOR));
        given(calendar.currentBusinessDate()).willReturn(TO);
        given(accounts.requireIdentityById(7L)).willReturn(intern(7L, "Target Intern"));
        given(reportQueries.query(new AttendanceActor(2, GlobalRole.MENTOR), 7, FROM, TO))
                .willReturn(new AttendanceReport(
                        7,
                        FROM,
                        TO,
                        List.of(
                                present(3, "2026-08-03T01:45:00Z", "2026-08-03T08:15:00Z", true, true,
                                        Optional.of(new BigDecimal("0.50"))),
                                present(4, "2026-08-04T01:30:00Z", "2026-08-04T08:30:00Z", false, false,
                                        Optional.of(BigDecimal.ONE)),
                                day(5, AttendanceReportClassification.OFF_DAY, Optional.empty()),
                                day(6, AttendanceReportClassification.OFF_DAY, Optional.empty()),
                                day(7, AttendanceReportClassification.OFF_DAY, Optional.empty())),
                        2,
                        2,
                        Optional.of(new BigDecimal("100.00")),
                        Optional.of(new BigDecimal("75.00"))));

        mvc.perform(get("/reports/attendance").with(user("mentor@example.test").roles("MENTOR"))
                        .param("internId", "7").param("from", "2026-08-03").param("to", "2026-08-07"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("100.00%")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("75.00%")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Late")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Early departure")));

        verify(reportQueries).query(new AttendanceActor(2, GlobalRole.MENTOR), 7, FROM, TO);
    }

    @Test
    void emptyDenominatorRendersNaForRateAndCompliance() throws Exception {
        given(currentUsers.actor(any(Principal.class)))
                .willReturn(new AttendanceActor(5, GlobalRole.INTERN));
        given(calendar.currentBusinessDate()).willReturn(TO);
        given(accounts.requireIdentityById(5L)).willReturn(intern(5L, "Mai Intern"));
        given(reportQueries.query(new AttendanceActor(5, GlobalRole.INTERN), 5, FROM, TO))
                .willReturn(new AttendanceReport(
                        5,
                        FROM,
                        TO,
                        List.of(day(5, AttendanceReportClassification.OFF_DAY, Optional.empty())),
                        0,
                        0,
                        Optional.empty(),
                        Optional.empty()));

        mvc.perform(get("/reports/attendance").with(user("intern@example.test").roles("INTERN"))
                        .param("from", "2026-08-03").param("to", "2026-08-07"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("N/A")));

        verify(reportQueries).query(new AttendanceActor(5, GlobalRole.INTERN), 5, FROM, TO);
    }

    @Test
    void internCannotInspectAnotherIntern() throws Exception {
        given(currentUsers.actor(any(Principal.class)))
                .willReturn(new AttendanceActor(5, GlobalRole.INTERN));
        given(calendar.currentBusinessDate()).willReturn(TO);

        mvc.perform(get("/reports/attendance").with(user("intern@example.test").roles("INTERN"))
                        .param("internId", "7"))
                .andExpect(status().isForbidden());

        verify(reportQueries, never()).query(any(), anyLong(), any(), any());
    }

    @Test
    void missingFiltersDefaultToCurrentBusinessDateMonth() throws Exception {
        given(currentUsers.actor(any(Principal.class)))
                .willReturn(new AttendanceActor(5, GlobalRole.INTERN));
        given(calendar.currentBusinessDate()).willReturn(LocalDate.of(2026, 8, 15));
        given(accounts.requireIdentityById(5L)).willReturn(intern(5L, "Mai Intern"));
        given(reportQueries.query(
                        new AttendanceActor(5, GlobalRole.INTERN), 5,
                        LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 15)))
                .willReturn(new AttendanceReport(
                        5,
                        LocalDate.of(2026, 8, 1),
                        LocalDate.of(2026, 8, 15),
                        List.of(),
                        0,
                        0,
                        Optional.empty(),
                        Optional.empty()));

        mvc.perform(get("/reports/attendance").with(user("intern@example.test").roles("INTERN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("N/A")));

        verify(reportQueries).query(
                new AttendanceActor(5, GlobalRole.INTERN), 5,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 15));
    }

    private static AccountIdentity intern(long id, String name) {
        return new AccountIdentity(id, "intern" + id + "@example.test", name, GlobalRole.INTERN, AccountStatus.ACTIVE);
    }

    private static AttendanceReportDay day(
            int dayOfMonth,
            AttendanceReportClassification classification,
            Optional<BigDecimal> score) {
        return new AttendanceReportDay(
                LocalDate.of(2026, 8, dayOfMonth),
                classification,
                1L,
                LocalDate.of(2026, 1, 1),
                ZoneId.of("Asia/Ho_Chi_Minh"),
                LocalTime.of(8, 30),
                LocalTime.of(15, 30),
                5,
                5,
                new BigDecimal("0.3333"),
                null,
                null,
                null,
                false,
                false,
                false,
                score);
    }

    private static AttendanceReportDay present(
            int dayOfMonth,
            String checkIn,
            String checkout,
            boolean late,
            boolean earlyDeparture,
            Optional<BigDecimal> score) {
        Instant checkoutAt = Instant.parse(checkout);
        return new AttendanceReportDay(
                LocalDate.of(2026, 8, dayOfMonth),
                AttendanceReportClassification.PRESENT,
                1L,
                LocalDate.of(2026, 1, 1),
                ZoneId.of("Asia/Ho_Chi_Minh"),
                LocalTime.of(8, 30),
                LocalTime.of(15, 30),
                5,
                5,
                new BigDecimal("0.3333"),
                Instant.parse(checkIn),
                checkoutAt,
                checkoutAt,
                late,
                earlyDeparture,
                false,
                score);
    }
}
