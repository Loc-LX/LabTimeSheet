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
import com.lab.labtimesheet.feature.account.model.dto.AccountDirectoryFilter;
import com.lab.labtimesheet.feature.account.model.dto.AccountIdentityCorrection;
import com.lab.labtimesheet.feature.account.model.dto.InternshipLifecycleGuard;
import com.lab.labtimesheet.feature.account.service.AccountService;
import com.lab.labtimesheet.feature.integration.service.SmtpConfigurationService;
import com.lab.labtimesheet.feature.project.service.ProjectQueryService;
import com.lab.labtimesheet.feature.project.service.ProjectService;
import java.time.LocalDate;
import java.util.List;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
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
                .andExpect(content().string(containsString("Active")));

        mvc.perform(get("/admin/accounts/7").with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ready for terminal action")))
                .andExpect(content().string(containsString("/admin/accounts/7/complete-internship")))
                .andExpect(content().string(containsString("Complete this internship?")))
                .andExpect(content().string(containsString("Withdraw this internship?")))
                .andExpect(content().string(containsString("Deactivate this account?")));
    }

    @Test
    void adminDirectoryAppliesSearchAndImmutableRoleFilter() throws Exception {
        AccountDirectoryFilter filter = new AccountDirectoryFilter("intern", GlobalRole.INTERN);
        when(accounts.requireActiveAdminId(ADMIN_EMAIL)).thenReturn(1L);
        when(accounts.administrationViews(1L, filter)).thenReturn(List.of(intern()));

        mvc.perform(get("/admin/accounts")
                        .param("search", " intern ")
                        .param("role", "INTERN")
                        .with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"intern\"")))
                .andExpect(content().string(containsString("value=\"INTERN\"")))
                .andExpect(content().string(containsString("intern@example.test")));

        verify(accounts).administrationViews(1L, filter);
    }

    @Test
    void adminCanOpenIdentityCorrectionWithoutRoleOrDisplayNameEditors() throws Exception {
        when(accounts.requireActiveAdminId(ADMIN_EMAIL)).thenReturn(1L);
        when(accounts.administrationView(7L, 1L)).thenReturn(intern());

        mvc.perform(get("/admin/accounts/7/edit").with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Account correction")))
                .andExpect(content().string(containsString("name=\"email\"")))
                .andExpect(content().string(containsString("name=\"studentCode\"")))
                .andExpect(content().string(not(containsString("name=\"displayName\""))))
                .andExpect(content().string(not(containsString("name=\"role\""))));
    }

    @Test
    void adminSubmitsIdentityCorrectionThroughAccountService() throws Exception {
        when(accounts.requireActiveAdminId(ADMIN_EMAIL)).thenReturn(1L);
        when(accounts.administrationView(7L, 1L)).thenReturn(intern());

        mvc.perform(post("/admin/accounts/7/edit")
                        .with(user(ADMIN_EMAIL).roles("ADMIN")).with(csrf())
                        .param("email", " corrected@example.test ")
                        .param("studentCode", "STU-008")
                        )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/accounts/7"));

        verify(accounts).correctAccount(7L, 1L, new AccountIdentityCorrection(
                "corrected@example.test", "STU-008", null, null));
    }

    @Test
    void correctionControlsFollowAccountAndInternshipLifecycle() throws Exception {
        AccountAdministrationView pending = account(7L, AccountStatus.PENDING_ACTIVATION, InternshipStatus.NOT_STARTED);
        AccountAdministrationView active = account(8L, AccountStatus.ACTIVE, InternshipStatus.ACTIVE);
        AccountAdministrationView locked = account(9L, AccountStatus.LOCKED, InternshipStatus.ACTIVE);
        AccountAdministrationView completed = account(10L, AccountStatus.ACTIVE, InternshipStatus.COMPLETED);
        AccountAdministrationView withdrawn = account(11L, AccountStatus.ACTIVE, InternshipStatus.WITHDRAWN);
        when(accounts.requireActiveAdminId(ADMIN_EMAIL)).thenReturn(1L);
        when(accounts.administrationView(7L, 1L)).thenReturn(pending);
        when(accounts.administrationView(8L, 1L)).thenReturn(active);
        when(accounts.administrationView(9L, 1L)).thenReturn(locked);
        when(accounts.administrationView(10L, 1L)).thenReturn(completed);
        when(accounts.administrationView(11L, 1L)).thenReturn(withdrawn);

        mvc.perform(get("/admin/accounts/7/edit").with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"email\"")))
                .andExpect(content().string(containsString("id=\"studentCode\"")))
                .andExpect(content().string(containsString("id=\"internshipStart\"")))
                .andExpect(content().string(containsString("id=\"internshipEnd\"")));
        for (long id : List.of(8L, 9L)) {
            mvc.perform(get("/admin/accounts/" + id + "/edit").with(user(ADMIN_EMAIL).roles("ADMIN")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("id=\"email\"")))
                    .andExpect(content().string(containsString("id=\"studentCode\"")))
                    .andExpect(content().string(not(containsString("id=\"internshipStart\""))))
                    .andExpect(content().string(not(containsString("id=\"internshipEnd\""))));
        }
        for (long id : List.of(10L, 11L)) {
            mvc.perform(get("/admin/accounts/" + id + "/edit").with(user(ADMIN_EMAIL).roles("ADMIN")))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("id=\"email\"")))
                    .andExpect(content().string(not(containsString("id=\"studentCode\""))))
                    .andExpect(content().string(not(containsString("id=\"internshipStart\""))));
        }
    }

    @Test
    void deactivatedAccountHasNoCorrectionSurface() throws Exception {
        when(accounts.requireActiveAdminId(ADMIN_EMAIL)).thenReturn(1L);
        when(accounts.administrationView(12L, 1L)).thenReturn(account(12L, AccountStatus.DEACTIVATED, InternshipStatus.ACTIVE));

        mvc.perform(get("/admin/accounts/12/edit").with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void duplicateCorrectionRetainsSafeInputWithoutSqlDiagnostics() throws Exception {
        when(accounts.requireActiveAdminId(ADMIN_EMAIL)).thenReturn(1L);
        when(accounts.administrationView(7L, 1L)).thenReturn(intern());
        doThrow(new DataIntegrityViolationException("SQL duplicate detail", new ConstraintViolationException(
                "duplicate", null, "uq_intern_profiles_student_code_ci")))
                .when(accounts).correctAccount(7L, 1L,
                        new AccountIdentityCorrection("corrected@example.test", "STU-DUPLICATE", null, null));

        mvc.perform(post("/admin/accounts/7/edit")
                        .with(user(ADMIN_EMAIL).roles("ADMIN")).with(csrf())
                        .param("email", "corrected@example.test")
                        .param("studentCode", "STU-DUPLICATE"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("An Intern with this student code already exists")))
                .andExpect(content().string(containsString("STU-DUPLICATE")))
                .andExpect(content().string(not(containsString("SQL duplicate detail"))))
                .andExpect(content().string(not(containsString("uq_intern_profiles_student_code_ci"))));
    }

    @Test
    void directoryUsesHumanLabelsForEveryInternshipStatus() throws Exception {
        when(accounts.requireActiveAdminId(ADMIN_EMAIL)).thenReturn(1L);
        when(accounts.administrationViews(1L)).thenReturn(List.of(
                account(7L, AccountStatus.ACTIVE, InternshipStatus.NOT_STARTED),
                account(8L, AccountStatus.ACTIVE, InternshipStatus.ACTIVE),
                account(9L, AccountStatus.ACTIVE, InternshipStatus.COMPLETED),
                account(10L, AccountStatus.ACTIVE, InternshipStatus.WITHDRAWN)));

        mvc.perform(get("/admin/accounts").with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Not started")))
                .andExpect(content().string(containsString("Active")))
                .andExpect(content().string(containsString("Completed")))
                .andExpect(content().string(containsString("Withdrawn")));
    }

    @Test
    void craftedInternFieldsForNonInternRemainServiceDenied() throws Exception {
        AccountAdministrationView mentor = new AccountAdministrationView(
                8L, "mentor@example.test", "Mentor", GlobalRole.MENTOR, AccountStatus.ACTIVE,
                null, null, null, null);
        when(accounts.requireActiveAdminId(ADMIN_EMAIL)).thenReturn(1L);
        when(accounts.administrationView(8L, 1L)).thenReturn(mentor);
        doThrow(new IllegalArgumentException("Internship fields are allowed only for Intern accounts"))
                .when(accounts).correctAccount(8L, 1L,
                        new AccountIdentityCorrection(null, "STU-999", null, null));

        mvc.perform(post("/admin/accounts/8/edit")
                        .with(user(ADMIN_EMAIL).roles("ADMIN")).with(csrf())
                        .param("studentCode", "STU-999"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Account correction could not be completed.")))
                .andExpect(content().string(not(containsString("name=\"role\""))));

        verify(accounts).correctAccount(8L, 1L,
                new AccountIdentityCorrection(null, "STU-999", null, null));
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
        return account(7L, AccountStatus.ACTIVE, InternshipStatus.ACTIVE);
    }

    private static AccountAdministrationView account(long id, AccountStatus accountStatus,
            InternshipStatus internshipStatus) {
        return new AccountAdministrationView(
                id, id == 7L ? "intern@example.test" : "intern-" + id + "@example.test", "Intern " + id, GlobalRole.INTERN, accountStatus,
                "STU-" + id, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 12, 31), internshipStatus);
    }
}
