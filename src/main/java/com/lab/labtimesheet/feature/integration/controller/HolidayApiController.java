package com.lab.labtimesheet.feature.integration.controller;

import java.security.Principal;

import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.integration.model.HolidayApiFailureKind;
import com.lab.labtimesheet.feature.integration.model.dto.HolidayApiActionForm;
import com.lab.labtimesheet.feature.integration.model.dto.HolidayApiForm;
import com.lab.labtimesheet.feature.integration.model.dto.HolidayApiPreviewForm;
import com.lab.labtimesheet.feature.integration.service.HolidayApiConfigurationService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Provides explicit Admin-only HolidayAPI setup, test, activation, and preview actions.
 * Cleartext keys are request-local and are cleared before any validation or provider failure is rendered.
 */
@Controller
@RequestMapping("/admin/holiday-api")
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class HolidayApiController {
    private static final String SAVE_FAILURE_MESSAGE =
            "HolidayAPI draft could not be saved. Check the key and try again.";
    private static final String TEST_FAILURE_MESSAGE =
            "HolidayAPI test could not be completed. Review the safe result below and try again.";
    private static final String ACTIVATION_FAILURE_MESSAGE =
            "HolidayAPI activation failed. Test the current draft successfully before activating it.";
    private static final String PREVIEW_FAILURE_MESSAGE =
            "HolidayAPI preview could not be completed. Review the safe result below and try again.";

    private final HolidayApiConfigurationService holidayApi;
    private final AccountService accounts;

    @GetMapping
    String form(Model model) {
        return renderForm(model, null, null);
    }

    @PostMapping("/draft")
    String saveDraft(@Valid @ModelAttribute("holidayApiForm") HolidayApiForm form,
            BindingResult bindingResult, Principal principal, Model model) {
        if (!bindingResult.hasErrors()) {
            try {
                holidayApi.saveDraft(adminId(principal), form.toDraft());
                return "redirect:/admin/holiday-api?saved";
            } catch (IllegalArgumentException | IllegalStateException failure) {
                bindingResult.reject("holidayApi.invalid", SAVE_FAILURE_MESSAGE);
            }
        }
        form.clearApiKey();
        return renderForm(model, form, null);
    }

    @PostMapping("/test")
    String test(@Valid @ModelAttribute("holidayApiAction") HolidayApiActionForm action,
            BindingResult bindingResult, @RequestParam(defaultValue = "2026") int year,
            Principal principal, Model model) {
        if (bindingResult.hasErrors()) {
            return renderActionError(model, "The HolidayAPI draft identifier is invalid.");
        }
        try {
            var result = holidayApi.testDraft(action.getDraftId(), adminId(principal), year);
            if (result.successful()) {
                return "redirect:/admin/holiday-api?tested";
            }
            model.addAttribute("holidayApiTestError", failureMessage(result.failure()));
            return renderForm(model, null, null);
        } catch (IllegalArgumentException | IllegalStateException failure) {
            return renderActionError(model, TEST_FAILURE_MESSAGE);
        }
    }

    @PostMapping("/activate")
    String activate(@Valid @ModelAttribute("holidayApiAction") HolidayApiActionForm action,
            BindingResult bindingResult, Principal principal, Model model) {
        if (bindingResult.hasErrors()) {
            return renderActionError(model, "The HolidayAPI draft identifier is invalid.");
        }
        try {
            holidayApi.activate(action.getDraftId(), adminId(principal));
            return "redirect:/admin/holiday-api?activated";
        } catch (IllegalArgumentException | IllegalStateException failure) {
            return renderActionError(model, ACTIVATION_FAILURE_MESSAGE);
        }
    }

    @PostMapping("/preview")
    String preview(@Valid @ModelAttribute("holidayApiPreviewForm") HolidayApiPreviewForm form,
            BindingResult bindingResult, Model model) {
        if (bindingResult.hasErrors()) {
            return renderForm(model, null, form);
        }
        try {
            var result = holidayApi.preview(form.getYear());
            if (result.successful()) {
                model.addAttribute("holidayApiPreview", result.preview());
            } else {
                model.addAttribute("holidayApiPreviewError", failureMessage(result.failure()));
            }
            return renderForm(model, null, form);
        } catch (IllegalArgumentException | IllegalStateException failure) {
            model.addAttribute("holidayApiPreviewError", PREVIEW_FAILURE_MESSAGE);
            return renderForm(model, null, form);
        }
    }

    private String renderForm(Model model, HolidayApiForm submittedForm, HolidayApiPreviewForm submittedPreview) {
        model.addAttribute("holidayApiStatus", holidayApi.setupStatus());
        if (!model.containsAttribute("holidayApiAction")) {
            model.addAttribute("holidayApiAction", new HolidayApiActionForm());
        }
        if (submittedForm == null && !model.containsAttribute("holidayApiForm")) {
            model.addAttribute("holidayApiForm", new HolidayApiForm());
        }
        if (submittedPreview == null && !model.containsAttribute("holidayApiPreviewForm")) {
            model.addAttribute("holidayApiPreviewForm", new HolidayApiPreviewForm());
        }
        return "holiday-api/form";
    }

    private String renderActionError(Model model, String message) {
        model.addAttribute("holidayApiActionError", message);
        return renderForm(model, null, null);
    }

    private long adminId(Principal principal) {
        return accounts.requireActiveAdminId(principal.getName());
    }

    private static String failureMessage(HolidayApiFailureKind failure) {
        if (failure == null) {
            return TEST_FAILURE_MESSAGE;
        }
        return switch (failure) {
            case NOT_CONFIGURED -> "HolidayAPI is not configured yet.";
            case INVALID_KEY -> "HolidayAPI rejected the configured key. Save a corrected draft and test again.";
            case RATE_LIMITED -> "HolidayAPI rate limit reached. Try again later.";
            case UNAVAILABLE -> "HolidayAPI is unavailable right now. Try again later.";
        };
    }
}
