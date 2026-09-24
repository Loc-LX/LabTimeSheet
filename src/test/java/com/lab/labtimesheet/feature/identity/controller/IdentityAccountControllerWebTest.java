package com.lab.labtimesheet.feature.identity.controller;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lab.labtimesheet.feature.identity.service.AccountService;
import com.lab.labtimesheet.platform.service.SmtpConfigurationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Identity browser contract for Admin account lifecycle actions. */
@WebMvcTest(IdentityAccountController.class)
class IdentityAccountControllerWebTest {
    private static final String ADMIN_EMAIL = "admin@example.test";

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private AccountService accounts;

    @MockitoBean
    private SmtpConfigurationService smtpConfiguration;

    /** Protects ACC-014, ACC-015 and ACC-018 while their Admin routes move with the identity boundary. */
    @Test
    void adminActionsUseAccountOwner() throws Exception {
        when(accounts.requireActiveAdminId(ADMIN_EMAIL)).thenReturn(1L);

        mvc.perform(post("/admin/accounts/7/lock").with(user(ADMIN_EMAIL).roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/accounts/7"));
        mvc.perform(post("/admin/accounts/7/unlock").with(user(ADMIN_EMAIL).roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/admin/accounts/7/deactivate").with(user(ADMIN_EMAIL).roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        verify(accounts).lockAccount(7L, 1L);
        verify(accounts).unlockAccount(7L, 1L);
        verify(accounts).deactivateAccount(7L, 1L);
    }

    /** Protects AUTH-002 and SEC-009 by retaining the non-disclosing redirect for a guessed account identifier. */
    @Test
    void guessedIdentifierDoesNotDiscloseIdentityDetails() throws Exception {
        when(accounts.requireActiveAdminId(ADMIN_EMAIL)).thenReturn(1L);
        doThrow(new IllegalArgumentException("Account not found"))
                .when(accounts).lockAccount(999L, 1L);

        mvc.perform(post("/admin/accounts/999/lock")
                        .with(user(ADMIN_EMAIL).roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/accounts"))
                .andExpect(flash().attribute("accountError", "Account action could not be completed."));
    }
}
