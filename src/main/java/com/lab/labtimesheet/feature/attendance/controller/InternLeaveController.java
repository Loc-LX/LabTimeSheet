package com.lab.labtimesheet.feature.attendance.controller;

import com.lab.labtimesheet.feature.attendance.exception.LeaveException;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRole;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveSubmission;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveSubmissionCommand;
import com.lab.labtimesheet.feature.attendance.service.AttendanceApplicationService;
import com.lab.labtimesheet.feature.attendance.service.AttendanceCurrentUserService;
import com.lab.labtimesheet.feature.attendance.service.LeaveService;
import java.security.Principal;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Server-rendered Intern leave routes: the request form with monthly quota stats and prior requests.
 */
@Controller
@RequestMapping("/intern/leave")
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class InternLeaveController {

    private final LeaveService leave;
    private final AttendanceApplicationService attendance;
    private final AttendanceCurrentUserService currentUsers;

    /**
     * Renders the Intern leave form for the current business month, showing quota, reservation, and prior requests.
     *
     * @param principal authenticated Intern
     * @param model Thymeleaf model
     * @return Intern leave view name
     */
    @GetMapping
    public String form(Principal principal, Model model) {
        AttendanceActor actor = requireIntern(currentUsers.actor(principal));
        LocalDate month = attendance.currentBusinessDate().withDayOfMonth(1);
        model.addAttribute("overview", leave.overview(actor.userId(), month));
        return "attendance/intern-leave";
    }

    /**
     * Submits an inclusive full-day leave range for the authenticated Intern and redirects with stable feedback.
     *
     * @param principal authenticated Intern
     * @param startDate inclusive first local date
     * @param endDate inclusive last local date
     * @param reason non-blank reason
     * @param redirectAttributes flash-message destination
     * @return redirect to the Intern leave form
     */
    @PostMapping
    public String submit(
            Principal principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam String reason,
            RedirectAttributes redirectAttributes) {
        AttendanceActor actor = requireIntern(currentUsers.actor(principal));
        try {
            LeaveSubmission submission = leave.submit(
                    actor.userId(), new LeaveSubmissionCommand(startDate, endDate, reason));
            redirectAttributes.addFlashAttribute(
                    "message",
                    "Leave submitted for " + submission.countedDays().size() + " counted day(s)");
        } catch (LeaveException exception) {
            redirectAttributes.addFlashAttribute("error", exception.rejection().name());
        }
        return "redirect:/intern/leave";
    }

    /**
     * Cancels the authenticated Intern's pending or approved request before its boundary and redirects.
     *
     * @param principal authenticated Intern
     * @param requestId request identifier
     * @param redirectAttributes flash-message destination
     * @return redirect to the Intern leave form
     */
    @PostMapping("/{requestId}/cancel")
    public String cancel(
            Principal principal,
            @PathVariable long requestId,
            RedirectAttributes redirectAttributes) {
        AttendanceActor actor = requireIntern(currentUsers.actor(principal));
        try {
            LeaveSubmission cancelled = leave.cancel(actor.userId(), requestId);
            redirectAttributes.addFlashAttribute(
                    "message",
                    "Leave " + cancelled.status().toLowerCase() + " (" + cancelled.countedDays().size()
                            + " counted day(s) released)");
        } catch (LeaveException exception) {
            redirectAttributes.addFlashAttribute("error", exception.rejection().name());
        }
        return "redirect:/intern/leave";
    }

    /**
     * Replaces the authenticated Intern's pending request before its boundary and redirects.
     *
     * @param principal authenticated Intern
     * @param requestId request identifier
     * @param startDate inclusive new first local date
     * @param endDate inclusive new last local date
     * @param reason new reason
     * @param redirectAttributes flash-message destination
     * @return redirect to the Intern leave form
     */
    @PostMapping("/{requestId}/edit")
    public String edit(
            Principal principal,
            @PathVariable long requestId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam String reason,
            RedirectAttributes redirectAttributes) {
        AttendanceActor actor = requireIntern(currentUsers.actor(principal));
        try {
            LeaveSubmission edited = leave.edit(
                    actor.userId(), requestId, new LeaveSubmissionCommand(startDate, endDate, reason));
            redirectAttributes.addFlashAttribute(
                    "message",
                    "Leave updated to " + edited.countedDays().size() + " counted day(s)");
        } catch (LeaveException exception) {
            redirectAttributes.addFlashAttribute("error", exception.rejection().name());
        }
        return "redirect:/intern/leave";
    }

    private static AttendanceActor requireIntern(AttendanceActor actor) {
        if (actor.role() != AttendanceRole.INTERN) {
            throw new AccessDeniedException("Only Interns may request leave");
        }
        return actor;
    }
}