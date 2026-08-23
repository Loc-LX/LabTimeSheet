package com.lab.labtimesheet.feature.attendance.controller;

import com.lab.labtimesheet.feature.attendance.exception.PolicyException;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRole;
import com.lab.labtimesheet.feature.attendance.model.dto.AttendancePolicyCommand;
import com.lab.labtimesheet.feature.attendance.service.AttendanceApplicationService;
import com.lab.labtimesheet.feature.attendance.service.AttendanceCurrentUserService;
import com.lab.labtimesheet.feature.attendance.service.AttendancePolicyApplicationService;
import java.security.Principal;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.ZoneId;
import java.math.BigDecimal;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Focused Admin-only Attendance Policy workflow.
 *
 * <p>The page accepts a native {@code yyyy-MM} value and delegates first-day
 * derivation and effective-date immutability to the Attendance policy service.
 * History is read from the same retained policy versions used by reports; no
 * second configuration or audit source is introduced.</p>
 */
@Controller
@RequestMapping("/admin/attendance-policies")
@RequiredArgsConstructor
public class AttendancePolicyController {

    private final AttendanceApplicationService attendance;
    private final AttendanceCurrentUserService currentUsers;
    private final AttendancePolicyApplicationService policies;

    /**
     * Renders the focused policy form and retained non-secret history.
     *
     * @param principal authenticated Admin principal
     * @param model Thymeleaf model
     * @return policy page view
     */
    @GetMapping
    public String page(Principal principal, Model model) {
        AttendanceActor actor = requireAdmin(currentUsers.actor(principal));
        var today = attendance.currentBusinessDate();
        model.addAttribute("today", today);
        model.addAttribute("minimumPolicyMonth", YearMonth.from(today).plusMonths(1));
        model.addAttribute("policyHistory", policies.history(actor));
        return "attendance/policies";
    }

    /**
     * Schedules one future policy from a month control.
     *
     * @param principal authenticated Admin principal
     * @param effectiveMonth native month value, not an arbitrary day
     * @param zoneId policy timezone
     * @param scheduledStart local schedule start
     * @param scheduledEnd local schedule end
     * @param checkInGraceMinutes inclusive check-in grace
     * @param checkoutGraceMinutes inclusive checkout grace
     * @param monthlyLeaveQuota monthly frozen leave quota
     * @param violationPenalty per-violation penalty
     * @param workdays selected ISO weekdays
     * @param redirectAttributes validation feedback destination
     * @return redirect to the focused policy page
     */
    @PostMapping
    public String schedule(
            Principal principal,
            @RequestParam(defaultValue = "") String effectiveMonth,
            @RequestParam(defaultValue = "") String zoneId,
            @RequestParam(defaultValue = "") String scheduledStart,
            @RequestParam(defaultValue = "") String scheduledEnd,
            @RequestParam(defaultValue = "") String checkInGraceMinutes,
            @RequestParam(defaultValue = "") String checkoutGraceMinutes,
            @RequestParam(defaultValue = "") String monthlyLeaveQuota,
            @RequestParam(defaultValue = "") String violationPenalty,
            @RequestParam(required = false) Set<String> workdays,
            RedirectAttributes redirectAttributes) {
        AttendanceActor actor = requireAdmin(currentUsers.actor(principal));
        Set<DayOfWeek> selectedWorkdays = Set.of();
        try {
            selectedWorkdays = requiredWorkdays(workdays);
            YearMonth month = requiredMonth(effectiveMonth);
            policies.schedule(actor, new AttendancePolicyCommand(
                    month.atDay(1),
                    ZoneId.of(zoneId.strip()),
                    requiredTime(scheduledStart),
                    requiredTime(scheduledEnd),
                    requiredInt(checkInGraceMinutes),
                    requiredInt(checkoutGraceMinutes),
                    requiredInt(monthlyLeaveQuota),
                    requiredDecimal(violationPenalty),
                    selectedWorkdays));
            redirectAttributes.addFlashAttribute("message", "Attendance policy scheduled");
        } catch (PolicyException | IllegalArgumentException | DateTimeParseException failure) {
            redirectAttributes.addFlashAttribute("settingsError", "Enter valid attendance policy values.");
            redirectAttributes.addFlashAttribute("policyInput", Map.of(
                    "effectiveMonth", effectiveMonth,
                    "zoneId", zoneId,
                    "scheduledStart", scheduledStart,
                    "scheduledEnd", scheduledEnd,
                    "checkInGraceMinutes", checkInGraceMinutes,
                    "checkoutGraceMinutes", checkoutGraceMinutes,
                    "monthlyLeaveQuota", monthlyLeaveQuota,
                    "violationPenalty", violationPenalty,
                    "workdays", selectedWorkdays));
        }
        return "redirect:/admin/attendance-policies";
    }

    private static YearMonth requiredMonth(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Future policy month is required");
        }
        try {
            return YearMonth.parse(value.strip());
        } catch (DateTimeParseException failure) {
            throw new IllegalArgumentException("Future policy month must use yyyy-MM", failure);
        }
    }

    private static LocalTime requiredTime(String value) {
        try {
            return LocalTime.parse(value.strip());
        } catch (DateTimeParseException failure) {
            throw new IllegalArgumentException("Enter valid attendance policy values.", failure);
        }
    }

    private static int requiredInt(String value) {
        try {
            return Integer.parseInt(value.strip());
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("Enter valid attendance policy values.", failure);
        }
    }

    private static BigDecimal requiredDecimal(String value) {
        try {
            return new BigDecimal(value.strip());
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("Enter valid attendance policy values.", failure);
        }
    }

    private static Set<DayOfWeek> requiredWorkdays(Set<String> values) {
        if (values == null) {
            return Set.of();
        }
        EnumSet<DayOfWeek> workdays = EnumSet.noneOf(DayOfWeek.class);
        try {
            values.forEach(value -> workdays.add(DayOfWeek.valueOf(value.strip())));
            return Set.copyOf(workdays);
        } catch (IllegalArgumentException failure) {
            throw new IllegalArgumentException("Enter valid attendance policy values.", failure);
        }
    }

    private static AttendanceActor requireAdmin(AttendanceActor actor) {
        if (actor == null || actor.role() != AttendanceRole.ADMIN) {
            throw new AccessDeniedException("Only Admin may manage attendance policy");
        }
        return actor;
    }
}
