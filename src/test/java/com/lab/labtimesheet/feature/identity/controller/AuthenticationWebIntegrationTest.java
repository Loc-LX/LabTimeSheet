package com.lab.labtimesheet.feature.identity.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.authenticated;
import static org.springframework.security.test.web.servlet.response.SecurityMockMvcResultMatchers.unauthenticated;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import java.time.Instant;
import java.util.List;

import com.lab.labtimesheet.config.TestcontainersConfiguration;
import com.lab.labtimesheet.feature.identity.model.entity.AppUser;
import com.lab.labtimesheet.feature.identity.repository.AppUserRepository;
import com.lab.labtimesheet.feature.identity.service.BootstrapService;
import com.lab.labtimesheet.platform.model.GlobalRole;
import jakarta.servlet.http.HttpSession;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthenticationWebIntegrationTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BootstrapService bootstrap;

    @Autowired
    private AppUserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void initializeAdmin() {
        bootstrap.bootstrap("admin@example.com", "Admin", "correct horse battery staple");
    }

    @Test
    void projectLoginPageSupportsFailureNormalizedSuccessAndLogout() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("accounts/login"))
                .andExpect(content().string(Matchers.containsString("Sign in")))
                .andExpect(content().string(Matchers.containsString("action=\"/login\"")));

        mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", " ADMIN@EXAMPLE.COM ")
                        .param("password", "incorrect password"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"))
                .andExpect(unauthenticated());

        mockMvc.perform(get("/login").param("error", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("accounts/login"))
                .andExpect(content().string(Matchers.containsString("Invalid email or password")));

        var login = mockMvc.perform(post("/login")
                        .with(csrf())
                        .param("username", " ADMIN@EXAMPLE.COM ")
                        .param("password", "correct horse battery staple"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated().withUsername("admin@example.com"))
                .andReturn();
        var session = (MockHttpSession) login.getRequest().getSession(false);

        mockMvc.perform(get("/").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"));

        mockMvc.perform(post("/logout").session(session).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"))
                .andExpect(unauthenticated());

        mockMvc.perform(get("/login").param("logout", ""))
                .andExpect(status().isOk())
                .andExpect(view().name("accounts/login"))
                .andExpect(content().string(Matchers.containsString("You have signed out")));
    }

    /**
     * Protects {@code ACC-030} and {@code SEC-005} through {@code AC-ACC-022}. Observable break: a
     * {@code LOCKED}, {@code DEACTIVATED} or {@code PENDING_ACTIVATION} account signs in with a correct
     * password, or its refusal differs from a wrong-password refusal. Hand-derived expectation: only
     * the {@code ACTIVE} account with its correct password is authenticated; the other four attempts
     * each redirect to {@code /login?error}, remain unauthenticated, and leave no security context in
     * the session if one exists, with the same generic response.
     */
    @Test
    void onlyAnActiveAccountWithItsCorrectPasswordSignsIn() throws Exception {
        String password = "correct matrix password";
        String encoded = passwordEncoder.encode(password);
        Instant now = Instant.parse("2026-09-26T12:00:00Z");

        AppUser active = AppUser.pending("state-active@example.test", "Active", GlobalRole.INTERN, null, now);
        active.activate(encoded, now);
        AppUser locked = AppUser.pending("state-locked@example.test", "Locked", GlobalRole.INTERN, null, now);
        locked.activate(encoded, now);
        locked.lock(now.plusSeconds(1));
        AppUser deactivated = AppUser.pending(
                "state-deactivated@example.test", "Deactivated", GlobalRole.INTERN, null, now);
        deactivated.activate(encoded, now);
        deactivated.deactivate(now.plusSeconds(1));
        AppUser pending = AppUser.pending("state-pending@example.test", "Pending", GlobalRole.INTERN, null, now);
        users.saveAllAndFlush(List.of(active, locked, deactivated, pending));

        String[][] refused = {
                {"state-locked@example.test", password},
                {"state-deactivated@example.test", password},
                {"state-pending@example.test", password},
                {"state-active@example.test", "wrong matrix password"}};
        for (String[] attempt : refused) {
            var result = mockMvc.perform(post("/login").with(csrf())
                            .param("username", attempt[0]).param("password", attempt[1]))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(redirectedUrl("/login?error"))
                    .andExpect(unauthenticated())
                    .andReturn();
            HttpSession session = result.getRequest().getSession(false);
            assertThat(session == null || session.getAttribute(
                    HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY) == null)
                    .as("refused sign-in must not persist an authenticated security context for %s", attempt[0])
                    .isTrue();
        }

        mockMvc.perform(post("/login").with(csrf())
                        .param("username", "state-active@example.test").param("password", password))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/"))
                .andExpect(authenticated().withUsername("state-active@example.test"));
    }
}
