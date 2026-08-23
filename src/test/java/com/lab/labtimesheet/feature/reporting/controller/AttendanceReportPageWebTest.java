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

import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.AttendancePolicy;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRole;
import com.lab.labtimesheet.feature.attendance.model.AttendanceViolations;
import com.lab.labtimesheet.feature.attendance.model.AttendancePolicyFixtures;
import com.lab.labtimesheet.feature.attendance.service.AttendanceApplicationService;
import com.lab.labtimesheet.feature.attendance.service.AttendanceCurrentUserService;
import com.lab.labtimesheet.feature.integration.service.SmtpConfigurationService;
import com.lab.labtimesheet.feature.reporting.model.dto.AttendanceReportDay;
import com.lab.labtimesheet.feature.reporting.model.dto.DayKind;
import com.lab.labtimesheet.feature.reporting.service.AttendanceReportDataProvider;
import com.lab.labtimesheet.feature.reporting.service.AttendanceReportService;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AttendanceReportController.class)
@Import(AttendanceReportService.class)
class AttendanceReportPageWebTest {

    private static final AttendancePolicy POLICY = AttendancePolicyFixtures.seeded(1L);
    private static final LocalDate FROM = LocalDate.of(2026, 8, 3);
    private static final LocalDate TO = LocalDate.of(2026, 8, 7);

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AttendanceReportDataProvider reports;

    @MockitoBean
    private AttendanceCurrentUserService currentUsers;

    @MockitoBean
    private AttendanceApplicationService attendance;

    @MockitoBean
    private SmtpConfigurationService smtpConfiguration;

    @Test
    void internRendersOwnClassifiedDaysSummaryAndComplianceChart() throws Exception {
        given(currentUsers.actor(any(Principal.class)))
                .willReturn(new AttendanceActor(5, AttendanceRole.INTERN));
        given(attendance.currentBusinessDate()).willReturn(TO);
        given(reports.reportDays(new AttendanceActor(5, AttendanceRole.INTERN), 5, FROM, TO))
                .willReturn(List.of(
                        day(3, DayKind.WORKDAY_PRESENT, new AttendanceViolations(false, false, false)),
                        day(4, DayKind.WORKDAY_PRESENT, new AttendanceViolations(true, false, false)),
                        day(5, DayKind.LEAVE, new AttendanceViolations(false, false, false)),
                        day(6, DayKind.WORKDAY_ABSENT, new AttendanceViolations(false, false, false)),
                        day(7, DayKind.WORKDAY_PRESENT, new AttendanceViolations(false, true, false))));

        mvc.perform(get("/reports/attendance").with(user("intern@example.test").roles("INTERN"))
                        .param("from", "2026-08-03").param("to", "2026-08-07"))
                .andExpect(status().isOk())
                .andExpect(view().name("reports/attendance"))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Attendance report")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("75.00%")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("62.50%")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("N/A"))))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Leave")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Absent")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Late")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Early departure")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Daily compliance")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("data-chart=")));

        verify(reports).reportDays(new AttendanceActor(5, AttendanceRole.INTERN), 5, FROM, TO);
    }

    @Test
    void mentorInspectsTargetInternAndSeesPerDayScores() throws Exception {
        given(currentUsers.actor(any(Principal.class)))
                .willReturn(new AttendanceActor(2, AttendanceRole.MENTOR));
        given(attendance.currentBusinessDate()).willReturn(TO);
        given(reports.reportDays(new AttendanceActor(2, AttendanceRole.MENTOR), 7, FROM, TO))
                .willReturn(List.of(
                        day(3, DayKind.WORKDAY_PRESENT, new AttendanceViolations(true, true, false)),
                        day(4, DayKind.WORKDAY_PRESENT, new AttendanceViolations(false, false, false)),
                        day(5, DayKind.OFF_DAY, new AttendanceViolations(false, false, false)),
                        day(6, DayKind.OFF_DAY, new AttendanceViolations(false, false, false)),
                        day(7, DayKind.OFF_DAY, new AttendanceViolations(false, false, false))));

        mvc.perform(get("/reports/attendance").with(user("mentor@example.test").roles("MENTOR"))
                        .param("internId", "7").param("from", "2026-08-03").param("to", "2026-08-07"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("100.00%")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("75.00%")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Late")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Early departure")))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Check in"))));

        verify(reports).reportDays(new AttendanceActor(2, AttendanceRole.MENTOR), 7, FROM, TO);
    }

    @Test
    void emptyDenominatorRendersNaForRateAndCompliance() throws Exception {
        given(currentUsers.actor(any(Principal.class)))
                .willReturn(new AttendanceActor(5, AttendanceRole.INTERN));
        given(attendance.currentBusinessDate()).willReturn(TO);
        given(reports.reportDays(new AttendanceActor(5, AttendanceRole.INTERN), 5, FROM, TO))
                .willReturn(List.of(
                        day(5, DayKind.OFF_DAY, new AttendanceViolations(false, false, false))));

        mvc.perform(get("/reports/attendance").with(user("intern@example.test").roles("INTERN"))
                        .param("from", "2026-08-03").param("to", "2026-08-07"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("N/A")));

        verify(reports).reportDays(new AttendanceActor(5, AttendanceRole.INTERN), 5, FROM, TO);
    }

    @Test
    void internCannotInspectAnotherIntern() throws Exception {
        given(currentUsers.actor(any(Principal.class)))
                .willReturn(new AttendanceActor(5, AttendanceRole.INTERN));

        mvc.perform(get("/reports/attendance").with(user("intern@example.test").roles("INTERN"))
                        .param("internId", "7"))
                .andExpect(status().isForbidden());

        verify(reports, never()).reportDays(any(), anyLong(), any(), any());
    }

    @Test
    void missingFiltersDefaultToCurrentBusinessDateMonth() throws Exception {
        given(currentUsers.actor(any(Principal.class)))
                .willReturn(new AttendanceActor(5, AttendanceRole.INTERN));
        given(attendance.currentBusinessDate()).willReturn(LocalDate.of(2026, 8, 15));
        given(reports.reportDays(
                        new AttendanceActor(5, AttendanceRole.INTERN), 5,
                        LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 15)))
                .willReturn(List.of());

        mvc.perform(get("/reports/attendance").with(user("intern@example.test").roles("INTERN")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("N/A")));

        verify(reports).reportDays(
                new AttendanceActor(5, AttendanceRole.INTERN), 5,
                LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 15));
    }

    private static AttendanceReportDay day(int dayOfMonth, DayKind kind, AttendanceViolations violations) {
        return new AttendanceReportDay(LocalDate.of(2026, 8, dayOfMonth), kind, POLICY, violations);
    }
}