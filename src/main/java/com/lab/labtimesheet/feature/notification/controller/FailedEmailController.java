package com.lab.labtimesheet.feature.notification.controller;

import java.security.Principal;
import com.lab.labtimesheet.feature.identity.service.AccountService;
import com.lab.labtimesheet.feature.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * Renders the Admin failed-email inspection list and handles manual retry requests.
 *
 * <p>Both operations delegate authorization to the platform notification service, which verifies
 * that the authenticated account is an active Admin before accessing failed delivery rows. The
 * route prefix {@code /admin/**} further restricts access to the {@code ADMIN} role at the
 * security layer; no additional role annotation is needed here (NOT-007, platform permission
 * matrix).
 *
 * <p>The retry action re-enters bounded retry state for the identified notification row without
 * creating a second in-app notification row. The view exposes recipient address and last error but
 * never the email body (NOT-007, AC-NOT-002, AC-NOT-007).
 */
@Controller
@RequestMapping("/admin/notifications/failed-email")
@RequiredArgsConstructor
public class FailedEmailController {

    private final AccountService accounts;
    private final NotificationService notifications;

    /**
     * Renders the failed-email delivery list for the authenticated active Admin.
     *
     * <p>Exposes recipient address and last error per row so the Admin can identify the failing
     * mailbox without reading message content (NOT-007, AC-NOT-002, AC-NOT-007).
     *
     * @param principal authenticated Admin account
     * @param model Thymeleaf model receiving the failed delivery projections
     * @return failed-email template name
     * @throws AccessDeniedException when the account is missing or no longer active
     */
    @GetMapping
    public String list(Principal principal, Model model) {
        long adminId = adminId(principal);
        model.addAttribute("failedEmails", notifications.failedEmailViews(adminId));
        return "notifications/failed-email";
    }

    /**
     * Re-enters one identified failed notification into a fresh bounded retry cycle.
     *
     * <p>The existing in-app notification row is reused; no duplicate notification is created
     * (NOT-007, AC-NOT-002). Redirects to the failed-email list regardless of whether the row
     * was found, with a flash status message indicating whether retry was initiated.
     *
     * @param principal authenticated Admin account
     * @param notificationId notification to retry
     * @param redirectAttributes flash attributes carrier for feedback message
     * @return redirect to the failed-email list
     * @throws AccessDeniedException when the account is missing or no longer active
     */
    @PostMapping("/{notificationId}/retry")
    public String retry(
            Principal principal,
            @PathVariable long notificationId,
            RedirectAttributes redirectAttributes) {
        boolean retried = notifications.retryFailedEmail(notificationId, adminId(principal));
        redirectAttributes.addFlashAttribute(
                "message", retried ? "Email retry started." : "This email is no longer in a failed state.");
        return "redirect:/admin/notifications/failed-email";
    }

    private long adminId(Principal principal) {
        if (principal == null || principal.getName() == null) {
            throw new AccessDeniedException("Active Admin account required");
        }
        try {
            return accounts.requireActiveAdminId(principal.getName());
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException("Active Admin account required", exception);
        }
    }
}
