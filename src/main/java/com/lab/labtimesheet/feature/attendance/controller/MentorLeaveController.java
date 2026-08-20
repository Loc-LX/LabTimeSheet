package com.lab.labtimesheet.feature.attendance.controller;

import com.lab.labtimesheet.feature.attendance.exception.LeaveException;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRole;
import com.lab.labtimesheet.feature.attendance.model.dto.LeaveDecisionCommand;
import com.lab.labtimesheet.feature.attendance.service.AttendanceCurrentUserService;
import com.lab.labtimesheet.feature.attendance.service.LeaveService;
import java.security.Principal;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
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
 * Server-rendered Mentor leave routes: the pending-decision list and approve/reject actions.
 */
@Controller
@RequestMapping("/mentor/leave")
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class MentorLeaveController {

    private final LeaveService leave;
    private final AttendanceCurrentUserService currentUsers;

    /**
     * Renders every leave request newest-first with its decision boundary.
     *
     * @param principal authenticated Mentor
     * @param model Thymeleaf model
     * @return Mentor leave decisions view name
     */
    @GetMapping
    public String form(Principal principal, Model model) {
        requireMentor(currentUsers.actor(principal));
        model.addAttribute("decisions", leave.decisions());
        return "attendance/mentor-leave";
    }

    /**
     * Approves a pending request before its boundary and redirects with stable feedback.
     *
     * @param principal authenticated Mentor
     * @param requestId request identifier
     * @param decisionNote optional note
     * @param redirectAttributes flash-message destination
     * @return redirect to the Mentor leave decisions page
     */
    @PostMapping("/{requestId}/approve")
    public String approve(
            Principal principal,
            @PathVariable long requestId,
            @RequestParam(required = false) String decisionNote,
            RedirectAttributes redirectAttributes) {
        return decide(principal, requestId, new LeaveDecisionCommand(true, decisionNote), redirectAttributes);
    }

    /**
     * Rejects a pending request before its boundary and redirects with stable feedback.
     *
     * @param principal authenticated Mentor
     * @param requestId request identifier
     * @param decisionNote optional note
     * @param redirectAttributes flash-message destination
     * @return redirect to the Mentor leave decisions page
     */
    @PostMapping("/{requestId}/reject")
    public String reject(
            Principal principal,
            @PathVariable long requestId,
            @RequestParam(required = false) String decisionNote,
            RedirectAttributes redirectAttributes) {
        return decide(principal, requestId, new LeaveDecisionCommand(false, decisionNote), redirectAttributes);
    }

    private String decide(
            Principal principal,
            long requestId,
            LeaveDecisionCommand command,
            RedirectAttributes redirectAttributes) {
        requireMentor(currentUsers.actor(principal));
        try {
            leave.decide(
                    currentUsers.actor(principal).userId(), requestId, command);
            redirectAttributes.addFlashAttribute(
                    "message", "Request " + requestId + (command.approved() ? " approved" : " rejected"));
        } catch (LeaveException exception) {
            redirectAttributes.addFlashAttribute("error", exception.rejection().name());
        }
        return "redirect:/mentor/leave";
    }

    private static AttendanceActor requireMentor(AttendanceActor actor) {
        if (actor.role() != AttendanceRole.MENTOR) {
            throw new AccessDeniedException("Only Mentors may decide leave");
        }
        return actor;
    }
}