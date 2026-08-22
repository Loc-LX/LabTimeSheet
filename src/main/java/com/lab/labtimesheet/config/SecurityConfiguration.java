package com.lab.labtimesheet.config;

import com.lab.labtimesheet.feature.account.controller.BootstrapAccessFilter;
import com.lab.labtimesheet.feature.account.controller.LoginThrottleFilter;
import com.lab.labtimesheet.feature.account.service.BootstrapService;
import com.lab.labtimesheet.feature.account.service.LoginThrottle;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.NoOpPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.crypto.password.Pbkdf2PasswordEncoder;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

/**
 * Defines form authentication, role-based Admin routes, public health probes, CSRF protection, and response
 * security headers. Bootstrap access is further constrained by {@link BootstrapAccessFilter} until initialization
 * completes.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {
    /**
     * Uses PBKDF2 for newly stored credentials so the full 12–128 character contract is hashed without BCrypt's
     * 72-byte input ceiling, while retaining explicit prefixes for legacy BCrypt and test-only no-op records.
     *
     * @return delegating encoder whose default write algorithm is PBKDF2
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        Map<String, PasswordEncoder> encoders = new LinkedHashMap<>();
        encoders.put("pbkdf2", Pbkdf2PasswordEncoder.defaultsForSpringSecurity_v5_8());
        encoders.put("bcrypt", new BCryptPasswordEncoder());
        encoders.put("noop", NoOpPasswordEncoder.getInstance());
        return new DelegatingPasswordEncoder("pbkdf2", encoders);
    }

    @Bean
    SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    HttpSessionEventPublisher httpSessionEventPublisher() {
        return new HttpSessionEventPublisher();
    }

    @Bean
    BootstrapAccessFilter bootstrapAccessFilter(BootstrapService bootstrap) {
        return new BootstrapAccessFilter(bootstrap);
    }

    @Bean
    LoginThrottleFilter loginThrottleFilter(LoginThrottle throttle) {
        return new LoginThrottleFilter(throttle);
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            BootstrapAccessFilter bootstrapAccessFilter,
            LoginThrottleFilter loginThrottleFilter,
            LoginThrottle throttle,
            SessionRegistry sessionRegistry)
            throws Exception {
        var success = new SavedRequestAwareAuthenticationSuccessHandler();
        success.setDefaultTargetUrl("/");
        success.setAlwaysUseDefaultTargetUrl(false);
        var failure = new SimpleUrlAuthenticationFailureHandler("/login?error");
        return http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/bootstrap/**", "/activate/**", "/login", "/forgot-password", "/reset-password",
                                "/error", "/assets/**",
                                "/actuator/health", "/actuator/health/**")
                        .permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .headers(headers -> headers.referrerPolicy(policy -> policy.policy(ReferrerPolicy.NO_REFERRER)))
                .formLogin(form -> form.loginPage("/login")
                        .successHandler((request, response, authentication) -> {
                            throttle.clear(request.getParameter("username"), request.getRemoteAddr());
                            success.onAuthenticationSuccess(request, response, authentication);
                        })
                        .failureHandler((request, response, exception) -> {
                            throttle.recordFailure(request.getParameter("username"), request.getRemoteAddr());
                            failure.onAuthenticationFailure(request, response, exception);
                        }))
                .sessionManagement(session -> {
                    session.maximumSessions(Integer.MAX_VALUE)
                            .sessionRegistry(sessionRegistry)
                            .expiredUrl("/login");
                })
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
                .addFilterBefore(bootstrapAccessFilter, AuthorizationFilter.class)
                .addFilterBefore(loginThrottleFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }
}
