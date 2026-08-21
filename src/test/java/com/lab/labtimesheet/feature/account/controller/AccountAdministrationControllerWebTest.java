package com.lab.labtimesheet.feature.account.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lab.labtimesheet.feature.account.model.AccountStatus;
import com.lab.labtimesheet.feature.account.model.GlobalRole;
import com.lab.labtimesheet.feature.account.model.InternshipStatus;
import com.lab.labtimesheet.feature.account.model.dto.AccountAdministrationView;
import com.lab.labtimesheet.feature.account.model.dto.InternshipLifecycleGuard;
import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.integration.service.SmtpConfigurationService;
import com.lab.labtimesheet.feature.project.service.ProjectQueryService;
import com.lab.labtimesheet.feature.project.service.ProjectService;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Admin browser contract for account state and Intern lifecycle administration. */
@WebMvcTest(AccountController.class)
class AccountAdministrationControllerWebTest {

    private static final String ADMIN_EMAIL = "admin@example.test";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AccountService accounts;

    @MockitoBean
    private ProjectQueryService projectQueries;

    @MockitoBean
    private ProjectService projects;

    @MockitoBean
    private SmtpConfigurationService smtpConfiguration;

    @Test
    void adminListsAccountsAndSeesLockedProjectTaskReadinessOnInternDetail() throws Exception {
        AccountAdministrationView intern = intern();
        when(accounts.requireActiveAdminId(ADMIN_EMAIL)).thenReturn(1L);
        when(accounts.administrationViews(1L)).thenReturn(List.of(
                new AccountAdministrationView(
                        1L, ADMIN_EMAIL, "Admin", GlobalRole.ADMIN, AccountStatus.ACTIVE,
                        null, null, null, null),
                intern));
        when(accounts.administrationView(7L, 1L)).thenReturn(intern);
        when(projectQueries.internshipLifecycleGuard(1L, 7L))
                .thenReturn(new InternshipLifecycleGuard(false, 0));

        mvc.perform(get("/admin/accounts").with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Accounts")))
                .andExpect(content().string(containsString("intern@example.test")))
                .andExpect(content().string(containsString("ACTIVE")));

        mvc.perform(get("/admin/accounts/7").with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ready for terminal action")))
                .andExpect(content().string(containsString("/admin/accounts/7/complete-internship")))
                .andExpect(content().string(containsString("Complete this internship?")))
                .andExpect(content().string(containsString("Withdraw this internship?")))
                .andExpect(content().string(containsString("Deactivate this account?")));
    }

    @Test
    void adminActionsUseAccountAndProjectOwners() throws Exception {
        when(accounts.requireActiveAdminId(ADMIN_EMAIL)).thenReturn(1L);

        mvc.perform(post("/admin/accounts/7/lock").with(user(ADMIN_EMAIL).roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/accounts/7"));
        mvc.perform(post("/admin/accounts/7/unlock").with(user(ADMIN_EMAIL).roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/admin/accounts/7/deactivate").with(user(ADMIN_EMAIL).roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/admin/accounts/7/complete-internship")
                        .with(user(ADMIN_EMAIL).roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/admin/accounts/7/withdraw-internship")
                        .with(user(ADMIN_EMAIL).roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        verify(accounts).lockAccount(7L, 1L);
        verify(accounts).unlockAccount(7L, 1L);
        verify(accounts).deactivateAccount(7L, 1L);
        verify(projects).completeInternship(1L, 7L);
        verify(projects).withdrawInternship(1L, 7L);
    }

    @Test
    void guessedIdentifierDoesNotDiscloseAccountDetails() throws Exception {
        when(accounts.requireActiveAdminId(ADMIN_EMAIL)).thenReturn(1L);
        when(accounts.administrationView(999L, 1L)).thenThrow(new IllegalArgumentException("Account not found"));
        doThrow(new IllegalArgumentException("Account not found"))
                .when(accounts).lockAccount(999L, 1L);

        mvc.perform(get("/admin/accounts/999").with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("Account not found"))));
        mvc.perform(post("/admin/accounts/999/lock")
                        .with(user(ADMIN_EMAIL).roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/accounts"))
                .andExpect(flash().attribute("accountError", "Account action could not be completed."));
    }

    private static AccountAdministrationView intern() {
        return new AccountAdministrationView(
                7L,
                "intern@example.test",
                "Intern",
                GlobalRole.INTERN,
                AccountStatus.ACTIVE,
                "STU-007",
                LocalDate.of(2026, 8, 1),
                LocalDate.of(2026, 12, 31),
                InternshipStatus.ACTIVE);
    }
}
