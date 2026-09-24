package com.lab.labtimesheet.feature.identity.controller;

import java.io.IOException;

import com.lab.labtimesheet.feature.identity.service.BootstrapService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Hides all non-bootstrap application routes until durable first-Admin initialization completes.
 * Only bootstrap pages, health, public assets, and error rendering remain reachable beforehand.
 */
@RequiredArgsConstructor
public class BootstrapAccessFilter extends OncePerRequestFilter {
    private final BootstrapService bootstrap;

    /**
     * Redirects the installation root to bootstrap and returns HTTP 404 for every other hidden route.
     *
     * @param request current HTTP request
     * @param response current HTTP response
     * @param chain remaining filter chain
     * @throws ServletException when downstream servlet processing fails
     * @throws IOException when response or downstream I/O fails
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!bootstrap.isInitialized() && path.equals(request.getContextPath() + "/")) {
            response.sendRedirect(request.getContextPath() + "/bootstrap");
            return;
        }
        if (!bootstrap.isInitialized() && !allowedBeforeBootstrap(path)) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean allowedBeforeBootstrap(String path) {
        return path.equals("/bootstrap") || path.startsWith("/bootstrap/")
                || path.equals("/actuator/health") || path.startsWith("/actuator/health/")
                || path.startsWith("/assets/")
                || path.equals("/error");
    }
}
