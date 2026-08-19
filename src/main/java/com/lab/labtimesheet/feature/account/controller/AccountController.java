package com.lab.labtimesheet.feature.account.controller;

import java.security.Principal;

import com.lab.labtimesheet.feature.account.model.GlobalRole;
import com.lab.labtimesheet.feature.account.model.dto.AccountAdminDetail;
import com.lab.labtimesheet.feature.account.model.dto.ActivationForm;
import com.lab.labtimesheet.feature.account.model.dto.CreateAccountForm;
import com.lab.labtimesheet.feature.account.model.dto.EditAccountForm;
import com.lab.labtimesheet.feature.account.model.dto.ForgotPasswordForm;
import com.lab.labtimesheet.feature.account.model.dto.ResetPasswordForm;
import com.lab.labtimesheet.feature.account.service.AccountService;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Handles Admin account management browser flows: creation, single-use activation, listing, detail,
 * and the lock/unlock/deactivation lifecycle actions. Known database uniqueness constraints are mapped
 * to their owning form fields without exposing persistence diagnostics, and lifecycle transition
 * failures redirect back to the affected detail page rather than surfacing a crash page.
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

    @GetMapping("/admin/accounts")
    String list(@RequestParam(value = "role", required = false) GlobalRole role, Model model) {
        model.addAttribute("accountRows", accounts.listAccounts(role));
        model.addAttribute("selectedRole", role);
        return "accounts/list";
    }

    @GetMapping("/admin/accounts/{id}")
    String detail(@PathVariable long id, Model model) {
        model.addAttribute("account", accounts.requireAccountDetail(id));
        return "accounts/detail";
    }

    @GetMapping("/admin/accounts/{id}/edit")
    String editAccount(@PathVariable long id, Model model) {
        AccountAdminDetail detail = accounts.requireAccountDetail(id);
        model.addAttribute("account", detail);
        if (!model.containsAttribute("editAccountForm")) {
            model.addAttribute("editAccountForm", EditAccountForm.from(detail));
        }
        return "accounts/edit";
    }

    @PostMapping("/admin/accounts/{id}/edit")
    String editAccountSubmit(@PathVariable long id,
            @Valid @ModelAttribute("editAccountForm") EditAccountForm form, BindingResult bindingResult,
            Principal principal, Model model) {
        if (bindingResult.hasErrors()) {
            model.addAttribute("account", accounts.requireAccountDetail(id));
            return "accounts/edit";
        }
        try {
            accounts.updateAccountAdminFields(
                    id, accounts.requireActiveAdminId(principal.getName()),
                    form.getUserVersion(), form.getProfileVersion(),
                    form.getEmail(), form.getDisplayName(),
                    form.getStudentCode(), form.getInternshipStart(), form.getInternshipEnd());
            return "redirect:/admin/accounts/" + id + "?updated";
        } catch (DataIntegrityViolationException duplicate) {
            rejectUniquenessViolation(bindingResult, duplicate);
        } catch (IllegalArgumentException | IllegalStateException exception) {
            bindingResult.reject("account.invalid", exception.getMessage());
        }
        model.addAttribute("account", accounts.requireAccountDetail(id));
        return "accounts/edit";
    }

    @PostMapping("/admin/accounts/{id}/lock")
    String lock(@PathVariable long id, Principal principal) {
        try {
            accounts.lockAccount(id, accounts.requireActiveAdminId(principal.getName()));
            return "redirect:/admin/accounts/" + id + "?locked";
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return "redirect:/admin/accounts/" + id + "?error";
        }
    }

    @PostMapping("/admin/accounts/{id}/unlock")
    String unlock(@PathVariable long id, Principal principal) {
        try {
            accounts.unlockAccount(id, accounts.requireActiveAdminId(principal.getName()));
            return "redirect:/admin/accounts/" + id + "?unlocked";
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return "redirect:/admin/accounts/" + id + "?error";
        }
    }

    @PostMapping("/admin/accounts/{id}/deactivate")
    String deactivate(@PathVariable long id, Principal principal) {
        try {
            accounts.deactivateAccount(id, accounts.requireActiveAdminId(principal.getName()));
            return "redirect:/admin/accounts/" + id + "?deactivated";
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return "redirect:/admin/accounts/" + id + "?error";
        }
    }

    @PostMapping("/admin/accounts/{id}/complete-internship")
    String completeInternship(@PathVariable long id, Principal principal) {
        try {
            accounts.completeInternship(id, accounts.requireActiveAdminId(principal.getName()));
            return "redirect:/admin/accounts/" + id + "?completed";
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return "redirect:/admin/accounts/" + id + "?error";
        }
    }

    @PostMapping("/admin/accounts/{id}/withdraw-internship")
    String withdrawInternship(@PathVariable long id, Principal principal) {
        try {
            accounts.withdrawInternship(id, accounts.requireActiveAdminId(principal.getName()));
            return "redirect:/admin/accounts/" + id + "?withdrawn";
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return "redirect:/admin/accounts/" + id + "?error";
        }
    }

    @PostMapping("/admin/accounts/{id}/resend-activation")
    String resendActivation(@PathVariable long id, Principal principal) {
        try {
            var result = accounts.resendActivation(id, accounts.requireActiveAdminId(principal.getName()));
            return "redirect:/admin/accounts/" + id + (result.deliverySucceeded() ? "?resent" : "?error");
        } catch (IllegalArgumentException | IllegalStateException exception) {
            return "redirect:/admin/accounts/" + id + "?error";
        }
    }

    @GetMapping("/forgot-password")
    String forgotPassword(@ModelAttribute("forgotPasswordForm") ForgotPasswordForm form) {
        return "accounts/forgot-password";
    }

    @PostMapping("/forgot-password")
    String forgotPasswordSubmit(@Valid @ModelAttribute("forgotPasswordForm") ForgotPasswordForm form,
            BindingResult bindingResult) {
        if (!bindingResult.hasErrors()) {
            accounts.requestPasswordReset(form.getEmail());
        }
        return "redirect:/forgot-password?sent";
    }

    @GetMapping("/reset-password")
    String resetPasswordForm(@ModelAttribute("resetPasswordForm") ResetPasswordForm form, Model model) {
        if (!accounts.isResetTokenUsable(form.getToken())) {
            model.addAttribute("error", "This reset link is invalid or no longer usable");
        }
        return "accounts/reset-password";
    }

    @PostMapping("/reset-password")
    String resetPasswordSubmit(@Valid @ModelAttribute("resetPasswordForm") ResetPasswordForm form,
            BindingResult bindingResult) {
        if (bindingResult.hasErrors()) {
            form.clearPasswords();
            return "accounts/reset-password";
        }
        try {
            if (accounts.resetPassword(form.getToken(), form.getPassword())) {
                return "redirect:/login?reset";
            }
            bindingResult.reject("reset.invalid", "This reset link is invalid or no longer usable");
        } catch (IllegalArgumentException exception) {
            bindingResult.reject("reset.invalid", exception.getMessage());
        }
        form.clearPasswords();
        return "accounts/reset-password";
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(IllegalArgumentException.class)
    String missingAccount(IllegalArgumentException exception, Model model) {
        model.addAttribute("errorStatus", 404);
        model.addAttribute("errorTitle", "Account not found");
        model.addAttribute("errorMessage", "The requested account could not be found.");
        return "error/generic";
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
                    : "redirect:/admin/accounts/new?deliveryFailed";
        } catch (DataIntegrityViolationException duplicate) {
            rejectUniquenessViolation(bindingResult, duplicate);
            return "accounts/new";
        } catch (IllegalArgumentException | IllegalStateException exception) {
            bindingResult.reject("account.invalid", exception.getMessage());
            return "accounts/new";
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
