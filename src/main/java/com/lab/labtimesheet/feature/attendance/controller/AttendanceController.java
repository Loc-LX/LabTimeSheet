package com.lab.labtimesheet.feature.attendance.controller;

import com.lab.labtimesheet.feature.attendance.exception.AttendanceException;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRole;
import com.lab.labtimesheet.feature.attendance.service.AttendanceApplicationService;
import com.lab.labtimesheet.feature.attendance.service.AttendanceCurrentUserService;
import java.security.Principal;
import java.time.LocalDate;
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
 * Server-rendered attendance routes for Intern punches and role-scoped historical inspection.
 */
@Controller
@RequestMapping("/attendance")
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class AttendanceController {

    private final AttendanceApplicationService attendance;
    private final AttendanceCurrentUserService currentUsers;

    /**
     * Renders the authenticated Intern's inclusive attendance history, defaulting to the current month.
     *
     * @param principal authenticated user
     * @param from optional inclusive local start date
     * @param to optional inclusive local end date
     * @param model Thymeleaf model
     * @return attendance history view name
     */
    @GetMapping
    public String ownHistory(
            Principal principal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Model model) {
        AttendanceActor actor = requireIntern(currentUsers.actor(principal));
        return history(actor, actor.userId(), from, to, model);
    }

    /**
     * Renders a target Intern's history for an authenticated Mentor or Admin.
     *
     * @param principal authenticated inspecting user
     * @param internId target Intern account identifier
     * @param from optional inclusive local start date
     * @param to optional inclusive local end date
     * @param model Thymeleaf model
     * @return attendance history view name
     */
    @GetMapping("/interns/{internId}")
    public String inspectHistory(
            Principal principal,
            @org.springframework.web.bind.annotation.PathVariable long internId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            Model model) {
        AttendanceActor actor = currentUsers.actor(principal);
        if (actor.role() == AttendanceRole.INTERN) {
            throw new AccessDeniedException("Intern inspection is not allowed");
        }
        return history(actor, internId, from, to, model);
    }

    /**
     * Checks in the authenticated Intern using server time and redirects with stable feedback.
     *
     * @param principal authenticated Intern
     * @param redirectAttributes flash-message destination
     * @return redirect to own attendance history
     */
    @PostMapping("/check-in")
    public String checkIn(Principal principal, RedirectAttributes redirectAttributes) {
        AttendanceActor actor = requireIntern(currentUsers.actor(principal));
        try {
            attendance.checkIn(actor.userId());
            redirectAttributes.addFlashAttribute("message", "Checked in");
        } catch (AttendanceException exception) {
            redirectAttributes.addFlashAttribute("error", exception.rejection().name());
        }
        return "redirect:/attendance";
    }

    /**
     * Checks out the authenticated Intern using server time and redirects with stable feedback.
     *
     * @param principal authenticated Intern
     * @param redirectAttributes flash-message destination
     * @return redirect to own attendance history
     */
    @PostMapping("/check-out")
    public String checkOut(Principal principal, RedirectAttributes redirectAttributes) {
        AttendanceActor actor = requireIntern(currentUsers.actor(principal));
        try {
            attendance.checkOut(actor.userId());
            redirectAttributes.addFlashAttribute("message", "Checked out");
        } catch (AttendanceException exception) {
            redirectAttributes.addFlashAttribute("error", exception.rejection().name());
        }
        return "redirect:/attendance";
    }

    private String history(
            AttendanceActor actor,
            long internId,
            LocalDate from,
            LocalDate to,
            Model model) {
        LocalDate effectiveTo = to == null ? attendance.currentBusinessDate() : to;
        LocalDate effectiveFrom = from == null ? effectiveTo.withDayOfMonth(1) : from;
        model.addAttribute("items", attendance.history(actor, internId, effectiveFrom, effectiveTo));
        model.addAttribute("targetInternId", internId);
        model.addAttribute("from", effectiveFrom);
        model.addAttribute("to", effectiveTo);
        model.addAttribute("ownHistory", actor.userId() == internId);
        return "attendance/history";
    }

    private static AttendanceActor requireIntern(AttendanceActor actor) {
        if (actor.role() != AttendanceRole.INTERN) {
            throw new AccessDeniedException("Only Interns may punch attendance");
        }
        return actor;
    }
}
