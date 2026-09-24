package com.lab.labtimesheet.feature.identity.controller;

import java.io.IOException;

import com.lab.labtimesheet.feature.identity.service.LoginThrottle;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Rejects a blocked form-login attempt before credential lookup while preserving the generic login failure route.
 * Source address comes from the server request; forwarded-header trust remains controlled by the active profile.
 */
@RequiredArgsConstructor
public class LoginThrottleFilter extends OncePerRequestFilter {
    private final LoginThrottle throttle;

    /**
     * Applies the throttle only to POST form-login requests.
     *
     * @param request current HTTP request
     * @param response current HTTP response
     * @param filterChain remaining security filters
     * @throws ServletException when the downstream filter fails
     * @throws IOException when the response redirect cannot be written
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if ("POST".equalsIgnoreCase(request.getMethod())
                && "/login".equals(request.getServletPath())
                && throttle.isBlocked(request.getParameter("username"), request.getRemoteAddr())) {
            response.sendRedirect(request.getContextPath() + "/login?error");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
