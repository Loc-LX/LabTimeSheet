package com.lab.labtimesheet.feature.attendance.controller;

import com.lab.labtimesheet.feature.attendance.exception.CorrectionException;
import com.lab.labtimesheet.feature.attendance.exception.LeaveException;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRole;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionDecision;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionRequestCommand;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveRequestCommand;
import com.lab.labtimesheet.feature.attendance.service.AttendanceCorrectionApplicationService;
import com.lab.labtimesheet.feature.attendance.service.AttendanceCurrentUserService;
import com.lab.labtimesheet.feature.attendance.service.LeaveApplicationService;
import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Map;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Exposes discoverable leave and missed-checkout correction pages without duplicating either
 * service state machine. Every read and mutation delegates the authenticated Attendance actor to
 * the owning application service, which remains authoritative for role, ownership, deadlines,
 * locking, and retained history.
 */
@Controller
@RequestMapping("/attendance")
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class AttendanceRequestController {

    private final AttendanceCurrentUserService currentUsers;
    private final LeaveApplicationService leave;
    private final AttendanceCorrectionApplicationService corrections;

    /** Renders the actor-scoped request queue and Intern submission forms. */
    @GetMapping("/requests")
    public String requests(
            Principal principal,
            @RequestParam(required = false) Long attendanceRecordId,
            Model model) {
        AttendanceActor actor = currentUsers.actor(principal);
        populate(actor, model);
        model.addAttribute("attendanceRecordId", attendanceRecordId);
        return "attendance/requests";
    }

    /** Renders one authorized leave request with its frozen allocation history. */
    @GetMapping("/leave/{requestId}")
    public String leaveRequest(Principal principal, @PathVariable long requestId, Model model) {
        AttendanceActor actor = currentUsers.actor(principal);
        populate(actor, model);
        model.addAttribute("selectedLeave", leave.view(actor, requestId));
        return "attendance/requests";
    }

    /** Renders one authorized correction with its raw/effective values and transition events. */
    @GetMapping("/corrections/{correctionId}")
    public String correction(Principal principal, @PathVariable long correctionId, Model model) {
        AttendanceActor actor = currentUsers.actor(principal);
        populate(actor, model);
        model.addAttribute("selectedCorrection", corrections.view(actor, correctionId));
        return "attendance/requests";
    }

    /** Submits one inclusive full-day leave range for the authenticated Intern. */
    @PostMapping("/leave")
    public String submitLeave(
            Principal principal,
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam String reason,
            RedirectAttributes redirectAttributes) {
        AttendanceActor actor = currentUsers.actor(principal);
        try {
            long id = leave.submit(actor, new LeaveRequestCommand(
                            requiredDate(startDate, "Enter valid leave dates."),
                            requiredDate(endDate, "Enter valid leave dates."),
                            reason))
                    .id();
            return "redirect:/attendance/leave/" + id;
        } catch (LeaveException | IllegalArgumentException failure) {
            redirectAttributes.addFlashAttribute("requestError", failure.getMessage());
            redirectAttributes.addFlashAttribute("leaveInput", Map.of(
                    "startDate", startDate,
                    "endDate", endDate,
                    "reason", reason));
            return "redirect:/attendance/requests";
        }
    }

    /** Revalidates and replaces one still-editable pending leave request. */
    @PostMapping("/leave/{requestId}/edit")
    public String editLeave(
            Principal principal,
            @PathVariable long requestId,
            @RequestParam String startDate,
            @RequestParam String endDate,
            @RequestParam String reason,
            RedirectAttributes redirectAttributes) {
        try {
            leave.edit(currentUsers.actor(principal), requestId, new LeaveRequestCommand(
                    requiredDate(startDate, "Enter valid leave dates."),
                    requiredDate(endDate, "Enter valid leave dates."),
                    reason));
            redirectAttributes.addFlashAttribute("message", "Leave request updated");
        } catch (LeaveException | IllegalArgumentException failure) {
            redirectAttributes.addFlashAttribute("requestError", failure.getMessage());
            redirectAttributes.addFlashAttribute("leaveEditInput", Map.of(
                    "startDate", startDate,
                    "endDate", endDate,
                    "reason", reason));
        }
        return "redirect:/attendance/leave/" + requestId;
    }

    /** Cancels an authorized leave request while its service deadline remains open. */
    @PostMapping("/leave/{requestId}/cancel")
    public String cancelLeave(
            Principal principal, @PathVariable long requestId, RedirectAttributes redirectAttributes) {
        try {
            leave.cancel(currentUsers.actor(principal), requestId);
            redirectAttributes.addFlashAttribute("message", "Leave request cancelled");
        } catch (LeaveException failure) {
            redirectAttributes.addFlashAttribute("requestError", failure.getMessage());
        }
        return "redirect:/attendance/leave/" + requestId;
    }

    /** Applies the Mentor-only approval transition. */
    @PostMapping("/leave/{requestId}/approve")
    public String approveLeave(
            Principal principal, @PathVariable long requestId, RedirectAttributes redirectAttributes) {
        try {
            leave.approve(currentUsers.actor(principal), requestId);
            redirectAttributes.addFlashAttribute("message", "Leave request approved");
        } catch (LeaveException failure) {
            redirectAttributes.addFlashAttribute("requestError", failure.getMessage());
        }
        return "redirect:/attendance/leave/" + requestId;
    }

    /** Applies the Mentor-only rejection transition. */
    @PostMapping("/leave/{requestId}/reject")
    public String rejectLeave(
            Principal principal, @PathVariable long requestId, RedirectAttributes redirectAttributes) {
        try {
            leave.reject(currentUsers.actor(principal), requestId);
            redirectAttributes.addFlashAttribute("message", "Leave request rejected");
        } catch (LeaveException failure) {
            redirectAttributes.addFlashAttribute("requestError", failure.getMessage());
        }
        return "redirect:/attendance/leave/" + requestId;
    }

    /** Submits one proposed effective checkout without changing the raw attendance row. */
    @PostMapping("/corrections")
    public String submitCorrection(
            Principal principal,
            @RequestParam String attendanceRecordId,
            @RequestParam String proposedCheckout,
            @RequestParam String reason,
            RedirectAttributes redirectAttributes) {
        AttendanceActor actor = currentUsers.actor(principal);
        try {
            long id = corrections
                    .submit(
                            actor,
                            requiredLong(
                                    attendanceRecordId,
                                    "Enter a valid attendance record and proposed checkout."),
                            new CorrectionRequestCommand(
                                    requiredDateTime(
                                            proposedCheckout,
                                            "Enter a valid attendance record and proposed checkout."),
                                    reason))
                    .id();
            return "redirect:/attendance/corrections/" + id;
        } catch (CorrectionException | IllegalArgumentException failure) {
            redirectAttributes.addFlashAttribute("requestError", failure.getMessage());
            redirectAttributes.addFlashAttribute("correctionInput", Map.of(
                    "attendanceRecordId", attendanceRecordId,
                    "proposedCheckout", proposedCheckout,
                    "reason", reason));
            return "redirect:/attendance/requests";
        }
    }

    /** Applies one Mentor correction decision and retains the service-generated event. */
    @PostMapping("/corrections/{correctionId}/decide")
    public String decideCorrection(
            Principal principal,
            @PathVariable long correctionId,
            @RequestParam String decision,
            @RequestParam(required = false) String note,
            RedirectAttributes redirectAttributes) {
        try {
            corrections.decide(
                    currentUsers.actor(principal),
                    correctionId,
                    requiredDecision(decision),
                    note);
            redirectAttributes.addFlashAttribute("message", "Correction decision saved");
        } catch (CorrectionException | IllegalArgumentException failure) {
            redirectAttributes.addFlashAttribute("requestError", failure.getMessage());
            redirectAttributes.addFlashAttribute("correctionDecisionInput", Map.of(
                    "decision", decision,
                    "note", note == null ? "" : note));
        }
        return "redirect:/attendance/corrections/" + correctionId;
    }

    private void populate(AttendanceActor actor, Model model) {
        model.addAttribute("actor", actor);
        model.addAttribute("intern", actor.role() == AttendanceRole.INTERN);
        model.addAttribute("mentor", actor.role() == AttendanceRole.MENTOR);
        model.addAttribute("leaveRequests", leave.list(actor));
        model.addAttribute("correctionRequests", corrections.list(actor));
    }

    private static LocalDate requiredDate(String value, String errorMessage) {
        try {
            return LocalDate.parse(value.strip());
        } catch (DateTimeParseException failure) {
            throw new IllegalArgumentException(errorMessage, failure);
        }
    }

    private static LocalDateTime requiredDateTime(String value, String errorMessage) {
        try {
            return LocalDateTime.parse(value.strip());
        } catch (DateTimeParseException failure) {
            throw new IllegalArgumentException(errorMessage, failure);
        }
    }

    private static long requiredLong(String value, String errorMessage) {
        try {
            return Long.parseLong(value.strip());
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException(errorMessage, failure);
        }
    }

    private static CorrectionDecision requiredDecision(String value) {
        try {
            return CorrectionDecision.valueOf(value.strip());
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("Choose a valid correction decision.", failure);
        }
    }
}
