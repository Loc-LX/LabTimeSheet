package com.lab.labtimesheet.feature.attendance.controller;

import com.lab.labtimesheet.feature.attendance.exception.CalendarException;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRole;
import com.lab.labtimesheet.feature.attendance.service.AttendanceApplicationService;
import com.lab.labtimesheet.feature.attendance.service.AttendanceCurrentUserService;
import com.lab.labtimesheet.feature.attendance.service.CalendarApplicationService;
import java.security.Principal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Map;
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
 * Admin-only server-rendered routes for manual global calendar management.
 */
@Controller
@RequestMapping("/attendance/calendar")
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class CalendarController {

    private final CalendarApplicationService calendar;
    private final AttendanceApplicationService attendance;
    private final AttendanceCurrentUserService currentUsers;

    /**
     * Renders the next year of locally stored calendar events for an authenticated Admin.
     *
     * @param principal authenticated Admin
     * @param model Thymeleaf model
     * @return calendar management view name
     */
    @GetMapping
    public String calendar(Principal principal, Model model) {
        AttendanceActor actor = requireAdmin(currentUsers.actor(principal));
        LocalDate today = attendance.currentBusinessDate();
        model.addAttribute("events", calendar.list(today, today.plusYears(1)));
        model.addAttribute("history", calendar.history(actor));
        model.addAttribute("today", today);
        return "attendance/calendar";
    }

    /**
     * Creates a custom future event using the authenticated Admin identity.
     *
     * @param principal authenticated Admin
     * @param date raw local event date
     * @param name non-blank display name
     * @param dayOff raw authoritative day-off choice
     * @param redirectAttributes flash-message destination
     * @return redirect to calendar management
     */
    @PostMapping
    public String create(
            Principal principal,
            @RequestParam(defaultValue = "") String date,
            @RequestParam(defaultValue = "") String name,
            @RequestParam(defaultValue = "false") String dayOff,
            RedirectAttributes redirectAttributes) {
        AttendanceActor actor = requireAdmin(currentUsers.actor(principal));
        try {
            calendar.createManual(
                    actor,
                    requiredDate(date),
                    name,
                    requiredBoolean(dayOff));
            redirectAttributes.addFlashAttribute("message", "Calendar event created");
        } catch (CalendarException | IllegalArgumentException failure) {
            redirectAttributes.addFlashAttribute("calendarError", failure.getMessage());
            redirectAttributes.addFlashAttribute("calendarInput", Map.of(
                    "date", date,
                    "name", name,
                    "dayOff", dayOff));
        }
        return "redirect:/attendance/calendar";
    }

    /**
     * Updates a future event using the submitted optimistic version and authenticated Admin identity.
     *
     * @param principal authenticated Admin
     * @param eventId event identifier
     * @param version raw expected optimistic version
     * @param date raw replacement local date
     * @param name replacement display name
     * @param dayOff raw replacement day-off choice
     * @param redirectAttributes flash-message destination
     * @return redirect to calendar management
     */
    @PostMapping("/{eventId}")
    public String update(
            Principal principal,
            @PathVariable long eventId,
            @RequestParam(defaultValue = "") String version,
            @RequestParam(defaultValue = "") String date,
            @RequestParam(defaultValue = "") String name,
            @RequestParam(defaultValue = "false") String dayOff,
            RedirectAttributes redirectAttributes) {
        AttendanceActor actor = requireAdmin(currentUsers.actor(principal));
        try {
            calendar.updateManual(
                    actor,
                    eventId,
                    requiredLong(version),
                    requiredDate(date),
                    name,
                    requiredBoolean(dayOff));
            redirectAttributes.addFlashAttribute("message", "Calendar event updated");
        } catch (CalendarException | IllegalArgumentException failure) {
            redirectAttributes.addFlashAttribute("calendarError", failure.getMessage());
            redirectAttributes.addFlashAttribute("calendarEditInput", Map.of(
                    "eventId", eventId,
                    "date", date,
                    "name", name,
                    "dayOff", dayOff));
        }
        return "redirect:/attendance/calendar";
    }

    private static LocalDate requiredDate(String value) {
        try {
            return LocalDate.parse(value.strip());
        } catch (DateTimeParseException failure) {
            throw new IllegalArgumentException("Enter valid calendar event values.", failure);
        }
    }

    private static long requiredLong(String value) {
        try {
            return Long.parseLong(value.strip());
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("Enter valid calendar event values.", failure);
        }
    }

    private static boolean requiredBoolean(String value) {
        return switch (value.strip()) {
            case "true" -> true;
            case "false" -> false;
            default -> throw new IllegalArgumentException("Enter valid calendar event values.");
        };
    }

    private static AttendanceActor requireAdmin(AttendanceActor actor) {
        if (actor == null || actor.role() != AttendanceRole.ADMIN) {
            throw new AccessDeniedException("Only Admin may manage the global calendar");
        }
        return actor;
    }
}
