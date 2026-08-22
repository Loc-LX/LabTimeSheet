package com.lab.labtimesheet.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Proves arbitrary clients cannot make forwarded headers define request origin or source address. */
class TrustedForwardedHeaderFilterTest {

    @Test
    void untrustedSocketWithForwardedHeadersIsRejectedBeforeHeaderAdaptation() throws Exception {
        TrustedForwardedHeaderFilter filter = new TrustedForwardedHeaderFilter(
                TrustedProxyMatcher.parse("10.20.0.0/24"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login");
        request.setRemoteAddr("192.0.2.10");
        request.addHeader("X-Forwarded-Proto", "https");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Boolean> called = new AtomicReference<>(false);

        FilterChain chain = (request1, response1) -> called.set(true);
        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(called).hasValue(false);
    }

    @Test
    void configuredProxyMayAdaptForwardedSchemeAndClientAddress() throws Exception {
        TrustedForwardedHeaderFilter filter = new TrustedForwardedHeaderFilter(
                TrustedProxyMatcher.parse("10.20.0.0/24"));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login");
        request.setRemoteAddr("10.20.0.44");
        request.addHeader("X-Forwarded-Proto", "https");
        request.addHeader("X-Forwarded-Host", "timesheet.example.edu");
        request.addHeader("X-Forwarded-For", "198.51.100.17");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<jakarta.servlet.http.HttpServletRequest> seen = new AtomicReference<>();

        FilterChain chain = (request1, response1) ->
                seen.set((jakarta.servlet.http.HttpServletRequest) request1);
        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(seen.get().getScheme()).isEqualTo("https");
        assertThat(seen.get().getServerName()).isEqualTo("timesheet.example.edu");
        assertThat(seen.get().getRemoteAddr()).isEqualTo("198.51.100.17");
    }
}
