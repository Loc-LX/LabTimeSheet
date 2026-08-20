package com.lab.labtimesheet.feature.attendance.controller;

import com.lab.labtimesheet.feature.attendance.exception.PolicyException;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRole;
import com.lab.labtimesheet.feature.attendance.model.dto.PolicyScheduleForm;
import com.lab.labtimesheet.feature.attendance.service.AttendanceApplicationService;
import com.lab.labtimesheet.feature.attendance.service.AttendanceCurrentUserService;
import com.lab.labtimesheet.feature.attendance.service.AttendancePolicyService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.security.Principal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Admin-only server-rendered routes for scheduling and replacing future attendance-policy versions.
 * The schedule form re-renders with retained input and field errors on invalid submissions (ERR-001);
 * per-row replacement keeps a flash-message redirect while still rejecting unsafe values without 500s.
 */
@Controller
@RequestMapping("/attendance/policy")
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class AttendancePolicyController {

    private static final List<String> TIMEZONES = List.of(
            "Asia/Ho_Chi_Minh",
            "Asia/Bangkok",
            "Asia/Phnom_Penh",
            "Asia/Vientiane",
            "Asia/Singapore",
            "Asia/Seoul",
            "Asia/Tokyo",
            "Asia/Kolkata",
            "UTC");

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
        populate(model, blankForm(attendance.currentBusinessDate()));
        return "attendance/policy";
    }

    /**
     * Schedules a new future policy version attributed to the authenticated Admin.
     * Invalid submissions re-render the page with the bound form and field errors; service rejections
     * are shown as a retained-input global error instead of a 500.
     *
     * @param principal authenticated Admin
     * @param form bound schedule values
     * @param bindingResult Bean Validation outcome
     * @param model Thymeleaf model for error re-renders
     * @param redirectAttributes flash-message destination
     * @return policy management view or redirect
     */
    @PostMapping
    public String schedule(
            Principal principal,
            @Valid @ModelAttribute("form") PolicyScheduleForm form,
            BindingResult bindingResult,
            Model model,
            RedirectAttributes redirectAttributes) {
        AttendanceActor actor = requireAdmin(currentUsers.actor(principal));
        if (bindingResult.hasErrors()) {
            populate(model, form);
            return "attendance/policy";
        }
        try {
            policies.scheduleVersion(actor, form.toCommand());
            redirectAttributes.addFlashAttribute("message", "Attendance policy version scheduled");
            return "redirect:/attendance/policy";
        } catch (PolicyException | IllegalArgumentException exception) {
            model.addAttribute("error", exception.getMessage());
            populate(model, form);
            return "attendance/policy";
        }
    }

    /**
     * Replaces a scheduled-but-not-yet-effective version using the submitted optimistic version.
     * Invalid values are rejected with a flash message and the row remains unchanged.
     *
     * @param principal authenticated Admin
     * @param policyId policy version identifier
     * @param version optimistic version rendered to the editor
     * @param form bound replacement schedule values
     * @param bindingResult Bean Validation outcome
     * @param redirectAttributes flash-message destination
     * @return redirect to the policy management screen
     */
    @PostMapping("/{policyId}")
    public String replace(
            Principal principal,
            @PathVariable long policyId,
            @RequestParam long version,
            @Valid @ModelAttribute("form") PolicyScheduleForm form,
            BindingResult bindingResult,
            RedirectAttributes redirectAttributes) {
        AttendanceActor actor = requireAdmin(currentUsers.actor(principal));
        if (bindingResult.hasErrors()) {
            redirectAttributes.addFlashAttribute("error", joinErrors(bindingResult));
            return "redirect:/attendance/policy";
        }
        try {
            policies.replaceVersion(actor, policyId, version, form.toCommand());
            redirectAttributes.addFlashAttribute("message", "Attendance policy version replaced");
        } catch (PolicyException | IllegalArgumentException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/attendance/policy";
    }

    private void populate(Model model, PolicyScheduleForm form) {
        LocalDate today = attendance.currentBusinessDate();
        model.addAttribute("versions", policies.versions());
        model.addAttribute("today", today);
        model.addAttribute("days", DayOfWeek.values());
        model.addAttribute("timezones", TIMEZONES);
        model.addAttribute("form", form);
    }

    private static String joinErrors(BindingResult bindingResult) {
        return bindingResult.getAllErrors().stream()
                .map(error -> error.getDefaultMessage() != null ? error.getDefaultMessage() : "Invalid value")
                .distinct()
                .reduce((left, right) -> left + "; " + right)
                .orElse("Please correct the invalid fields");
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