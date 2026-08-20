package com.lab.labtimesheet.feature.attendance.controller;

import com.lab.labtimesheet.feature.attendance.exception.CorrectionException;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRole;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionDecisionCommand;
import com.lab.labtimesheet.feature.attendance.service.AttendanceCurrentUserService;
import com.lab.labtimesheet.feature.attendance.service.CorrectionService;
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
 * Server-rendered Mentor correction routes: the pending-decision list and approve/reject/revert actions inside
 * each request's 24-hour decision window.
 */
@Controller
@RequestMapping("/mentor/corrections")
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class MentorCorrectionController {

    private final CorrectionService corrections;
    private final AttendanceCurrentUserService currentUsers;

    /**
     * Renders every correction newest-first with its decision boundary and derived action availability.
     *
     * @param principal authenticated Mentor
     * @param model Thymeleaf model
     * @return Mentor corrections view name
     */
    @GetMapping
    public String form(Principal principal, Model model) {
        requireMentor(currentUsers.actor(principal));
        model.addAttribute("decisions", corrections.decisions());
        return "attendance/mentor-corrections";
    }

    /**
     * Approves a pending correction before its decision deadline and redirects with stable feedback.
     *
     * @param principal authenticated Mentor
     * @param correctionId correction identifier
     * @param decisionNote optional note
     * @param redirectAttributes flash-message destination
     * @return redirect to the Mentor corrections page
     */
    @PostMapping("/{correctionId}/approve")
    public String approve(
            Principal principal,
            @PathVariable long correctionId,
            @RequestParam(required = false) String decisionNote,
            RedirectAttributes redirectAttributes) {
        return decide(principal, correctionId, new CorrectionDecisionCommand(true, decisionNote), redirectAttributes);
    }

    /**
     * Rejects a pending correction before its decision deadline and redirects with stable feedback.
     *
     * @param principal authenticated Mentor
     * @param correctionId correction identifier
     * @param decisionNote optional note
     * @param redirectAttributes flash-message destination
     * @return redirect to the Mentor corrections page
     */
    @PostMapping("/{correctionId}/reject")
    public String reject(
            Principal principal,
            @PathVariable long correctionId,
            @RequestParam(required = false) String decisionNote,
            RedirectAttributes redirectAttributes) {
        return decide(principal, correctionId, new CorrectionDecisionCommand(false, decisionNote), redirectAttributes);
    }

    /**
     * Reverts a decided correction back to PENDING inside its decision window and redirects with stable feedback.
     *
     * @param principal authenticated Mentor
     * @param correctionId correction identifier
     * @param note optional revert note
     * @param redirectAttributes flash-message destination
     * @return redirect to the Mentor corrections page
     */
    @PostMapping("/{correctionId}/revert")
    public String revert(
            Principal principal,
            @PathVariable long correctionId,
            @RequestParam(required = false) String note,
            RedirectAttributes redirectAttributes) {
        requireMentor(currentUsers.actor(principal));
        try {
            corrections.revert(currentUsers.actor(principal).userId(), correctionId, note);
            redirectAttributes.addFlashAttribute(
                    "message", "Correction " + correctionId + " reverted to pending");
        } catch (CorrectionException exception) {
            redirectAttributes.addFlashAttribute("error", exception.rejection().name());
        }
        return "redirect:/mentor/corrections";
    }

    private String decide(
            Principal principal,
            long correctionId,
            CorrectionDecisionCommand command,
            RedirectAttributes redirectAttributes) {
        requireMentor(currentUsers.actor(principal));
        try {
            corrections.decide(currentUsers.actor(principal).userId(), correctionId, command);
            redirectAttributes.addFlashAttribute(
                    "message", "Correction " + correctionId + (command.approved() ? " approved" : " rejected"));
        } catch (CorrectionException exception) {
            redirectAttributes.addFlashAttribute("error", exception.rejection().name());
        }
        return "redirect:/mentor/corrections";
    }

    private static AttendanceActor requireMentor(AttendanceActor actor) {
        if (actor.role() != AttendanceRole.MENTOR) {
            throw new AccessDeniedException("Only Mentors may decide corrections");
        }
        return actor;
    }
}