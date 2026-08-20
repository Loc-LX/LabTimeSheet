package com.lab.labtimesheet.feature.attendance.controller;

import com.lab.labtimesheet.feature.attendance.exception.HolidayCalendarException;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRole;
import com.lab.labtimesheet.feature.attendance.model.dto.HolidayImportForm;
import com.lab.labtimesheet.feature.attendance.model.dto.HolidayImportSummary;
import com.lab.labtimesheet.feature.attendance.model.dto.HolidayPreviewRow;
import com.lab.labtimesheet.feature.attendance.model.dto.HolidaySelection;
import com.lab.labtimesheet.feature.attendance.model.dto.HolidaySelectionForm;
import com.lab.labtimesheet.feature.attendance.service.AttendanceApplicationService;
import com.lab.labtimesheet.feature.attendance.service.AttendanceCurrentUserService;
import com.lab.labtimesheet.feature.attendance.service.CalendarApplicationService;
import com.lab.labtimesheet.feature.attendance.service.HolidayImportService;
import java.security.Principal;
import java.time.LocalDate;
import java.util.List;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
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
 * Admin-only server-rendered routes for global calendar and Vietnamese holiday management.
 */
@Controller
@RequestMapping("/attendance/calendar")
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class CalendarController {

    private final CalendarApplicationService calendar;
    private final HolidayImportService holidays;
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
        requireAdmin(currentUsers.actor(principal));
        LocalDate today = attendance.currentBusinessDate();
        model.addAttribute("events", calendar.list(today, today.plusYears(1)));
        model.addAttribute("today", today);
        return "attendance/calendar";
    }

    /**
     * Creates a custom future event using the authenticated Admin identity.
     *
     * @param principal authenticated Admin
     * @param date local event date
     * @param name non-blank display name
     * @param dayOff authoritative day-off choice
     * @param redirectAttributes flash-message destination
     * @return redirect to calendar management
     */
    @PostMapping
    public String create(
            Principal principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam String name,
            @RequestParam(defaultValue = "false") boolean dayOff,
            RedirectAttributes redirectAttributes) {
        AttendanceActor actor = requireAdmin(currentUsers.actor(principal));
        calendar.createManual(actor, date, name, dayOff);
        redirectAttributes.addFlashAttribute("message", "Calendar event created");
        return "redirect:/attendance/calendar";
    }

    /**
     * Updates a future event using the submitted optimistic version and authenticated Admin identity.
     *
     * @param principal authenticated Admin
     * @param eventId event identifier
     * @param version expected optimistic version
     * @param date replacement local date
     * @param name replacement display name
     * @param dayOff replacement day-off choice
     * @param redirectAttributes flash-message destination
     * @return redirect to calendar management
     */
    @PostMapping("/{eventId}")
    public String update(
            Principal principal,
            @PathVariable long eventId,
            @RequestParam long version,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam String name,
            @RequestParam(defaultValue = "false") boolean dayOff,
            RedirectAttributes redirectAttributes) {
        AttendanceActor actor = requireAdmin(currentUsers.actor(principal));
        calendar.updateManual(actor, eventId, version, date, name, dayOff);
        redirectAttributes.addFlashAttribute("message", "Calendar event updated");
        return "redirect:/attendance/calendar";
    }

    /**
     * Renders the Vietnamese HolidayAPI preview with public rows preselected and
     * previously imported source UUIDs disclosed. An unavailable API reports an
     * actionable message while the manual custom-event fallback stays available.
     *
     * @param principal authenticated Admin
     * @param year requested preview year, defaulting to the current business year
     * @param model Thymeleaf model
     * @return holiday-import view name
     */
    @GetMapping("/holidays")
    public String holidays(
            Principal principal, @RequestParam(required = false) Integer year, Model model) {
        AttendanceActor actor = requireAdmin(currentUsers.actor(principal));
        int previewYear = year == null ? attendance.currentBusinessDate().getYear() : year;
        List<HolidayPreviewRow> preview;
        try {
            preview = holidays.preview(actor, previewYear);
        } catch (HolidayCalendarException exception) {
            model.addAttribute("error", exception.getMessage());
            preview = List.of();
        }
        model.addAttribute("year", previewYear);
        model.addAttribute("preview", preview);
        model.addAttribute("form", holidayForm(previewYear, preview));
        return "attendance/holiday-import";
    }

    /**
     * Imports the Admin's explicitly selected holiday rows with full provenance.
     *
     * @param principal authenticated Admin
     * @param form bound selections and preview year
     * @param redirectAttributes flash-message destination
     * @return redirect back to the holiday preview
     */
    @PostMapping("/holidays")
    public String importHolidays(
            Principal principal, @ModelAttribute HolidayImportForm form, RedirectAttributes redirectAttributes) {
        AttendanceActor actor = requireAdmin(currentUsers.actor(principal));
        List<HolidaySelection> selections = form.getSelections().stream()
                .filter(HolidaySelectionForm::isSelected)
                .map(selection -> new HolidaySelection(selection.getUuid(), selection.isDayOff()))
                .toList();
        try {
            HolidayImportSummary summary = holidays.importSelections(actor, form.getYear(), selections);
            redirectAttributes.addFlashAttribute(
                    "message",
                    "Imported " + summary.imported() + " holiday(s); "
                            + summary.skipped() + " already present");
        } catch (HolidayCalendarException exception) {
            redirectAttributes.addFlashAttribute("error", exception.getMessage());
        }
        return "redirect:/attendance/calendar/holidays?year=" + form.getYear();
    }

    private static HolidayImportForm holidayForm(int year, List<HolidayPreviewRow> preview) {
        HolidayImportForm form = new HolidayImportForm();
        form.setYear(year);
        form.setSelections(preview.stream()
                .map(row -> {
                    HolidaySelectionForm selection = new HolidaySelectionForm();
                    selection.setUuid(row.candidate().uuid());
                    selection.setSelected(row.preselected() && !row.alreadyImported());
                    selection.setDayOff(row.preselected());
                    return selection;
                })
                .toList());
        return form;
    }

    private static AttendanceActor requireAdmin(AttendanceActor actor) {
        if (actor.role() != AttendanceRole.ADMIN) {
            throw new AccessDeniedException("Only Admin may manage the global calendar");
        }
        return actor;
    }
}
