package com.lab.labtimesheet.feature.attendance.controller;

import com.lab.labtimesheet.feature.attendance.exception.CorrectionException;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRole;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionSubmission;
import com.lab.labtimesheet.feature.attendance.model.dto.CorrectionSubmissionCommand;
import com.lab.labtimesheet.feature.attendance.service.AttendanceApplicationService;
import com.lab.labtimesheet.feature.attendance.service.AttendanceCurrentUserService;
import com.lab.labtimesheet.feature.attendance.service.CorrectionService;
import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalTime;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Server-rendered Intern missed-checkout correction routes: the form and prior corrections.
 */
@Controller
@RequestMapping("/intern/corrections")
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class InternCorrectionController {

    private final CorrectionService corrections;
    private final AttendanceApplicationService attendance;
    private final AttendanceCurrentUserService currentUsers;

    /**
     * Renders the Intern correction form for the current business month with prior corrections newest-first.
     *
     * @param principal authenticated Intern
     * @param model Thymeleaf model
     * @return Intern correction view name
     */
    @GetMapping
    public String form(Principal principal, Model model) {
        AttendanceActor actor = requireIntern(currentUsers.actor(principal));
        LocalDate month = attendance.currentBusinessDate().withDayOfMonth(1);
        model.addAttribute("overview", corrections.overview(actor.userId(), month));
        return "attendance/intern-corrections";
    }

    /**
     * Submits a missed-checkout correction for the authenticated Intern and redirects with stable feedback.
     *
     * @param principal authenticated Intern
     * @param workDate policy-local attendance date
     * @param proposedCheckoutTime proposed checkout time in the policy timezone
     * @param reason non-blank reason
     * @param redirectAttributes flash-message destination
     * @return redirect to the Intern correction form
     */
    @PostMapping
    public String submit(
            Principal principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate workDate,
            @RequestParam @DateTimeFormat(pattern = "HH:mm") LocalTime proposedCheckoutTime,
            @RequestParam String reason,
            RedirectAttributes redirectAttributes) {
        AttendanceActor actor = requireIntern(currentUsers.actor(principal));
        try {
            CorrectionSubmission submission = corrections.submit(
                    actor.userId(), new CorrectionSubmissionCommand(workDate, proposedCheckoutTime, reason));
            redirectAttributes.addFlashAttribute(
                    "message",
                    "Correction submitted for " + submission.workDateDisplay() + " at "
                            + submission.proposedCheckoutTimeDisplay());
        } catch (CorrectionException exception) {
            redirectAttributes.addFlashAttribute("error", exception.rejection().name());
        }
        return "redirect:/intern/corrections";
    }

    private static AttendanceActor requireIntern(AttendanceActor actor) {
        if (actor.role() != AttendanceRole.INTERN) {
            throw new AccessDeniedException("Only Interns may submit corrections");
        }
        return actor;
    }
}