package com.lab.labtimesheet.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicReference;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/** Proves state-changing requests accept only the configured public origin in production. */
class OriginEnforcementFilterTest {

    @Test
    void mismatchedOriginIsRejectedForStateChangingRequests() throws Exception {
        OriginEnforcementFilter filter = new OriginEnforcementFilter("https://timesheet.example.edu");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/accounts");
        request.addHeader("Origin", "https://evil.example");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Boolean> called = new AtomicReference<>(false);

        FilterChain chain = (request1, response1) -> called.set(true);
        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(called).hasValue(false);
    }

    @Test
    void matchingOriginAndRequestsWithoutOriginRemainAvailableToCsrf() throws Exception {
        OriginEnforcementFilter filter = new OriginEnforcementFilter("https://timesheet.example.edu");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/admin/accounts");
        request.addHeader("Origin", "https://timesheet.example.edu");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Integer> calls = new AtomicReference<>(0);

        FilterChain chain = (request1, response1) -> calls.updateAndGet(i -> i + 1);
        filter.doFilter(request, response, chain);
        MockHttpServletRequest legacyRequest = new MockHttpServletRequest("POST", "/admin/accounts");
        filter.doFilter(legacyRequest, new MockHttpServletResponse(), chain);

        assertThat(calls).hasValue(2);
    }
}
