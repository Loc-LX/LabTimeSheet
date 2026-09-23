package com.lab.labtimesheet.feature.identity.controller;

import java.security.Principal;

import com.lab.labtimesheet.feature.identity.model.dto.ActivationForm;
import com.lab.labtimesheet.feature.identity.service.AccountService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Handles identity-owned account lifecycle actions and single-use activation browser flows. */
@Controller
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class IdentityAccountController {
    private final AccountService accounts;

    /** Reissues one activation link for the pending account named by the failed-delivery page. */
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

    /** Locks one account through the identity boundary. */
    @PostMapping("/admin/accounts/{targetUserId}/lock")
    String lockAccount(
            @PathVariable long targetUserId,
            Principal principal,
            RedirectAttributes redirectAttributes) {
        long adminId = accounts.requireActiveAdminId(principal.getName());
        return mutateAccount(
                targetUserId,
                redirectAttributes,
                () -> accounts.lockAccount(targetUserId, adminId),
                "Account locked");
    }

    /** Unlocks one account through the identity boundary. */
    @PostMapping("/admin/accounts/{targetUserId}/unlock")
    String unlockAccount(
            @PathVariable long targetUserId,
            Principal principal,
            RedirectAttributes redirectAttributes) {
        long adminId = accounts.requireActiveAdminId(principal.getName());
        return mutateAccount(
                targetUserId,
                redirectAttributes,
                () -> accounts.unlockAccount(targetUserId, adminId),
                "Account unlocked");
    }

    /** Deactivates one account through the identity boundary. */
    @PostMapping("/admin/accounts/{targetUserId}/deactivate")
    String deactivateAccount(
            @PathVariable long targetUserId,
            Principal principal,
            RedirectAttributes redirectAttributes) {
        long adminId = accounts.requireActiveAdminId(principal.getName());
        return mutateAccount(
                targetUserId,
                redirectAttributes,
                () -> accounts.deactivateAccount(targetUserId, adminId),
                "Account deactivated");
    }

    /** Opens the single-use activation form. */
    @GetMapping("/activate")
    String activationForm(@ModelAttribute("activationForm") ActivationForm form, Model model) {
        if (form.getToken() == null || form.getToken().isBlank()) {
            model.addAttribute("error", "This activation link is invalid or no longer usable");
        }
        return "accounts/activate";
    }

    /** Activates one pending identity with the submitted token and password. */
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

    private static String mutateAccount(
            long targetUserId,
            RedirectAttributes redirectAttributes,
            Runnable mutation,
            String successMessage) {
        try {
            mutation.run();
            redirectAttributes.addFlashAttribute("message", successMessage);
            return accountRedirect(targetUserId);
        } catch (IllegalArgumentException failure) {
            redirectAttributes.addFlashAttribute("accountError", "Account action could not be completed.");
            return "redirect:/admin/accounts";
        } catch (IllegalStateException failure) {
            redirectAttributes.addFlashAttribute("accountError", failure.getMessage());
            return accountRedirect(targetUserId);
        }
    }

    private static String accountRedirect(long targetUserId) {
        return "redirect:/admin/accounts/" + targetUserId;
    }
}
