package com.lab.labtimesheet.config;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.OncePerRequestFilter;

/** Enforces the configured public origin on production state-changing requests before CSRF evaluation. */
final class OriginEnforcementFilter extends OncePerRequestFilter {
    private static final Set<String> STATE_CHANGING_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");
    private final String publicOrigin;

    OriginEnforcementFilter(String publicOrigin) {
        this.publicOrigin = ProductionReadiness.canonicalOrigin(publicOrigin);
    }

    /**
     * Rejects a present Origin or Referer origin that is not the configured public origin. Requests without either
     * header continue so Spring Security's CSRF token remains the universal state-changing control.
     *
     * @param request incoming servlet request
     * @param response outgoing servlet response
     * @param filterChain remaining servlet filters
     * @throws IOException when the response or downstream chain fails
     * @throws ServletException when the downstream chain fails
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String suppliedOrigin = request.getHeader("Origin");
        if (suppliedOrigin == null) {
            suppliedOrigin = originFromReferer(request.getHeader("Referer"));
        }
        if (suppliedOrigin != null && !publicOrigin.equals(suppliedOrigin)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        filterChain.doFilter(request, response);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !STATE_CHANGING_METHODS.contains(request.getMethod().toUpperCase(java.util.Locale.ROOT));
    }

    private static String originFromReferer(String referer) {
        if (referer == null || referer.isBlank()) {
            return null;
        }
        try {
            URI uri = new URI(referer);
            return ProductionReadiness.canonicalOrigin(
                    uri.getScheme() + "://" + uri.getRawAuthority());
        } catch (URISyntaxException | IllegalArgumentException failure) {
            return "invalid-origin";
        }
    }
}
