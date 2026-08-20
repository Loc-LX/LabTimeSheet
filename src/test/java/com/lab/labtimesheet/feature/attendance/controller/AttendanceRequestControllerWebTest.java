package com.lab.labtimesheet.feature.attendance.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lab.labtimesheet.feature.attendance.exception.LeaveException;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.AttendancePolicy;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRole;
import com.lab.labtimesheet.feature.attendance.model.AttendanceViolations;
import com.lab.labtimesheet.feature.attendance.model.CorrectionStatus;
import com.lab.labtimesheet.feature.attendance.model.CorrectionEventType;
import com.lab.labtimesheet.feature.attendance.model.LeaveStatus;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionDecision;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionRequestCommand;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionEventView;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionSummary;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveRequestCommand;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveAllocation;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveRequestSummary;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveRequestView;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionView;
import com.lab.labtimesheet.feature.attendance.service.AttendanceCorrectionApplicationService;
import com.lab.labtimesheet.feature.attendance.service.AttendanceCurrentUserService;
import com.lab.labtimesheet.feature.attendance.service.LeaveApplicationService;
import com.lab.labtimesheet.feature.integration.service.SmtpConfigurationService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.DayOfWeek;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Production web contract for discoverable leave and missed-checkout correction workflows. */
@WebMvcTest(AttendanceRequestController.class)
class AttendanceRequestControllerWebTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AttendanceCurrentUserService currentUsers;

    @MockitoBean
    private LeaveApplicationService leave;

    @MockitoBean
    private AttendanceCorrectionApplicationService corrections;

    @MockitoBean
    private SmtpConfigurationService smtpConfiguration;

    @Test
    void internListsOwnRequestsAndSubmitsLeaveAndCorrection() throws Exception {
        AttendanceActor actor = new AttendanceActor(7L, AttendanceRole.INTERN);
        when(currentUsers.actor(org.mockito.ArgumentMatchers.any())).thenReturn(actor);
        when(leave.list(actor)).thenReturn(List.of(new LeaveRequestSummary(
                10L, 7L, LocalDate.of(2026, 8, 28), LocalDate.of(2026, 9, 2),
                "Family", LeaveStatus.PENDING, Instant.parse("2026-08-20T00:00:00Z"))));
        when(corrections.list(actor)).thenReturn(List.of(new CorrectionSummary(
                11L, 55L, 7L, Instant.parse("2026-08-20T09:00:00Z"), "Missed",
                CorrectionStatus.PENDING.name(), Instant.parse("2026-08-20T10:00:00Z"),
                Instant.parse("2026-08-21T10:00:00Z"))));
        LeaveRequestView submittedLeave = mock(LeaveRequestView.class);
        when(submittedLeave.id()).thenReturn(10L);
        when(leave.submit(actor, new LeaveRequestCommand(
                LocalDate.of(2026, 8, 28), LocalDate.of(2026, 9, 2), "Family")))
                .thenReturn(submittedLeave);
        CorrectionView submittedCorrection = mock(CorrectionView.class);
        when(submittedCorrection.id()).thenReturn(11L);
        when(corrections.submit(actor, 55L,
                new CorrectionRequestCommand(LocalDateTime.of(2026, 8, 20, 16, 0), "Missed")))
                .thenReturn(submittedCorrection);

        mvc.perform(get("/attendance/requests").with(user("intern@example.test").roles("INTERN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cross-month leave")))
                .andExpect(content().string(containsString("Missed checkout correction")))
                .andExpect(content().string(containsString("Family")));

        mvc.perform(post("/attendance/leave").with(user("intern@example.test").roles("INTERN")).with(csrf())
                        .param("startDate", "2026-08-28").param("endDate", "2026-09-02")
                        .param("reason", "Family"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/attendance/leave/*"));
        mvc.perform(post("/attendance/corrections").with(user("intern@example.test").roles("INTERN")).with(csrf())
                        .param("attendanceRecordId", "55")
                        .param("proposedCheckout", "2026-08-20T16:00").param("reason", "Missed"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("/attendance/corrections/*"));

        verify(leave).submit(actor, new LeaveRequestCommand(
                LocalDate.of(2026, 8, 28), LocalDate.of(2026, 9, 2), "Family"));
        verify(corrections).submit(actor, 55L,
                new CorrectionRequestCommand(LocalDateTime.of(2026, 8, 20, 16, 0), "Missed"));
    }

    @Test
    void mentorDecisionPostsUseOnlyTheServiceStateMachine() throws Exception {
        AttendanceActor actor = new AttendanceActor(2L, AttendanceRole.MENTOR);
        when(currentUsers.actor(org.mockito.ArgumentMatchers.any())).thenReturn(actor);

        mvc.perform(post("/attendance/leave/10/approve").with(user("mentor@example.test").roles("MENTOR")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/attendance/corrections/11/decide")
                        .with(user("mentor@example.test").roles("MENTOR")).with(csrf())
                        .param("decision", "REOPEN").param("note", "Recheck"))
                .andExpect(status().is3xxRedirection());

        verify(leave).approve(actor, 10L);
        verify(corrections).decide(actor, 11L, CorrectionDecision.REOPEN, "Recheck");
    }

    @Test
    void rejectedLeaveRetainsSafeInputWithAnInlineError() throws Exception {
        AttendanceActor actor = new AttendanceActor(7L, AttendanceRole.INTERN);
        LeaveRequestCommand command = new LeaveRequestCommand(
                LocalDate.of(2026, 8, 28), LocalDate.of(2026, 9, 2), "Retain this reason");
        when(currentUsers.actor(org.mockito.ArgumentMatchers.any())).thenReturn(actor);
        when(leave.list(actor)).thenReturn(List.of());
        when(corrections.list(actor)).thenReturn(List.of());
        when(leave.submit(actor, command)).thenThrow(new LeaveException("Leave overlaps an active request"));
        Map<String, Object> input = Map.of(
                "startDate", "2026-08-28",
                "endDate", "2026-09-02",
                "reason", "Retain this reason");

        mvc.perform(post("/attendance/leave")
                        .with(user("intern@example.test").roles("INTERN")).with(csrf())
                        .param("startDate", "2026-08-28")
                        .param("endDate", "2026-09-02")
                        .param("reason", "Retain this reason"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("leaveInput", input));

        mvc.perform(get("/attendance/requests")
                        .with(user("intern@example.test").roles("INTERN"))
                        .flashAttr("requestError", "Leave overlaps an active request")
                        .flashAttr("leaveInput", input))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"2026-08-28\"")))
                .andExpect(content().string(containsString("value=\"Retain this reason\"")))
                .andExpect(content().string(containsString("id=\"leave-form-error\"")));
    }

    @Test
    void retainedLeaveAllocationsAndCorrectionEventsRenderWithoutExposingRawMutationState() throws Exception {
        AttendanceActor intern = new AttendanceActor(7L, AttendanceRole.INTERN);
        when(currentUsers.actor(org.mockito.ArgumentMatchers.any())).thenReturn(intern);
        when(leave.list(intern)).thenReturn(List.of());
        when(corrections.list(intern)).thenReturn(List.of());
        when(leave.view(intern, 10L)).thenReturn(new LeaveRequestView(
                10L, 7L, LocalDate.of(2026, 8, 28), LocalDate.of(2026, 9, 2), "Family",
                LeaveStatus.PENDING, Instant.parse("2026-08-20T00:00:00Z"),
                Instant.parse("2026-08-28T01:00:00Z"), null, null, null,
                List.of(new LeaveAllocation(LocalDate.of(2026, 8, 28),
                        LocalDate.of(2026, 8, 1), 1L, 3))));

        mvc.perform(get("/attendance/leave/10").with(user("intern@example.test").roles("INTERN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("28/08/2026 · 01/08/2026")));

        AttendanceActor mentor = new AttendanceActor(2L, AttendanceRole.MENTOR);
        when(currentUsers.actor(org.mockito.ArgumentMatchers.any())).thenReturn(mentor);
        when(leave.list(mentor)).thenReturn(List.of());
        when(corrections.list(mentor)).thenReturn(List.of());
        AttendancePolicy policy = new AttendancePolicy(
                1L, LocalDate.of(2026, 1, 1), ZoneId.of("Asia/Ho_Chi_Minh"),
                LocalTime.of(8, 0), LocalTime.of(17, 0), 15, 15, 3,
                BigDecimal.valueOf(0.1), Set.of(DayOfWeek.MONDAY));
        when(corrections.view(mentor, 11L)).thenReturn(new CorrectionView(
                11L, 55L, 7L, null, LocalDateTime.of(2026, 8, 20, 16, 0), null,
                "Missed", CorrectionStatus.PENDING, Instant.parse("2026-08-20T10:00:00Z"),
                Instant.parse("2026-08-21T10:00:00Z"), Instant.parse("2026-08-21T10:00:00Z"),
                null, policy, new AttendanceViolations(false, false, true),
                List.of(new CorrectionEventView(1L, CorrectionEventType.SUBMITTED, null,
                        CorrectionStatus.PENDING, 7L, "Missed", Instant.parse("2026-08-20T10:00:00Z")))));

        mvc.perform(get("/attendance/corrections/11").with(user("mentor@example.test").roles("MENTOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("SUBMITTED")))
                .andExpect(content().string(containsString("Approve")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("Reopen"))))
                .andExpect(content().string(containsString("Save decision")));

        when(corrections.view(mentor, 11L)).thenReturn(new CorrectionView(
                11L, 55L, 7L, null, LocalDateTime.of(2026, 8, 20, 16, 0),
                Instant.parse("2026-08-20T09:00:00Z"), "Missed", CorrectionStatus.APPROVED,
                Instant.parse("2026-08-20T10:00:00Z"), Instant.parse("2026-08-21T10:00:00Z"),
                Instant.parse("2026-08-21T10:00:00Z"), null, policy,
                new AttendanceViolations(false, true, false), List.of()));

        mvc.perform(get("/attendance/corrections/11").with(user("mentor@example.test").roles("MENTOR")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Reopen")))
                .andExpect(content().string(org.hamcrest.Matchers.not(containsString("value=\"APPROVE\""))));
    }
}
