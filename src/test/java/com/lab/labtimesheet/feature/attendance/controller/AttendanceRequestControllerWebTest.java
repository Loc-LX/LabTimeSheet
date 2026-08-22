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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
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
import com.lab.labtimesheet.feature.attendance.service.AttendanceApplicationService;
import com.lab.labtimesheet.feature.attendance.service.AttendanceCurrentUserService;
import com.lab.labtimesheet.feature.attendance.service.LeaveApplicationService;
import com.lab.labtimesheet.feature.integration.service.SmtpConfigurationService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.DayOfWeek;
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

/** Production web contract for discoverable leave and missed-checkout correction workflows. */
@WebMvcTest(AttendanceRequestController.class)
class AttendanceRequestControllerWebTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AttendanceCurrentUserService currentUsers;

    @MockitoBean
    private AttendanceApplicationService attendance;

    @MockitoBean
    private LeaveApplicationService leave;

    @MockitoBean
    private AttendanceCorrectionApplicationService corrections;

    @MockitoBean
    private SmtpConfigurationService smtpConfiguration;

    @Test
    void legacyRequestRouteRedirectsToLeaveWorkflow() throws Exception {
        mvc.perform(get("/attendance/requests")
                        .with(user("intern@example.test").roles("INTERN")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/attendance/leave"));
    }

    @Test
    void internListsOwnRequestsAndSubmitsLeaveAndCorrection() throws Exception {
        AttendanceActor actor = new AttendanceActor(7L, AttendanceRole.INTERN);
        when(currentUsers.actor(org.mockito.ArgumentMatchers.any())).thenReturn(actor);
        when(attendance.currentBusinessDate()).thenReturn(LocalDate.of(2026, 8, 21));
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

        when(leave.balance(actor, YearMonth.of(2026, 8))).thenReturn(
                new com.lab.labtimesheet.feature.attendance.model.dto.LeaveBalance(
                        YearMonth.of(2026, 8), 1, 3));
        mvc.perform(get("/attendance/leave").param("month", "2026-08")
                        .with(user("intern@example.test").roles("INTERN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Leave balance")))
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
        when(attendance.currentBusinessDate()).thenReturn(LocalDate.of(2026, 8, 21));
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

        mvc.perform(get("/attendance/leave")
                        .with(user("intern@example.test").roles("INTERN"))
                        .flashAttr("requestError", "Leave overlaps an active request")
                        .flashAttr("leaveInput", input))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"2026-08-28\"")))
                .andExpect(content().string(containsString("value=\"Retain this reason\"")))
                .andExpect(content().string(containsString("id=\"leave-form-error\"")));
    }

    @Test
    void malformedLeaveDatesRetainRawSafeInputInsteadOfReturningBadRequest() throws Exception {
        when(currentUsers.actor(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new AttendanceActor(7L, AttendanceRole.INTERN));

        mvc.perform(post("/attendance/leave")
                        .with(user("intern@example.test").roles("INTERN")).with(csrf())
                        .param("startDate", "not-a-date")
                        .param("endDate", "2026-09-02")
                        .param("reason", "Retained reason"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/attendance/leave"))
                .andExpect(flash().attribute("requestError", "Enter valid leave dates."))
                .andExpect(flash().attribute("leaveInput", Map.of(
                        "startDate", "not-a-date",
                        "endDate", "2026-09-02",
                        "reason", "Retained reason")));
    }

    @Test
    void malformedCorrectionFieldsRetainRawSafeInputInsteadOfReturningBadRequest() throws Exception {
        when(currentUsers.actor(org.mockito.ArgumentMatchers.any()))
                .thenReturn(new AttendanceActor(7L, AttendanceRole.INTERN));

        mvc.perform(post("/attendance/corrections")
                        .with(user("intern@example.test").roles("INTERN")).with(csrf())
                        .param("attendanceRecordId", "not-an-id")
                        .param("proposedCheckout", "not-a-date-time")
                        .param("reason", "Retained reason"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/attendance/corrections"))
                .andExpect(flash().attribute(
                        "requestError", "Enter a valid attendance record and proposed checkout."))
                .andExpect(flash().attribute("correctionInput", Map.of(
                        "attendanceRecordId", "not-an-id",
                        "proposedCheckout", "not-a-date-time",
                        "reason", "Retained reason")));
    }

    @Test
    void correctionEntryRetainsAttendanceRecordIdFromHistoryLink() throws Exception {
        AttendanceActor actor = new AttendanceActor(7L, AttendanceRole.INTERN);
        when(currentUsers.actor(org.mockito.ArgumentMatchers.any())).thenReturn(actor);
        when(corrections.list(actor)).thenReturn(List.of());

        mvc.perform(get("/attendance/corrections").param("attendanceRecordId", "55")
                        .with(user("intern@example.test").roles("INTERN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"55\"")));
    }

    @Test
    void correctionFormRendersRetainedSafeInputAfterValidationFailure() throws Exception {
        AttendanceActor actor = new AttendanceActor(7L, AttendanceRole.INTERN);
        when(currentUsers.actor(org.mockito.ArgumentMatchers.any())).thenReturn(actor);
        when(corrections.list(actor)).thenReturn(List.of());

        mvc.perform(get("/attendance/corrections")
                        .with(user("intern@example.test").roles("INTERN"))
                        .flashAttr("requestError", "Enter a valid attendance record and proposed checkout.")
                        .flashAttr("correctionInput", Map.of(
                                "attendanceRecordId", "not-an-id",
                                "proposedCheckout", "2026-08-20T16:00",
                                "reason", "Retained reason")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"not-an-id\"")))
                .andExpect(content().string(containsString("value=\"2026-08-20T16:00\"")))
                .andExpect(content().string(containsString("value=\"Retained reason\"")))
                .andExpect(content().string(containsString("id=\"correction-form-error\"")));
    }

    @Test
    void leaveEditFormRendersRetainedSafeInputAfterValidationFailure() throws Exception {
        AttendanceActor actor = new AttendanceActor(7L, AttendanceRole.INTERN);
        when(currentUsers.actor(org.mockito.ArgumentMatchers.any())).thenReturn(actor);
        when(attendance.currentBusinessDate()).thenReturn(LocalDate.of(2026, 8, 21));
        when(leave.list(actor)).thenReturn(List.of());
        when(corrections.list(actor)).thenReturn(List.of());
        when(leave.balance(actor, YearMonth.of(2026, 8))).thenReturn(
                new com.lab.labtimesheet.feature.attendance.model.dto.LeaveBalance(
                        YearMonth.of(2026, 8), 0, 3));
        when(leave.view(actor, 10L)).thenReturn(new LeaveRequestView(
                10L, 7L, LocalDate.of(2026, 8, 28), LocalDate.of(2026, 8, 28), "Original",
                LeaveStatus.PENDING, Instant.parse("2026-08-20T00:00:00Z"),
                Instant.parse("2026-08-28T01:00:00Z"), null, null, null, List.of()));

        mvc.perform(get("/attendance/leave/10")
                        .with(user("intern@example.test").roles("INTERN"))
                        .flashAttr("requestError", "Leave dates are invalid")
                        .flashAttr("leaveEditInput", Map.of(
                                "startDate", "not-a-date",
                                "endDate", "2026-08-30",
                                "reason", "Retained edit reason")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"not-a-date\"")))
                .andExpect(content().string(containsString("value=\"2026-08-30\"")))
                .andExpect(content().string(containsString("value=\"Retained edit reason\"")))
                .andExpect(content().string(containsString("id=\"leave-edit-form-error\"")));
    }

    @Test
    void malformedCorrectionDecisionRetainsRawSafeInputInsteadOfReturningBadRequest() throws Exception {
        mvc.perform(post("/attendance/corrections/11/decide")
                        .with(user("mentor@example.test").roles("MENTOR")).with(csrf())
                        .param("decision", "NOT_A_DECISION")
                        .param("note", "Retained note"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/attendance/corrections/11"))
                .andExpect(flash().attribute("requestError", "Choose a valid correction decision."))
                .andExpect(flash().attribute("correctionDecisionInput", Map.of(
                        "decision", "NOT_A_DECISION",
                        "note", "Retained note")));
    }

    @Test
    void correctionDecisionFormRendersRetainedSafeInputAfterValidationFailure() throws Exception {
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
                null, policy, new AttendanceViolations(false, false, true), List.of()));

        mvc.perform(get("/attendance/corrections/11")
                        .with(user("mentor@example.test").roles("MENTOR"))
                        .flashAttr("requestError", "Choose a valid correction decision.")
                        .flashAttr("correctionDecisionInput", Map.of(
                                "decision", "NOT_A_DECISION",
                                "note", "Retained note")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"Retained note\"")))
                .andExpect(content().string(containsString("id=\"decision-form-error\"")));
    }

    @Test
    void retainedLeaveAllocationsAndCorrectionEventsRenderWithoutExposingRawMutationState() throws Exception {
        AttendanceActor intern = new AttendanceActor(7L, AttendanceRole.INTERN);
        when(currentUsers.actor(org.mockito.ArgumentMatchers.any())).thenReturn(intern);
        when(attendance.currentBusinessDate()).thenReturn(LocalDate.of(2026, 8, 21));
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
                .andExpect(content().string(containsString("28/08/2026 · quota month 08/2026")));

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

    @Test
    void correctionInstantsRenderInTheAttachedHistoricalPolicyZone() throws Exception {
        TimeZone previousZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        try {
            AttendanceActor mentor = new AttendanceActor(2L, AttendanceRole.MENTOR);
            when(currentUsers.actor(org.mockito.ArgumentMatchers.any())).thenReturn(mentor);
            when(leave.list(mentor)).thenReturn(List.of());
            when(corrections.list(mentor)).thenReturn(List.of());
            AttendancePolicy policy = new AttendancePolicy(
                    1L, LocalDate.of(2026, 1, 1), ZoneId.of("Asia/Ho_Chi_Minh"),
                    LocalTime.of(8, 0), LocalTime.of(17, 0), 15, 15, 3,
                    BigDecimal.valueOf(0.1), Set.of(DayOfWeek.MONDAY));
            when(corrections.view(mentor, 11L)).thenReturn(new CorrectionView(
                    11L, 55L, 7L,
                    Instant.parse("2026-08-20T18:00:00Z"),
                    LocalDateTime.of(2026, 8, 21, 1, 15),
                    Instant.parse("2026-08-20T18:30:00Z"),
                    "Missed", CorrectionStatus.APPROVED,
                    Instant.parse("2026-08-20T17:00:00Z"),
                    Instant.parse("2026-08-21T17:00:00Z"),
                    Instant.parse("2026-08-21T17:00:00Z"), null, policy,
                    new AttendanceViolations(false, false, false),
                    List.of(new CorrectionEventView(
                            1L, CorrectionEventType.APPROVED, CorrectionStatus.PENDING,
                            CorrectionStatus.APPROVED, 2L, "Approved",
                            Instant.parse("2026-08-20T19:00:00Z")))));

            mvc.perform(get("/attendance/corrections/11")
                            .with(user("mentor@example.test").roles("MENTOR")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("21/08/2026 01:00")))
                    .andExpect(content().string(containsString("21/08/2026 01:30")))
                    .andExpect(content().string(containsString("21/08/2026 02:00")))
                    .andExpect(content().string(containsString("Asia/Ho_Chi_Minh")));
        } finally {
            TimeZone.setDefault(previousZone);
        }
    }
}
