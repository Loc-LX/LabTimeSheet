package com.lab.labtimesheet.feature.internship.controller;

import com.lab.labtimesheet.feature.internship.service.InternshipService;

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

import com.lab.labtimesheet.feature.identity.model.AccountStatus;
import com.lab.labtimesheet.feature.internship.model.InternshipStatus;
import com.lab.labtimesheet.feature.identity.model.dto.AccountDirectoryFilter;
import com.lab.labtimesheet.feature.identity.model.dto.AccountIdentityCorrection;
import com.lab.labtimesheet.feature.internship.model.dto.InternshipAccountAdministrationView;
import com.lab.labtimesheet.feature.internship.model.dto.InternshipLifecycleGuard;
import com.lab.labtimesheet.feature.identity.service.AccountService;
import com.lab.labtimesheet.platform.model.GlobalRole;
import com.lab.labtimesheet.platform.service.SmtpConfigurationService;
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
    private InternshipService internships;

    @MockitoBean
    private SmtpConfigurationService smtpConfiguration;

    @Test
    void adminListsAccountsAndSeesLockedProjectTaskReadinessOnInternDetail() throws Exception {
        InternshipAccountAdministrationView intern = intern();
        when(accounts.requireActiveAdminId(ADMIN_EMAIL)).thenReturn(1L);
        when(internships.administrationViews(1L)).thenReturn(List.of(
                new InternshipAccountAdministrationView(
                        1L, ADMIN_EMAIL, "Admin", GlobalRole.ADMIN, AccountStatus.ACTIVE,
                        null, null, null, null),
                intern));
        when(internships.administrationView(7L, 1L)).thenReturn(intern);
        when(internships.internshipLifecycleReadiness(7L, 1L))
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
    void accountDetailSeparatesAccessFromBlockedInternLifecycle() throws Exception {
        InternshipAccountAdministrationView intern = intern();
        when(accounts.requireActiveAdminId(ADMIN_EMAIL)).thenReturn(1L);
        when(internships.administrationView(7L, 1L)).thenReturn(intern);
        when(internships.internshipLifecycleReadiness(7L, 1L))
                .thenReturn(new InternshipLifecycleGuard(true, 2));

        mvc.perform(get("/admin/accounts/7").with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("class=\"account-detail-header\"")))
                .andExpect(content().string(containsString("Account ID 7")))
                .andExpect(content().string(containsString("account-detail-badges")))
                .andExpect(content().string(containsString("Account access")))
                .andExpect(content().string(containsString("account-detail-access")))
                .andExpect(content().string(containsString("Immutable role")))
                .andExpect(content().string(containsString("Internship lifecycle")))
                .andExpect(content().string(containsString("account-detail-lifecycle")))
                .andExpect(content().string(containsString("Terminal-action readiness")))
                .andExpect(content().string(containsString("Blocked:")))
                .andExpect(content().string(containsString("currently a Project Leader")))
                .andExpect(content().string(containsString("2 unfinished Tasks")));
    }

    @Test
    void adminDirectoryAppliesSearchAndImmutableRoleFilter() throws Exception {
        AccountDirectoryFilter filter = new AccountDirectoryFilter("intern", GlobalRole.INTERN);
        when(accounts.requireActiveAdminId(ADMIN_EMAIL)).thenReturn(1L);
        when(internships.administrationViews(1L, filter)).thenReturn(List.of(intern()));

        mvc.perform(get("/admin/accounts")
                        .param("search", " intern ")
                        .param("role", "INTERN")
                        .with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("value=\"intern\"")))
                .andExpect(content().string(containsString("value=\"INTERN\"")))
                .andExpect(content().string(containsString("intern@example.test")));

        verify(internships).administrationViews(1L, filter);
    }

    @Test
    void adminCanOpenIdentityCorrectionWithoutRoleOrDisplayNameEditors() throws Exception {
        when(accounts.requireActiveAdminId(ADMIN_EMAIL)).thenReturn(1L);
        when(internships.administrationView(7L, 1L)).thenReturn(intern());

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
        when(internships.administrationView(7L, 1L)).thenReturn(intern());

        mvc.perform(post("/admin/accounts/7/edit")
                        .with(user(ADMIN_EMAIL).roles("ADMIN")).with(csrf())
                        .param("email", " corrected@example.test ")
                        .param("studentCode", "STU-008")
                        )
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/admin/accounts/7"));

        verify(internships).correctAccount(7L, 1L, new AccountIdentityCorrection(
                "corrected@example.test", "STU-008", null, null));
    }

    @Test
    void correctionControlsFollowAccountAndInternshipLifecycle() throws Exception {
        InternshipAccountAdministrationView pending = account(7L, AccountStatus.PENDING_ACTIVATION, InternshipStatus.NOT_STARTED);
        InternshipAccountAdministrationView active = account(8L, AccountStatus.ACTIVE, InternshipStatus.ACTIVE);
        InternshipAccountAdministrationView locked = account(9L, AccountStatus.LOCKED, InternshipStatus.ACTIVE);
        InternshipAccountAdministrationView completed = account(10L, AccountStatus.ACTIVE, InternshipStatus.COMPLETED);
        InternshipAccountAdministrationView withdrawn = account(11L, AccountStatus.ACTIVE, InternshipStatus.WITHDRAWN);
        when(accounts.requireActiveAdminId(ADMIN_EMAIL)).thenReturn(1L);
        when(internships.administrationView(7L, 1L)).thenReturn(pending);
        when(internships.administrationView(8L, 1L)).thenReturn(active);
        when(internships.administrationView(9L, 1L)).thenReturn(locked);
        when(internships.administrationView(10L, 1L)).thenReturn(completed);
        when(internships.administrationView(11L, 1L)).thenReturn(withdrawn);

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
        when(internships.administrationView(12L, 1L)).thenReturn(account(12L, AccountStatus.DEACTIVATED, InternshipStatus.ACTIVE));

        mvc.perform(get("/admin/accounts/12/edit").with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isNotFound());
    }

    @Test
    void duplicateCorrectionRetainsSafeInputWithoutSqlDiagnostics() throws Exception {
        when(accounts.requireActiveAdminId(ADMIN_EMAIL)).thenReturn(1L);
        when(internships.administrationView(7L, 1L)).thenReturn(intern());
        doThrow(new DataIntegrityViolationException("SQL duplicate detail", new ConstraintViolationException(
                "duplicate", null, "uq_intern_profiles_student_code_ci")))
                .when(internships).correctAccount(7L, 1L,
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
        when(internships.administrationViews(1L)).thenReturn(List.of(
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
        InternshipAccountAdministrationView mentor = new InternshipAccountAdministrationView(
                8L, "mentor@example.test", "Mentor", GlobalRole.MENTOR, AccountStatus.ACTIVE,
                null, null, null, null);
        when(accounts.requireActiveAdminId(ADMIN_EMAIL)).thenReturn(1L);
        when(internships.administrationView(8L, 1L)).thenReturn(mentor);
        doThrow(new IllegalArgumentException("Internship fields are allowed only for Intern accounts"))
                .when(internships).correctAccount(8L, 1L,
                        new AccountIdentityCorrection(null, "STU-999", null, null));

        mvc.perform(post("/admin/accounts/8/edit")
                        .with(user(ADMIN_EMAIL).roles("ADMIN")).with(csrf())
                        .param("studentCode", "STU-999"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Account correction could not be completed.")))
                .andExpect(content().string(not(containsString("name=\"role\""))));

        verify(internships).correctAccount(8L, 1L,
                new AccountIdentityCorrection(null, "STU-999", null, null));
    }

    @Test
    void adminActionsUseAccountAndProjectOwners() throws Exception {
        when(accounts.requireActiveAdminId(ADMIN_EMAIL)).thenReturn(1L);

        mvc.perform(post("/admin/accounts/7/complete-internship")
                        .with(user(ADMIN_EMAIL).roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/admin/accounts/7/withdraw-internship")
                        .with(user(ADMIN_EMAIL).roles("ADMIN")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        verify(internships).completeInternship(7L, 1L);
        verify(internships).withdrawInternship(7L, 1L);
    }

    @Test
    void guessedIdentifierDoesNotDiscloseAccountDetails() throws Exception {
        when(accounts.requireActiveAdminId(ADMIN_EMAIL)).thenReturn(1L);
        when(internships.administrationView(999L, 1L)).thenThrow(new IllegalArgumentException("Account not found"));
        mvc.perform(get("/admin/accounts/999").with(user(ADMIN_EMAIL).roles("ADMIN")))
                .andExpect(status().isNotFound())
                .andExpect(content().string(not(containsString("Account not found"))));
    }

    private static InternshipAccountAdministrationView intern() {
        return account(7L, AccountStatus.ACTIVE, InternshipStatus.ACTIVE);
    }

    private static InternshipAccountAdministrationView account(long id, AccountStatus accountStatus,
            InternshipStatus internshipStatus) {
        return new InternshipAccountAdministrationView(
                id, id == 7L ? "intern@example.test" : "intern-" + id + "@example.test", "Intern " + id, GlobalRole.INTERN, accountStatus,
                "STU-" + id, LocalDate.of(2026, 8, 1), LocalDate.of(2026, 12, 31), internshipStatus);
    }
}
