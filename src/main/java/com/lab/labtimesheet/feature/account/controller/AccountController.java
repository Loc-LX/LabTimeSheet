package com.lab.labtimesheet.feature.account.controller;

import java.security.Principal;

import com.lab.labtimesheet.feature.account.model.dto.ActivationForm;
import com.lab.labtimesheet.feature.account.model.dto.CreateAccountForm;
import com.lab.labtimesheet.feature.account.service.AccountService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Handles Admin account creation and single-use account activation browser flows. Known database uniqueness
 * constraints are mapped to their owning form fields without exposing persistence diagnostics.
 */
@Controller
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class AccountController {
    private final AccountService accounts;

    @GetMapping("/admin/accounts/new")
    String newAccount(Model model) {
        if (!model.containsAttribute("accountForm")) {
            model.addAttribute("accountForm", new CreateAccountForm());
        }
        return "accounts/new";
    }

    @PostMapping("/admin/accounts")
    String create(@Valid @ModelAttribute("accountForm") CreateAccountForm form, BindingResult bindingResult,
            Principal principal) {
        if (bindingResult.hasErrors()) {
            return "accounts/new";
        }
        try {
            var result = accounts.create(form.toCommand(), accounts.requireActiveAdminId(principal.getName()));
            return result.deliverySucceeded()
                    ? "redirect:/admin/accounts/new?created"
                    : "redirect:/admin/accounts/new?deliveryFailed&accountId=" + result.userId();
        } catch (DataIntegrityViolationException duplicate) {
            rejectUniquenessViolation(bindingResult, duplicate);
            return "accounts/new";
        } catch (IllegalArgumentException | IllegalStateException exception) {
            bindingResult.reject("account.invalid", exception.getMessage());
            return "accounts/new";
        }
    }

    /**
     * Reissues one activation link for the pending account named by the failed-delivery page.
     *
     * @param targetUserId pending account identifier from the server-rendered form
     * @param principal authenticated Admin principal
     * @return a generic success or failure status for the account page
     */
    @PostMapping("/admin/accounts/{targetUserId}/resend-activation")
    String resendActivation(@PathVariable long targetUserId, Principal principal) {
        try {
            var result = accounts.resendActivation(targetUserId, accounts.requireActiveAdminId(principal.getName()));
            return result.deliverySucceeded()
                    ? "redirect:/admin/accounts/new?activationResent"
                    : "redirect:/admin/accounts/new?deliveryFailed&accountId=" + targetUserId;
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return "redirect:/admin/accounts/new?deliveryFailed&accountId=" + targetUserId;
        }
    }

    @GetMapping("/activate")
    String activationForm(@ModelAttribute("activationForm") ActivationForm form, Model model) {
        if (form.getToken() == null || form.getToken().isBlank()) {
            model.addAttribute("error", "This activation link is invalid or no longer usable");
        }
        return "accounts/activate";
    }

    @PostMapping("/activate")
    String activate(@Valid @ModelAttribute("activationForm") ActivationForm form, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            form.clearPasswords();
            return "accounts/activate";
        }
        try {
            if (accounts.activate(form.getToken(), form.getPassword())) {
                return "redirect:/login?activated";
            }
            bindingResult.reject("activation.invalid", "This activation link is invalid or no longer usable");
        } catch (IllegalArgumentException exception) {
            bindingResult.reject("activation.invalid", exception.getMessage());
        }
        form.clearPasswords();
        return "accounts/activate";
    }

    private static void rejectUniquenessViolation(BindingResult bindingResult,
            DataIntegrityViolationException violation) {
        String constraintName = constraintName(violation);
        if ("uq_app_users_email_ci".equals(constraintName)) {
            bindingResult.rejectValue(
                    "email", "account.email.duplicate", "An account with this email already exists");
        } else if ("uq_intern_profiles_student_code_ci".equals(constraintName)) {
            bindingResult.rejectValue("studentCode", "account.studentCode.duplicate",
                    "An Intern with this student code already exists");
        } else {
            bindingResult.reject("account.unique", "Account details conflict with an existing account");
        }
    }

    private static String constraintName(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof ConstraintViolationException violation) {
                return violation.getConstraintName();
            }
            current = current.getCause();
        }
        return null;
    }
}
