package com.lab.labtimesheet.feature.reporting.controller;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.lab.labtimesheet.feature.account.model.AccountStatus;
import com.lab.labtimesheet.feature.account.model.GlobalRole;
import com.lab.labtimesheet.feature.account.model.dto.AccountIdentity;
import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.integration.service.SmtpConfigurationService;
import com.lab.labtimesheet.feature.notification.model.NotificationType;
import com.lab.labtimesheet.feature.notification.model.dto.NotificationInbox;
import com.lab.labtimesheet.feature.notification.model.dto.NotificationInboxItem;
import com.lab.labtimesheet.feature.notification.service.NotificationService;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Production-shaped MVC contract for recipient-scoped notification rendering and mutation. */
@WebMvcTest(NotificationController.class)
class NotificationControllerWebTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AccountService accounts;

    @MockitoBean
    private NotificationService notifications;

    @MockitoBean
    private SmtpConfigurationService smtpConfiguration;

    @Test
    void inboxUsesAuthenticatedRecipientAndRendersAccessibleItems() throws Exception {
        given(accounts.requireIdentityByEmail("intern@example.test")).willReturn(
                new AccountIdentity(17L, "intern@example.test", "Intern", GlobalRole.INTERN, AccountStatus.ACTIVE));
        given(notifications.inboxFor(17L)).willReturn(new NotificationInbox(List.of(new NotificationInboxItem(
                5L,
                NotificationType.TASK_COMMENTED,
                "Task comment",
                "A comment needs review.",
                "/projects/7/tasks/3",
                Instant.parse("2026-08-21T00:00:00Z"),
                false)), 1L));

        mvc.perform(get("/notifications").with(user("intern@example.test").roles("INTERN")))
                .andExpect(status().isOk())
                .andExpect(view().name("notifications/inbox"))
                .andExpect(content().string(containsString("Task comment")))
                .andExpect(content().string(containsString("Notifications, 1 unread")))
                .andExpect(content().string(containsString("/notifications/5/read")));

        verify(notifications).inboxFor(17L);
    }

    @Test
    void markReadUsesAuthenticatedRecipientAndCsrfProtectedPost() throws Exception {
        given(accounts.requireIdentityByEmail("intern@example.test")).willReturn(
                new AccountIdentity(17L, "intern@example.test", "Intern", GlobalRole.INTERN, AccountStatus.ACTIVE));

        mvc.perform(post("/notifications/5/read")
                        .with(user("intern@example.test").roles("INTERN"))
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/notifications"));

        verify(notifications).markRead(17L, 5L);
    }

    @Test
    void inboxRequiresAuthenticationBeforeResolvingRecipient() throws Exception {
        mvc.perform(get("/notifications"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(accounts, notifications);
    }

    @Test
    void inboxRejectsAnInactiveAccountBeforeReadingNotifications() throws Exception {
        given(accounts.requireIdentityByEmail("intern@example.test")).willReturn(
                new AccountIdentity(17L, "intern@example.test", "Intern", GlobalRole.INTERN,
                        AccountStatus.DEACTIVATED));

        mvc.perform(get("/notifications").with(user("intern@example.test").roles("INTERN")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(notifications);
    }
}
