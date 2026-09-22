package com.lab.labtimesheet.feature.identity.controller;

import java.security.Principal;

import com.lab.labtimesheet.feature.identity.model.AccountStatus;
import com.lab.labtimesheet.feature.identity.model.dto.AccountAdministrationView;
import com.lab.labtimesheet.feature.identity.model.dto.AccountCorrectionForm;
import com.lab.labtimesheet.feature.identity.model.dto.AccountDirectoryFilter;
import com.lab.labtimesheet.feature.identity.model.dto.ActivationForm;
import com.lab.labtimesheet.feature.identity.model.dto.CreateAccountForm;
import com.lab.labtimesheet.feature.identity.service.AccountService;
import com.lab.labtimesheet.feature.project.exception.ProjectAccessDeniedException;
import com.lab.labtimesheet.feature.project.service.ProjectQueryService;
import com.lab.labtimesheet.feature.project.service.ProjectService;
import com.lab.labtimesheet.platform.model.GlobalRole;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Handles Admin account creation, lifecycle administration, and single-use account activation browser flows.
 * Known database uniqueness constraints are mapped to their owning form fields without exposing persistence
 * diagnostics. Intern terminal actions recompute Project and Task readiness through the producer-owned services.
 */
@Controller
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
class AccountController {
    private final AccountService accounts;
    private final ProjectQueryService projectQueries;
    private final ProjectService projects;

    @GetMapping("/admin/accounts")
    String accountList(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) GlobalRole role,
            Principal principal,
            Model model) {
        long adminId = accounts.requireActiveAdminId(principal.getName());
        AccountDirectoryFilter filter = new AccountDirectoryFilter(search, role);
        model.addAttribute("accounts", filter.search().isEmpty() && filter.role() == null
                ? accounts.administrationViews(adminId)
                : accounts.administrationViews(adminId, filter));
        model.addAttribute("accountFilter", filter);
        model.addAttribute("accountRoles", GlobalRole.values());
        return "accounts/index";
    }

    @GetMapping("/admin/accounts/{targetUserId}")
    String accountDetail(@PathVariable long targetUserId, Principal principal, Model model) {
        long adminId = accounts.requireActiveAdminId(principal.getName());
        try {
            var account = accounts.administrationView(targetUserId, adminId);
            model.addAttribute("selectedAccount", account);
            if (account.role() == GlobalRole.INTERN) {
                model.addAttribute(
                        "readiness",
                        projectQueries.internshipLifecycleGuard(adminId, targetUserId));
            }
            return "accounts/index";
        } catch (IllegalArgumentException | ProjectAccessDeniedException failure) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    /** Opens the Admin-only identity correction form for one non-secret account projection. */
    @GetMapping("/admin/accounts/{targetUserId}/edit")
    String accountEdit(@PathVariable long targetUserId, Principal principal, Model model) {
        long adminId = accounts.requireActiveAdminId(principal.getName());
        try {
            var account = accounts.administrationView(targetUserId, adminId);
            if (account.accountStatus() == AccountStatus.DEACTIVATED) {
                throw new IllegalArgumentException("Account not editable");
            }
            model.addAttribute("selectedAccount", account);
            model.addAttribute("correctionForm", new AccountCorrectionForm(account));
            return "accounts/edit";
        } catch (IllegalArgumentException | ProjectAccessDeniedException failure) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

    /**
     * Applies an Admin identity correction through the public Account service and retains safe
     * input when validation or delivery fails.
     */
    @PostMapping("/admin/accounts/{targetUserId}/edit")
    String accountEdit(
            @PathVariable long targetUserId,
            @Valid @ModelAttribute("correctionForm") AccountCorrectionForm form,
            BindingResult bindingResult,
            Principal principal,
            Model model,
            RedirectAttributes redirectAttributes) {
        long adminId = accounts.requireActiveAdminId(principal.getName());
        var account = editableAccount(targetUserId, adminId);
        model.addAttribute("selectedAccount", account);
        if (bindingResult.hasErrors()) {
            return "accounts/edit";
        }
        try {
            accounts.correctAccount(targetUserId, adminId, form.toCorrection());
            redirectAttributes.addFlashAttribute("message", "Account correction saved");
            return accountRedirect(targetUserId);
        } catch (IllegalArgumentException | IllegalStateException failure) {
            bindingResult.reject("account.correction.invalid", "Account correction could not be completed.");
            return "accounts/edit";
        } catch (DataIntegrityViolationException duplicate) {
            rejectUniquenessViolation(bindingResult, duplicate);
            return "accounts/edit";
        }
    }

    private AccountAdministrationView editableAccount(long targetUserId, long adminId) {
        try {
            var account = accounts.administrationView(targetUserId, adminId);
            if (account.accountStatus() == AccountStatus.DEACTIVATED) {
                throw new IllegalArgumentException("Account not editable");
            }
            return account;
        } catch (IllegalArgumentException | ProjectAccessDeniedException failure) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
    }

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

    @PostMapping("/admin/accounts/{targetUserId}/complete-internship")
    String completeInternship(
            @PathVariable long targetUserId,
            Principal principal,
            RedirectAttributes redirectAttributes) {
        long adminId = accounts.requireActiveAdminId(principal.getName());
        return mutateAccount(
                targetUserId,
                redirectAttributes,
                () -> projects.completeInternship(adminId, targetUserId),
                "Internship completed");
    }

    @PostMapping("/admin/accounts/{targetUserId}/withdraw-internship")
    String withdrawInternship(
            @PathVariable long targetUserId,
            Principal principal,
            RedirectAttributes redirectAttributes) {
        long adminId = accounts.requireActiveAdminId(principal.getName());
        return mutateAccount(
                targetUserId,
                redirectAttributes,
                () -> projects.withdrawInternship(adminId, targetUserId),
                "Internship withdrawn");
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
