package com.lab.labtimesheet.feature.account.controller;

import com.lab.labtimesheet.feature.account.model.dto.ForgotPasswordForm;
import com.lab.labtimesheet.feature.account.model.dto.PasswordResetForm;
import com.lab.labtimesheet.feature.account.service.AccountService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/** Renders enumeration-safe password recovery and one-time reset forms. */
@Controller
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class PasswordRecoveryController {
    private static final String RESET_ERROR = "This password-reset link is invalid or no longer usable";

    private final AccountService accounts;

    /** @return public recovery request form */
    @GetMapping("/forgot-password")
    String forgot(Model model) {
        if (!model.containsAttribute("forgotPasswordForm")) {
            model.addAttribute("forgotPasswordForm", new ForgotPasswordForm());
        }
        return "accounts/forgot-password";
    }

    /** Queues a reset only when permitted while preserving the same generic response for every email. */
    @PostMapping("/forgot-password")
    String request(@Valid @ModelAttribute("forgotPasswordForm") ForgotPasswordForm form,
            BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            return "accounts/forgot-password";
        }
        accounts.requestPasswordReset(form.getEmail());
        return "redirect:/forgot-password?requested";
    }

    /** @param token opaque token copied into the request-local form */
    @GetMapping("/reset-password")
    String reset(@RequestParam(value = "token", required = false) String token,
            @ModelAttribute("passwordResetForm") PasswordResetForm form, Model model) {
        form.setToken(token);
        if (token == null || token.isBlank()) {
            model.addAttribute("error", RESET_ERROR);
        }
        return "accounts/reset-password";
    }

    /** Consumes the one-time token without disclosing whether a token ever existed. */
    @PostMapping("/reset-password")
    String reset(@Valid @ModelAttribute("passwordResetForm") PasswordResetForm form, BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            form.clearPasswords();
            return "accounts/reset-password";
        }
        if (!accounts.resetPassword(form.getToken(), form.getPassword())) {
            bindingResult.reject("passwordReset.invalid", RESET_ERROR);
            form.clearPasswords();
            return "accounts/reset-password";
        }
        return "redirect:/login?reset";
    }
}
