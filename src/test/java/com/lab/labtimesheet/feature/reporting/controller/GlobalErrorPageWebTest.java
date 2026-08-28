package com.lab.labtimesheet.feature.reporting.controller;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.lab.labtimesheet.feature.integration.service.SmtpConfigurationService;
import com.lab.labtimesheet.feature.project.service.ProjectQueryService;
import jakarta.servlet.RequestDispatcher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.autoconfigure.error.DefaultErrorViewResolver;
import org.springframework.boot.webmvc.autoconfigure.error.ErrorViewResolver;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Controller;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(GlobalErrorPageWebTest.ErrorProbeController.class)
@AutoConfigureMockMvc
@Import({GlobalErrorPageWebTest.ErrorViewConfiguration.class,
        GlobalErrorPageWebTest.TestSecurityConfiguration.class})
class GlobalErrorPageWebTest {

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private SmtpConfigurationService smtpConfiguration;

    @MockitoBean
    private ProjectQueryService projectQueries;

    @Test
    void browserErrorDispatchUsesDedicatedNotFoundPage() throws Exception {
        mvc.perform(get("/error")
                        .accept(MediaType.TEXT_HTML)
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/missing-resource"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Page not found")));
    }

    @Test
    void unexpectedServerErrorUsesSafeFailurePageWhenGlobalViewAdviceCannotReachDatabase() throws Exception {
        doThrow(new IllegalStateException(
                "database secret; exception-message-secret; java.lang.IllegalStateException; "
                        + "SecretController.java:42; submitted-value-secret; "
                        + "SELECT * FROM smtp_configurations"))
                .when(smtpConfiguration).hasActiveConfiguration();

        mvc.perform(get("/error").with(anonymous())
                        .accept(MediaType.TEXT_HTML)
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI,
                                "/dashboard/secret-account?submitted=submitted-value-secret")
                        .requestAttr(RequestDispatcher.ERROR_EXCEPTION,
                                new IllegalStateException(
                                        "database secret; exception-message-secret; java.lang.IllegalStateException; "
                                                + "SecretController.java:42; submitted-value-secret; "
                                                + "SELECT * FROM smtp_configurations\n"
                                                + "at com.lab.labtimesheet.SecretController.handle")))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Something went wrong")))
                .andExpect(content().string(not(containsString("database secret"))))
                .andExpect(content().string(not(containsString("exception-message-secret"))))
                .andExpect(content().string(not(containsString("/dashboard/secret-account"))))
                .andExpect(content().string(not(containsString("java.lang.IllegalStateException"))))
                .andExpect(content().string(not(containsString("SecretController.java:42"))))
                .andExpect(content().string(not(containsString("submitted-value-secret"))))
                .andExpect(content().string(not(containsString("SELECT * FROM smtp_configurations"))))
                .andExpect(content().string(not(containsString("at com.lab.labtimesheet"))));
    }

    @Test
    void anonymousForbiddenPageOffersSignInRecoveryWithoutAuthenticatedNavigation() throws Exception {
        mvc.perform(get("/error").with(anonymous())
                        .accept(MediaType.TEXT_HTML)
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 403)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/admin/settings"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("You do not have access to this page")))
                .andExpect(content().string(containsString("href=\"/login\"")))
                .andExpect(content().string(not(containsString("Return to dashboard"))))
                .andExpect(content().string(not(containsString("anonymousUser"))));
    }

    @Test
    void authenticatedForbiddenPageOffersDashboardRecoveryWithoutAnonymousNavigation() throws Exception {
        mvc.perform(get("/error").with(user("mentor@example.test").roles("MENTOR"))
                        .accept(MediaType.TEXT_HTML)
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 403)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/admin/settings"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("You do not have access to this page")))
                .andExpect(content().string(containsString("href=\"/dashboard\"")))
                .andExpect(content().string(containsString("Return to dashboard")))
                .andExpect(content().string(not(containsString("Sign in"))))
                .andExpect(content().string(not(containsString("Logout"))))
                .andExpect(content().string(not(containsString("anonymousUser"))));
    }

    @Test
    void browserClientErrorUsesGeneralFallbackWithoutRenderingRequestOrExceptionDetails() throws Exception {
        mvc.perform(get("/error").with(anonymous())
                        .accept(MediaType.TEXT_HTML)
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 400)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI,
                                "/tasks/secret-task-id?submitted=binding-secret-value")
                        .requestAttr(RequestDispatcher.ERROR_EXCEPTION,
                                new IllegalArgumentException(
                                        "SQL secret-task-id details; binding-secret-value; "
                                                + "at com.lab.labtimesheet.TaskController.handle")))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Request could not be completed")))
                .andExpect(content().string(containsString("href=\"/login\"")))
                .andExpect(content().string(not(containsString("secret-task-id"))))
                .andExpect(content().string(not(containsString("SQL secret-task-id details"))))
                .andExpect(content().string(not(containsString("binding-secret-value"))))
                .andExpect(content().string(not(containsString("at com.lab.labtimesheet"))));
    }

    @Test
    void jsonErrorNegotiationRemainsNonHtml() throws Exception {
        mvc.perform(get("/error").with(anonymous())
                        .accept(MediaType.APPLICATION_JSON)
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 404)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI,
                                "/missing-resource/secret-resource?submitted=json-secret-value")
                        .requestAttr(RequestDispatcher.ERROR_EXCEPTION,
                                new IllegalStateException(
                                        "json-exception-secret; java.lang.IllegalStateException; "
                                                + "SELECT * FROM smtp_configurations")))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(content().string(not(containsString("<html"))))
                .andExpect(content().string(not(containsString("Lab Timesheet"))))
                .andExpect(content().string(not(containsString("Page not found"))))
                .andExpect(content().string(not(containsString("json-exception-secret"))))
                .andExpect(content().string(not(containsString("SELECT * FROM smtp_configurations"))))
                .andExpect(content().string(not(containsString("/missing-resource/secret-resource"))))
                .andExpect(content().string(not(containsString("json-secret-value"))));
    }

    @Test
    void authenticatedInternServerErrorDoesNotQueryProjectNavigationAdvice() throws Exception {
        doThrow(new IllegalStateException("database daily-report secret"))
                .when(projectQueries).authenticatedActor(anyString());

        mvc.perform(get("/error").with(user("intern@example.test").roles("INTERN"))
                        .accept(MediaType.TEXT_HTML)
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, 500)
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/reports/daily"))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Something went wrong")))
                .andExpect(content().string(not(containsString("database daily-report secret"))));

        verifyNoInteractions(projectQueries);
    }

    @Test
    void roleDeniedRequestRendersForbiddenErrorPage() throws Exception {
        var denied = mvc.perform(get("/role-probe").with(user("mentor@example.test").roles("MENTOR"))
                        .accept(MediaType.TEXT_HTML))
                .andExpect(status().isForbidden())
                .andReturn();

        // MockMvc does not run the servlet container's registered ERROR dispatcher after sendError.
        // Follow that public dispatch explicitly with the status returned by Spring Security.
        mvc.perform(get("/error").with(user("mentor@example.test").roles("MENTOR"))
                        .accept(MediaType.TEXT_HTML)
                        .requestAttr(RequestDispatcher.ERROR_STATUS_CODE, denied.getResponse().getStatus())
                        .requestAttr(RequestDispatcher.ERROR_REQUEST_URI, "/role-probe"))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("You do not have access to this page")));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class ErrorViewConfiguration {

        @Bean
        ErrorViewResolver conventionErrorViewResolver(ApplicationContext context, WebProperties properties) {
            return new DefaultErrorViewResolver(context, properties.getResources());
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestSecurityConfiguration {

        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            return http.authorizeHttpRequests(authorize -> authorize
                    .requestMatchers("/role-probe").hasRole("ADMIN")
                    .anyRequest().permitAll()).build();
        }
    }

    @Controller
    static class ErrorProbeController {
    }
}
