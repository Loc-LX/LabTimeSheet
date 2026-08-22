package com.lab.labtimesheet.feature.attendance.controller;

import com.lab.labtimesheet.feature.attendance.exception.CorrectionException;
import com.lab.labtimesheet.feature.attendance.exception.LeaveException;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRole;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionDecision;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionRequestCommand;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveRequestCommand;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveBalance;
import com.lab.labtimesheet.feature.attendance.service.AttendanceApplicationService;
import com.lab.labtimesheet.feature.attendance.service.AttendanceCorrectionApplicationService;
import com.lab.labtimesheet.feature.attendance.service.AttendanceCurrentUserService;
import com.lab.labtimesheet.feature.attendance.service.LeaveApplicationService;
import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
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
    private final AttendanceApplicationService attendance;
    private final LeaveApplicationService leave;
    private final AttendanceCorrectionApplicationService corrections;

    /**
     * Redirects the superseded combined queue to the focused Leave workflow.
     *
     * @param principal authenticated actor
     * @param model unused legacy view model
     * @return redirect to the focused Leave page
     */
    @GetMapping("/requests")
    public String requests(
            Principal principal,
            @RequestParam(required = false) Long attendanceRecordId,
            Model model) {
        return "redirect:/attendance/leave";
    }

    /**
     * Renders the Intern-owned Leave page or the global Mentor Leave queue.
     *
     * @param principal authenticated actor
     * @param month optional selected quota month in {@code yyyy-MM} form
     * @param model Thymeleaf model
     * @param redirectAttributes validation feedback destination
     * @return focused Leave page or a safe redirect for malformed month input
     */
    @GetMapping("/leave")
    public String leaveRequests(
            Principal principal,
            @RequestParam(required = false) String month,
            Model model,
            RedirectAttributes redirectAttributes) {
        AttendanceActor actor = currentUsers.actor(principal);
        try {
            YearMonth selectedMonth = month == null || month.isBlank()
                    ? YearMonth.from(attendance.currentBusinessDate())
                    : YearMonth.parse(month.strip());
            model.addAttribute("actor", actor);
            model.addAttribute("intern", actor.role() == AttendanceRole.INTERN);
            model.addAttribute("mentor", actor.role() == AttendanceRole.MENTOR);
            model.addAttribute("leaveRequests", leave.list(actor));
            model.addAttribute("selectedMonth", selectedMonth);
            if (actor.role() == AttendanceRole.INTERN) {
                LeaveBalance balance = leave.balance(actor, selectedMonth);
                model.addAttribute("balance", balance);
            }
            return "attendance/leave";
        } catch (IllegalArgumentException failure) {
            redirectAttributes.addFlashAttribute("requestError", "Choose a valid leave balance month.");
            return "redirect:/attendance/leave";
        }
    }

    /**
     * Renders the Intern-owned Correction page or the global Mentor decision queue.
     *
     * @param principal authenticated actor
     * @param attendanceRecordId optional attendance row retained from the history CTA
     * @param model Thymeleaf model
     * @return focused Correction page
     */
    @GetMapping("/corrections")
    public String correctionRequests(
            Principal principal,
            @RequestParam(required = false) Long attendanceRecordId,
            Model model) {
        AttendanceActor actor = currentUsers.actor(principal);
        model.addAttribute("actor", actor);
        model.addAttribute("intern", actor.role() == AttendanceRole.INTERN);
        model.addAttribute("mentor", actor.role() == AttendanceRole.MENTOR);
        model.addAttribute("attendanceRecordId", attendanceRecordId);
        model.addAttribute("correctionRequests", corrections.list(actor));
        return "attendance/corrections";
    }

    /**
     * Renders one authorized leave request with its frozen allocation history.
     *
     * @param principal authenticated actor
     * @param requestId leave request identifier
     * @param model Thymeleaf model
     * @return focused Leave page with the selected request
     */
    @GetMapping("/leave/{requestId}")
    public String leaveRequest(Principal principal, @PathVariable long requestId, Model model) {
        AttendanceActor actor = currentUsers.actor(principal);
        model.addAttribute("actor", actor);
        model.addAttribute("intern", actor.role() == AttendanceRole.INTERN);
        model.addAttribute("mentor", actor.role() == AttendanceRole.MENTOR);
        model.addAttribute("leaveRequests", leave.list(actor));
        model.addAttribute("selectedLeave", leave.view(actor, requestId));
        if (actor.role() == AttendanceRole.INTERN) {
            YearMonth selectedMonth = YearMonth.from(attendance.currentBusinessDate());
            model.addAttribute("selectedMonth", selectedMonth);
            model.addAttribute("balance", leave.balance(actor, selectedMonth));
        }
        return "attendance/leave";
    }

    /**
     * Renders one authorized correction with its raw/effective values and transition events.
     *
     * @param principal authenticated actor
     * @param correctionId correction identifier
     * @param model Thymeleaf model
     * @return focused Correction page with the selected request
     */
    @GetMapping("/corrections/{correctionId}")
    public String correction(Principal principal, @PathVariable long correctionId, Model model) {
        AttendanceActor actor = currentUsers.actor(principal);
        model.addAttribute("actor", actor);
        model.addAttribute("intern", actor.role() == AttendanceRole.INTERN);
        model.addAttribute("mentor", actor.role() == AttendanceRole.MENTOR);
        model.addAttribute("correctionRequests", corrections.list(actor));
        model.addAttribute("selectedCorrection", corrections.view(actor, correctionId));
        return "attendance/corrections";
    }

    /**
     * Submits one inclusive full-day leave range for the authenticated Intern.
     *
     * @param principal authenticated Intern
     * @param startDate raw inclusive leave start
     * @param endDate raw inclusive leave end
     * @param reason user-supplied leave reason
     * @param redirectAttributes validation feedback destination
     * @return detail redirect for success, or focused Leave page after failure
     */
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
            return "redirect:/attendance/leave";
        }
    }

    /**
     * Revalidates and replaces one still-editable pending leave request.
     *
     * @param principal authenticated Intern owner
     * @param requestId leave request identifier
     * @param startDate replacement inclusive leave start
     * @param endDate replacement inclusive leave end
     * @param reason replacement leave reason
     * @param redirectAttributes validation feedback destination
     * @return selected Leave detail redirect
     */
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

    /**
     * Cancels an authorized leave request while its service deadline remains open.
     *
     * @param principal authenticated actor
     * @param requestId leave request identifier
     * @param redirectAttributes transition feedback destination
     * @return selected Leave detail redirect
     */
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

    /**
     * Applies the Mentor-only approval transition.
     *
     * @param principal authenticated Mentor
     * @param requestId leave request identifier
     * @param redirectAttributes transition feedback destination
     * @return selected Leave detail redirect
     */
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

    /**
     * Applies the Mentor-only rejection transition.
     *
     * @param principal authenticated Mentor
     * @param requestId leave request identifier
     * @param redirectAttributes transition feedback destination
     * @return selected Leave detail redirect
     */
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

    /**
     * Submits one proposed effective checkout without changing the raw attendance row.
     *
     * @param principal authenticated Intern owner
     * @param attendanceRecordId attached attendance identifier
     * @param proposedCheckout proposed local checkout timestamp
     * @param reason user-supplied correction reason
     * @param redirectAttributes validation feedback destination
     * @return detail redirect for success, or focused Correction page after failure
     */
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
            return "redirect:/attendance/corrections";
        }
    }

    /**
     * Applies one Mentor correction decision and retains the service-generated event.
     *
     * @param principal authenticated Mentor
     * @param correctionId correction identifier
     * @param decision requested state transition
     * @param note optional decision note
     * @param redirectAttributes transition feedback destination
     * @return selected Correction detail redirect
     */
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
