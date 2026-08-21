package com.lab.labtimesheet.feature.reporting.controller;

import com.lab.labtimesheet.feature.account.model.AccountStatus;
import com.lab.labtimesheet.feature.account.model.dto.AccountIdentity;
import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.notification.service.NotificationService;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import lombok.RequiredArgsConstructor;

/**
 * Renders the authenticated recipient's notification inbox and its idempotent mark-read action.
 *
 * <p>The route never accepts a recipient identifier from the browser. Account lifecycle is
 * revalidated through the public Account service before the Platform notification boundary is
 * called, so a stale session cannot read or mutate another recipient's rows.</p>
 */
@Controller
@RequestMapping("/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final AccountService accounts;
    private final NotificationService notifications;

    /**
     * Renders the newest-first notification list for the authenticated active account.
     *
     * @param authentication authenticated account whose principal name is its email
     * @param model Thymeleaf model receiving the recipient-scoped inbox
     * @return notification inbox template
     * @throws AccessDeniedException when the account is missing or no longer active
     */
    @GetMapping
    public String inbox(Authentication authentication, Model model) {
        long recipientUserId = recipientUserId(authentication);
        model.addAttribute("notifications", notifications.inboxFor(recipientUserId));
        return "notifications/inbox";
    }

    /**
     * Marks one notification read for the authenticated recipient and returns to the inbox.
     *
     * <p>Platform's recipient-scoped service intentionally treats missing and foreign IDs as a
     * no-op, preserving non-disclosure while allowing repeated form submissions.</p>
     *
     * @param authentication authenticated account
     * @param notificationId requested notification identifier
     * @return redirect to the authenticated inbox
     * @throws AccessDeniedException when the account is missing or no longer active
     */
    @PostMapping("/{notificationId}/read")
    public String markRead(Authentication authentication, @PathVariable long notificationId) {
        notifications.markRead(recipientUserId(authentication), notificationId);
        return "redirect:/notifications";
    }

    private long recipientUserId(Authentication authentication) {
        if (authentication == null || authentication.getName() == null) {
            throw new AccessDeniedException("Active account required for notifications");
        }
        final AccountIdentity identity;
        try {
            identity = accounts.requireIdentityByEmail(authentication.getName());
        } catch (IllegalArgumentException exception) {
            throw new AccessDeniedException("Active account required for notifications");
        }
        if (identity.status() != AccountStatus.ACTIVE) {
            throw new AccessDeniedException("Active account required for notifications");
        }
        return identity.id();
    }
}
