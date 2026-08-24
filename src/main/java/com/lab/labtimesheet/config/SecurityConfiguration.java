package com.lab.labtimesheet.config;

import com.lab.labtimesheet.feature.account.controller.BootstrapAccessFilter;
import com.lab.labtimesheet.feature.account.controller.LoginThrottleFilter;
import com.lab.labtimesheet.feature.account.service.BootstrapService;
import com.lab.labtimesheet.feature.account.service.LoginThrottle;
import java.util.LinkedHashMap;
import java.util.Map;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;
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

    /** Registers the production-only forwarded-header filter ahead of Spring's header adaptation. */
    @Bean
    @Profile("prod")
    FilterRegistrationBean<TrustedForwardedHeaderFilter> trustedForwardedHeaderFilter(SecurityProperties security) {
        FilterRegistrationBean<TrustedForwardedHeaderFilter> registration = new FilterRegistrationBean<>(
                new TrustedForwardedHeaderFilter(TrustedProxyMatcher.parse(security.getTrustedProxyCidrs())));
        registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    /** Registers strict configured-origin checks only for production state-changing requests. */
    @Bean
    @Profile("prod")
    FilterRegistrationBean<OriginEnforcementFilter> originEnforcementFilter(
            @Value("${lab.public-origin}") String publicOrigin) {
        FilterRegistrationBean<OriginEnforcementFilter> registration = new FilterRegistrationBean<>(
                new OriginEnforcementFilter(publicOrigin));
        registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }

    @Bean
    SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            BootstrapAccessFilter bootstrapAccessFilter,
            LoginThrottleFilter loginThrottleFilter,
            LoginThrottle throttle,
            SessionRegistry sessionRegistry,
            Environment environment)
            throws Exception {
        // Mỗi request đi qua SecurityFilterChain trước DispatcherServlet. Vì /projects không nằm trong permitAll
        // hay /admin/**, nó rơi vào anyRequest().authenticated(): chưa login thì dừng ở security/redirect login,
        // đã login thì SecurityContext chứa Authentication để Controller nhận Principal.
        // CSRF protection của Spring Security vẫn bật mặc định vì chain không gọi csrf().disable(). Do đó POST
        // /projects phải mang CSRF token hợp lệ; request thiếu/sai token bị filter từ chối trước khi ProjectController
        // được gọi, nên không có ProjectCreateForm, transaction hay INSERT nào được tạo.
        var success = new SavedRequestAwareAuthenticationSuccessHandler();
        success.setDefaultTargetUrl("/");
        success.setAlwaysUseDefaultTargetUrl(false);
        var failure = new SimpleUrlAuthenticationFailureHandler("/login?error");
        boolean production = environment.acceptsProfiles(Profiles.of("prod"));
        return http
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers(
                                "/bootstrap/**", "/activate/**", "/login", "/forgot-password", "/reset-password",
                                "/error", "/assets/**",
                                "/actuator/health", "/actuator/health/**")
                        .permitAll()
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .headers(headers -> {
                    headers.referrerPolicy(policy -> policy.policy(ReferrerPolicy.NO_REFERRER));
                    if (production) {
                        headers.httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .preload(true)
                                .maxAgeInSeconds(31_536_000));
                        headers.contentSecurityPolicy(policy -> policy.policyDirectives(
                                "default-src 'self'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'"));
                    }
                })
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
