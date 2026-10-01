package com.lab.labtimesheet.feature.reporting.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.lab.labtimesheet.feature.identity.model.AccountStatus;
import com.lab.labtimesheet.feature.identity.model.dto.AccountIdentity;
import com.lab.labtimesheet.feature.identity.service.AccountService;
import com.lab.labtimesheet.platform.authorization.AuthorizationColumn;
import com.lab.labtimesheet.platform.authorization.AuthorizationRequest;
import com.lab.labtimesheet.platform.model.GlobalRole;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.TestingAuthenticationToken;

/**
 * Protects {@code AUTH-012} and {@code AC-AUTH-011}. Observable break: the shared layout asks the
 * policy with role-derived scope that disagrees with the active stored identity. Expected scope is
 * the one resolved from the Account service DTO.
 */
class AttendanceReportNavigationAdviceTest {

    @Test
    void suppliesOnlyActiveStoredMentorScopeToTheTemplatePolicyCall() {
        AccountService accounts = mock(AccountService.class);
        ObjectProvider<AccountService> provider = mock(ObjectProvider.class);
        given(provider.getIfAvailable()).willReturn(accounts);
        given(accounts.requireIdentityByEmail("mentor@example.test")).willReturn(new AccountIdentity(
                5L, "mentor@example.test", "Mentor", GlobalRole.MENTOR, AccountStatus.ACTIVE));
        AttendanceReportNavigationAdvice advice = new AttendanceReportNavigationAdvice(provider);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/projects");
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(
                "mentor@example.test", "", "ROLE_MENTOR");

        AuthorizationRequest result = advice.authorizationRequest(authentication, request);

        assertThat(result.actorColumns()).containsExactly(AuthorizationColumn.OWNING_MENTOR);
        assertThat(result.scopeState()).isEqualTo("ACTIVE");
        assertThat(result.recordState()).isNull();
        assertThat(result.targetState()).isNull();
    }

    @Test
    void inactiveStoredIdentityHasNoTemplateAuthorizationContext() {
        AccountService accounts = mock(AccountService.class);
        ObjectProvider<AccountService> provider = mock(ObjectProvider.class);
        given(provider.getIfAvailable()).willReturn(accounts);
        given(accounts.requireIdentityByEmail("locked@example.test")).willReturn(new AccountIdentity(
                5L, "locked@example.test", "Locked", GlobalRole.ADMIN, AccountStatus.LOCKED));
        AttendanceReportNavigationAdvice advice = new AttendanceReportNavigationAdvice(provider);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/projects");
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(
                "locked@example.test", "", "ROLE_ADMIN");

        assertThat(advice.authorizationRequest(authentication, request)).isNull();
    }

    /**
     * Protects {@code AUTH-012} and {@code AC-AUTH-011}. Observable break: a missing stored identity crashes page
     * rendering; expected template context is null so the report link fails closed.
     */
    @Test
    void missingStoredIdentityHasNoTemplateAuthorizationContext() {
        AccountService accounts = mock(AccountService.class);
        ObjectProvider<AccountService> provider = mock(ObjectProvider.class);
        given(provider.getIfAvailable()).willReturn(accounts);
        AttendanceReportNavigationAdvice advice = new AttendanceReportNavigationAdvice(provider);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/projects");
        TestingAuthenticationToken authentication = new TestingAuthenticationToken(
                "missing@example.test", "", "ROLE_INTERN");

        assertThat(advice.authorizationRequest(authentication, request)).isNull();
    }
}
