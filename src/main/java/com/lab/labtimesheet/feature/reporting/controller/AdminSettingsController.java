package com.lab.labtimesheet.feature.reporting.controller;

import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.attendance.exception.CalendarException;
import com.lab.labtimesheet.feature.attendance.exception.PolicyException;
import com.lab.labtimesheet.feature.attendance.model.AttendanceActor;
import com.lab.labtimesheet.feature.attendance.model.AttendanceRole;
import com.lab.labtimesheet.feature.attendance.model.dto.AttendancePolicyCommand;
import com.lab.labtimesheet.feature.attendance.model.dto.CalendarImportSelection;
import com.lab.labtimesheet.feature.attendance.service.AttendanceApplicationService;
import com.lab.labtimesheet.feature.attendance.service.AttendanceCurrentUserService;
import com.lab.labtimesheet.feature.attendance.service.AttendancePolicyApplicationService;
import com.lab.labtimesheet.feature.attendance.service.CalendarApplicationService;
import com.lab.labtimesheet.feature.integration.model.dto.HolidayApiDraft;
import com.lab.labtimesheet.feature.integration.service.HolidayApiConfigurationService;
import com.lab.labtimesheet.feature.integration.service.SmtpConfigurationService;
import java.math.BigDecimal;
import java.security.Principal;
import java.time.DayOfWeek;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.ui.Model;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Renders the Admin-owned attendance/integration settings and their non-secret retained history.
 * Provider I/O occurs only after an explicit POST; ordinary page loads use local persistence and
 * redacted public DTOs.
 */
@Controller
@RequestMapping("/admin/settings")
@RequiredArgsConstructor
public class AdminSettingsController {

    private static final String DECISION_PREFIX = "decision_";

    private final AccountService accounts;
    private final AttendanceCurrentUserService currentUsers;
    private final AttendanceApplicationService attendance;
    private final AttendancePolicyApplicationService policies;
    private final CalendarApplicationService calendar;
    private final HolidayApiConfigurationService holidayApi;
    private final SmtpConfigurationService smtp;

    /** Renders local setup state and all four non-secret Admin History sections. */
    @GetMapping
    public String settings(Principal principal, Model model) {
        render(principal, model);
        return "admin/settings";
    }

    /** Schedules one future attendance-policy version through the policy service. */
    @PostMapping("/policy")
    public String schedulePolicy(
            Principal principal,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate effectiveFrom,
            @RequestParam String zoneId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime scheduledStart,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.TIME) LocalTime scheduledEnd,
            @RequestParam int checkInGraceMinutes,
            @RequestParam int checkoutGraceMinutes,
            @RequestParam int monthlyLeaveQuota,
            @RequestParam BigDecimal violationPenalty,
            @RequestParam(required = false) Set<DayOfWeek> workdays,
            RedirectAttributes redirectAttributes) {
        Set<DayOfWeek> selectedWorkdays = workdays == null ? Set.of() : Set.copyOf(workdays);
        try {
            policies.schedule(actor(principal), new AttendancePolicyCommand(
                    effectiveFrom,
                    ZoneId.of(zoneId),
                    scheduledStart,
                    scheduledEnd,
                    checkInGraceMinutes,
                    checkoutGraceMinutes,
                    monthlyLeaveQuota,
                    violationPenalty,
                    selectedWorkdays));
            redirectAttributes.addFlashAttribute("message", "Attendance policy scheduled");
        } catch (PolicyException | IllegalArgumentException | DateTimeException failure) {
            redirectAttributes.addFlashAttribute("settingsError", failure.getMessage());
            redirectAttributes.addFlashAttribute("policyInput", Map.of(
                    "effectiveFrom", effectiveFrom.toString(),
                    "zoneId", zoneId,
                    "scheduledStart", scheduledStart.toString(),
                    "scheduledEnd", scheduledEnd.toString(),
                    "checkInGraceMinutes", checkInGraceMinutes,
                    "checkoutGraceMinutes", checkoutGraceMinutes,
                    "monthlyLeaveQuota", monthlyLeaveQuota,
                    "violationPenalty", violationPenalty.toPlainString(),
                    "workdays", selectedWorkdays));
        }
        return "redirect:/admin/settings#policy-history";
    }

    /** Saves a request-local HolidayAPI key as an encrypted VN draft. */
    @PostMapping("/holiday-api/draft")
    public String saveHolidayDraft(
            Principal principal, @RequestParam String apiKey, RedirectAttributes redirectAttributes) {
        try {
            holidayApi.saveDraft(adminId(principal), new HolidayApiDraft(apiKey));
            redirectAttributes.addFlashAttribute("message", "HolidayAPI draft saved");
        } catch (IllegalArgumentException | IllegalStateException failure) {
            redirectAttributes.addFlashAttribute("settingsError", failure.getMessage());
        }
        return "redirect:/admin/settings#holiday-api-history";
    }

    /** Tests the current encrypted HolidayAPI draft and renders only its safe outcome. */
    @PostMapping("/holiday-api/test")
    public String testHolidayDraft(
            Principal principal,
            @RequestParam long draftId,
            @RequestParam int year,
            Model model,
            RedirectAttributes redirectAttributes) {
        try {
            var result = holidayApi.testDraft(draftId, adminId(principal), year);
            render(principal, model);
            model.addAttribute("holidayTestResult", result);
            return "admin/settings";
        } catch (IllegalArgumentException | IllegalStateException failure) {
            redirectAttributes.addFlashAttribute("settingsError", failure.getMessage());
            return "redirect:/admin/settings#holiday-api-history";
        }
    }

    /** Activates one tested HolidayAPI draft and retires the previous revision. */
    @PostMapping("/holiday-api/activate")
    public String activateHolidayDraft(
            Principal principal, @RequestParam long draftId, RedirectAttributes redirectAttributes) {
        try {
            holidayApi.activate(draftId, adminId(principal));
            redirectAttributes.addFlashAttribute("message", "HolidayAPI configuration activated");
        } catch (IllegalArgumentException | IllegalStateException failure) {
            redirectAttributes.addFlashAttribute("settingsError", failure.getMessage());
        }
        return "redirect:/admin/settings#holiday-api-history";
    }

    /** Performs the explicit provider call and renders locally selectable candidate decisions. */
    @PostMapping("/calendar/preview")
    public String previewCalendar(Principal principal, @RequestParam int year, Model model) {
        AttendanceActor actor = actor(principal);
        var providerPreview = calendar.previewFromProvider(actor, year);
        render(principal, model);
        model.addAttribute("providerPreview", providerPreview);
        model.addAttribute("calendarPreview", calendar.preview(actor, year, providerPreview));
        model.addAttribute("previewYear", year);
        return "admin/settings";
    }

    /** Imports only explicitly selected provider identities with each local day-off decision. */
    @PostMapping("/calendar/import")
    public String importCalendar(
            Principal principal,
            @RequestParam int year,
            @RequestParam MultiValueMap<String, String> parameters,
            RedirectAttributes redirectAttributes) {
        try {
            List<CalendarImportSelection> selections = parameters.entrySet().stream()
                    .filter(entry -> entry.getKey().startsWith(DECISION_PREFIX))
                    .filter(entry -> {
                        if (entry.getValue().size() != 1) {
                            throw new IllegalArgumentException("Exactly one calendar decision is required");
                        }
                        return !"skip".equals(entry.getValue().getFirst());
                    })
                    .map(entry -> new CalendarImportSelection(
                            entry.getKey().substring(DECISION_PREFIX.length()),
                            switch (entry.getValue().getFirst()) {
                                case "true" -> true;
                                case "false" -> false;
                                default -> throw new IllegalArgumentException("Invalid calendar decision");
                            }))
                    .toList();
            calendar.importSelected(actor(principal), year, selections);
            redirectAttributes.addFlashAttribute("message", "Calendar selections imported");
        } catch (CalendarException | IllegalArgumentException | IllegalStateException failure) {
            redirectAttributes.addFlashAttribute("settingsError", failure.getMessage());
        }
        return "redirect:/admin/settings#calendar-history";
    }

    private void render(Principal principal, Model model) {
        AttendanceActor actor = actor(principal);
        long adminId = adminId(principal);
        LocalDate today = attendance.currentBusinessDate();
        model.addAttribute("today", today);
        model.addAttribute("defaultYear", today.getYear());
        model.addAttribute("policyHistory", policies.history(actor));
        model.addAttribute("calendarHistory", calendar.history(actor));
        model.addAttribute("smtpHistory", smtp.history(adminId));
        model.addAttribute("holidayApiHistory", holidayApi.history(adminId));
        model.addAttribute("holidayApiStatus", holidayApi.setupStatus(adminId));
    }

    private long adminId(Principal principal) {
        return accounts.requireActiveAdminId(principal.getName());
    }

    private AttendanceActor actor(Principal principal) {
        AttendanceActor actor = currentUsers.actor(principal);
        if (actor == null || actor.role() != AttendanceRole.ADMIN) {
            throw new AccessDeniedException("Only Admin may manage attendance settings");
        }
        return actor;
    }
}
