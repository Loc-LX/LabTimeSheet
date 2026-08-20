package com.lab.labtimesheet.feature.attendance.controller;

import com.lab.labtimesheet.feature.attendance.exception.PolicyException;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRole;
import com.lab.labtimesheet.feature.attendance.model.dto.PolicyScheduleForm;
import com.lab.labtimesheet.feature.attendance.service.AttendanceApplicationService;
import com.lab.labtimesheet.feature.attendance.service.AttendanceCurrentUserService;
import com.lab.labtimesheet.feature.attendance.service.AttendancePolicyService;
import java.math.BigDecimal;
import java.security.Principal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Admin-only server-rendered routes for scheduling and replacing future attendance-policy versions.
 */
@Controller
@RequestMapping("/attendance/policy")
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class AttendancePolicyController {

    private final AttendancePolicyService policies;
    private final AttendanceApplicationService attendance;
    private final AttendanceCurrentUserService currentUsers;

    /**
     * Renders the policy timeline and a new-version form for an authenticated Admin.
     *
     * @param principal authenticated Admin
     * @param model Thymeleaf model
     * @return attendance policy management view name
     */
    @GetMapping
    public String policy(Principal principal, Model model) {
        requireAdmin(currentUsers.actor(principal));
        LocalDate today = attendance.currentBusinessDate();
        model.addAttribute("versions", policies.versions());
        model.addAttribute("today", today);
        model.addAttribute("form", blankForm(today));
        model.addAttribute("days", DayOfWeek.values());
        return "attendance/policy";
    }

    /**
     * Schedules a new future policy version attributed to the authenticated Admin.
     *
     * @param principal authenticated Admin
     * @param form bound schedule values
     * @param redirectAttributes flash-message destination
     * @return redirect to the policy management screen
     */
    @PostMapping
    public String schedule(
            Principal principal, @ModelAttribute PolicyScheduleForm form, RedirectAttributes redirectAttributes) {
        AttendanceActor actor = requireAdmin(currentUsers.actor(principal));
        try {
            policies.scheduleVersion(actor, form.toCommand());
            redirectAttributes.addFlashAttribute("message", "Attendance policy version scheduled");
        } catch (PolicyException | IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/attendance/policy";
    }

    /**
     * Replaces a scheduled-but-not-yet-effective version using the submitted optimistic version.
     *
     * @param principal authenticated Admin
     * @param policyId policy version identifier
     * @param version optimistic version rendered to the editor
     * @param form bound replacement schedule values
     * @param redirectAttributes flash-message destination
     * @return redirect to the policy management screen
     */
    @PostMapping("/{policyId}")
    public String replace(
            Principal principal,
            @PathVariable long policyId,
            @RequestParam long version,
            @ModelAttribute PolicyScheduleForm form,
            RedirectAttributes redirectAttributes) {
        AttendanceActor actor = requireAdmin(currentUsers.actor(principal));
        try {
            policies.replaceVersion(actor, policyId, version, form.toCommand());
            redirectAttributes.addFlashAttribute("message", "Attendance policy version replaced");
        } catch (PolicyException | IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/attendance/policy";
    }

    private static PolicyScheduleForm blankForm(LocalDate today) {
        PolicyScheduleForm form = new PolicyScheduleForm();
        form.setEffectiveFrom(today.plusMonths(1).withDayOfMonth(1));
        form.setZoneId("Asia/Ho_Chi_Minh");
        form.setScheduledStart(LocalTime.of(8, 30));
        form.setScheduledEnd(LocalTime.of(15, 30));
        form.setCheckInGraceMinutes(30);
        form.setCheckoutGraceMinutes(30);
        form.setMonthlyLeaveQuota(3);
        form.setViolationPenalty(new BigDecimal("0.25"));
        form.setWorkdays(new LinkedHashSet<>(Set.of(
                DayOfWeek.MONDAY,
                DayOfWeek.TUESDAY,
                DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY,
                DayOfWeek.FRIDAY)));
        return form;
    }

    private static AttendanceActor requireAdmin(AttendanceActor actor) {
        if (actor.role() != AttendanceRole.ADMIN) {
            throw new AccessDeniedException("Only Admin may manage attendance policy versions");
        }
        return actor;
    }
}