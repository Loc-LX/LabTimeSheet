package com.lab.labtimesheet.config;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.filter.ForwardedHeaderFilter;

/**
 * Applies Spring's forwarded-header adaptation only after the raw socket peer passes the configured production proxy
 * allow-list. Requests with forwarded headers from an untrusted peer receive a generic bad-request response.
 */
final class TrustedForwardedHeaderFilter extends ForwardedHeaderFilter {
    private final TrustedProxyMatcher proxies;

    TrustedForwardedHeaderFilter(TrustedProxyMatcher proxies) {
        this.proxies = proxies;
    }

    /**
     * Rejects spoofable forwarded headers before delegating scheme/host/client-address adaptation to Spring.
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
        if (!proxies.matches(request.getRemoteAddr())) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }
        super.doFilterInternal(request, response, filterChain);
    }
}
