package com.lab.labtimesheet.feature.identity.controller;

import java.security.Principal;
import java.util.List;

import com.lab.labtimesheet.feature.identity.service.AccountService;
import com.lab.labtimesheet.platform.model.dto.SmtpActionForm;
import com.lab.labtimesheet.platform.model.dto.SmtpForm;
import com.lab.labtimesheet.platform.service.SmtpConfigurationService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

/**
 * Runs the Admin SMTP draft, connection-test, activation, and ordered setup-deferral browser workflows.
 * Cleartext passwords remain request-local and are cleared before any error view is rendered. Failures crossing the
 * SMTP adapter boundary are represented by fixed operator guidance rather than raw provider diagnostics.
 *
 * <p>The handler verifies the signed-in Admin through {@code identity} and resolves the test recipient from that
 * verified identity before any shared SMTP service is called ({@code R4}).
 */
@Controller
@RequestMapping("/admin/smtp")
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class SmtpController {
    private static final String TEST_FAILURE_MESSAGE =
            "SMTP test failed. Verify the draft settings and server availability, then try again.";
    private static final String ACTIVATION_FAILURE_MESSAGE =
            "SMTP activation failed. Test the current draft again before activating it.";
    private static final String DEFERRAL_STEP = SmtpController.class.getName() + ".deferralStep";
    private static final List<String> DEFERRAL_WARNINGS = List.of(
            "Account onboarding is disabled until SMTP is active.",
            "Activation resend is disabled until SMTP is active.",
            "Password recovery is disabled until SMTP is active.",
            "Workflow email delivery is less immediate until SMTP is active.",
            "I acknowledge this installation remains restricted until SMTP is active.");

    private final SmtpConfigurationService smtp;
    private final AccountService accounts;

    @GetMapping
    String form(Principal principal, Model model) {
        return renderForm(model, null, adminId(principal));
    }

    @PostMapping("/draft")
    String saveDraft(@Valid @ModelAttribute("smtpForm") SmtpForm form, BindingResult bindingResult,
            Principal principal, Model model) {
        if (bindingResult.hasErrors()) {
            form.clearPassword();
            return renderForm(model, form, adminId(principal));
        }
        try {
            smtp.saveDraft(adminId(principal), form.toDraft());
            return "redirect:/admin/smtp?saved";
        } catch (IllegalArgumentException | IllegalStateException validation) {
            bindingResult.reject("smtp.invalid", validation.getMessage());
            form.clearPassword();
            return renderForm(model, form, adminId(principal));
        }
    }

    @PostMapping("/test")
    String test(@Valid @ModelAttribute("smtpAction") SmtpActionForm action, BindingResult bindingResult,
            Principal principal, Model model) {
        if (bindingResult.hasErrors()) {
            return renderActionError(model, bindingResult, principal);
        }
        try {
            long verifiedAdminId = adminId(principal);
            smtp.testDraft(action.getDraftId(), verifiedAdminId,
                    accounts.requireIdentityById(verifiedAdminId).email());
            return "redirect:/admin/smtp?tested";
        } catch (IllegalArgumentException | IllegalStateException | MailException failure) {
            bindingResult.reject("smtp.test.failed", TEST_FAILURE_MESSAGE);
            return renderActionError(model, bindingResult, principal);
        }
    }

    @PostMapping("/activate")
    String activate(@Valid @ModelAttribute("smtpAction") SmtpActionForm action, BindingResult bindingResult,
            Principal principal, Model model) {
        if (bindingResult.hasErrors()) {
            return renderActionError(model, bindingResult, principal);
        }
        try {
            smtp.activate(action.getDraftId(), adminId(principal));
            return "redirect:/admin/smtp?activated";
        } catch (IllegalArgumentException | IllegalStateException failure) {
            bindingResult.reject("smtp.activate.failed", ACTIVATION_FAILURE_MESSAGE);
            return renderActionError(model, bindingResult, principal);
        }
    }

    @GetMapping("/defer")
    String deferral(HttpSession session, Model model) {
        if (smtp.hasActiveConfiguration()) {
            return "redirect:/admin/smtp";
        }
        int step = deferralStep(session);
        model.addAttribute("deferralStep", step);
        model.addAttribute("deferralWarning", DEFERRAL_WARNINGS.get(step - 1));
        return "smtp/defer";
    }

    @PostMapping("/defer/next")
    String nextDeferral(HttpSession session) {
        session.setAttribute(DEFERRAL_STEP, Math.min(5, deferralStep(session) + 1));
        return "redirect:/admin/smtp/defer";
    }

    @PostMapping("/defer/back")
    String previousDeferral(HttpSession session) {
        session.setAttribute(DEFERRAL_STEP, Math.max(1, deferralStep(session) - 1));
        return "redirect:/admin/smtp/defer";
    }

    @PostMapping("/defer/finish")
    String finishDeferral(HttpSession session) {
        if (deferralStep(session) != 5) {
            return "redirect:/admin/smtp/defer";
        }
        session.removeAttribute(DEFERRAL_STEP);
        return "redirect:/dashboard";
    }

    private String renderForm(Model model, SmtpForm submittedForm, long adminId) {
        var status = smtp.setupStatus(adminId);
        model.addAttribute("smtpStatus", status);
        model.addAttribute("smtpAction", new SmtpActionForm());
        if (submittedForm == null) {
            model.addAttribute("smtpForm", SmtpForm.from(status));
        }
        return "smtp/form";
    }

    private String renderActionError(Model model, BindingResult bindingResult, Principal principal) {
        model.addAttribute("smtpActionError", bindingResult.getAllErrors().getFirst().getDefaultMessage());
        return renderForm(model, null, adminId(principal));
    }

    private static int deferralStep(HttpSession session) {
        Object value = session.getAttribute(DEFERRAL_STEP);
        return value instanceof Integer step && step >= 1 && step <= 5 ? step : 1;
    }

    private long adminId(Principal principal) {
        return accounts.requireActiveAdminId(principal.getName());
    }
}
