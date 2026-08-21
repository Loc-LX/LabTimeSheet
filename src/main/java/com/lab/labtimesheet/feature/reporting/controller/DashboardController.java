package com.lab.labtimesheet.feature.reporting.controller;

import com.lab.labtimesheet.feature.account.model.AccountStatus;
import com.lab.labtimesheet.feature.account.model.dto.AccountIdentity;
import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.notification.model.dto.NotificationInbox;
import com.lab.labtimesheet.feature.notification.service.NotificationService;
import com.lab.labtimesheet.feature.reporting.exception.DashboardAccessDeniedException;
import com.lab.labtimesheet.feature.reporting.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Selects the dashboard view for the authenticated global authority.
 *
 * <p>The authority selects only which role-specific flow to invoke. The reporting service then
 * reloads and revalidates the persisted account role and lifecycle before returning any data.
 */
@Controller
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final AccountService accounts;
    private final NotificationService notifications;

    /**
     * Renders the dashboard permitted by the caller's authenticated global role.
     *
     * @param authentication authenticated caller whose name is the persisted account email
     * @param model Thymeleaf model populated with the role-specific {@code dashboard} projection
     *     and recipient-scoped {@code notifications}
     * @return the Admin, Mentor, or Intern dashboard template name
     * @throws DashboardAccessDeniedException when the authority is unsupported or does not match
     *     an active persisted account identity
     */
    @GetMapping("/dashboard")
    public String dashboard(Authentication authentication, Model model) {
        String email = authentication.getName();
        if (hasRole(authentication, "ROLE_ADMIN")) {
            model.addAttribute("dashboard", dashboardService.admin(email));
            model.addAttribute("notifications", notificationInbox(email));
            return "dashboard/admin";
        }
        if (hasRole(authentication, "ROLE_MENTOR")) {
            model.addAttribute("dashboard", dashboardService.mentor(email));
            model.addAttribute("notifications", notificationInbox(email));
            return "dashboard/mentor";
        }
        if (hasRole(authentication, "ROLE_INTERN")) {
            model.addAttribute("dashboard", dashboardService.intern(email));
            model.addAttribute("notifications", notificationInbox(email));
            return "dashboard/intern";
        }
        throw new DashboardAccessDeniedException("Dashboard access requires a supported global role");
    }

    private NotificationInbox notificationInbox(String email) {
        final AccountIdentity identity;
        try {
            identity = accounts.requireIdentityByEmail(email);
        } catch (IllegalArgumentException exception) {
            throw new DashboardAccessDeniedException("Active account required for notifications");
        }
        if (identity.status() != AccountStatus.ACTIVE) {
            throw new DashboardAccessDeniedException("Active account required for notifications");
        }
        return notifications.inboxFor(identity.id());
    }

    private boolean hasRole(Authentication authentication, String role) {
        return authentication.getAuthorities().stream().anyMatch(authority -> authority.getAuthority().equals(role));
    }
}
