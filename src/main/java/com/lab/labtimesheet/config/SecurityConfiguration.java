package com.lab.labtimesheet.config;

import com.lab.labtimesheet.feature.account.controller.BootstrapAccessFilter;
import com.lab.labtimesheet.feature.account.service.BootstrapService;
import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;
import org.springframework.security.web.session.HttpSessionEventPublisher;

/**
 * Defines form authentication, role-based Admin routes, public health probes, CSRF protection, and response
 * security headers. Bootstrap access is further constrained by {@link BootstrapAccessFilter} until initialization
 * completes.
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    @Bean
    BootstrapAccessFilter bootstrapAccessFilter(BootstrapService bootstrap) {
        return new BootstrapAccessFilter(bootstrap);
    }

    @Bean
    SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    @Bean
    ServletListenerRegistrationBean<HttpSessionEventPublisher> httpSessionEventPublisher() {
        return new ServletListenerRegistrationBean<>(new HttpSessionEventPublisher());
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, BootstrapAccessFilter bootstrapAccessFilter,
            SessionRegistry sessionRegistry) throws Exception {
        return http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/bootstrap/**", "/activate/**", "/login", "/error", "/assets/**",
                                "/actuator/health", "/actuator/health/**")
                        .permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .headers(headers -> headers.referrerPolicy(policy -> policy.policy(ReferrerPolicy.NO_REFERRER)))
                .formLogin(form -> form.loginPage("/login").defaultSuccessUrl("/", false))
                .logout(logout -> logout.logoutSuccessUrl("/login?logout"))
                .sessionManagement(session -> session.maximumSessions(-1).sessionRegistry(sessionRegistry))
                .addFilterBefore(bootstrapAccessFilter, AuthorizationFilter.class)
                .build();
    }
}
