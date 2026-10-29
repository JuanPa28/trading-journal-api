package com.tradingjournal.api.config;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.Random;

import static org.assertj.core.api.Assertions.assertThat;

class FaultInjectionFilterTest {

    private MockHttpServletResponse call(FaultInjectionFilter filter, MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, new MockFilterChain());
        return response;
    }

    @Test
    void stableNeverInjectsFaultsEvenWhenConfigured() throws Exception {
        FaultInjectionFilter filter = new FaultInjectionFilter("stable", 1.0, 0, true, new Random());
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/trades");
        request.addHeader(FaultInjectionFilter.FAULT_HEADER, "error");

        assertThat(call(filter, request).getStatus()).isEqualTo(200);
    }

    @Test
    void canaryWithFullErrorRateAlwaysReturns500() throws Exception {
        FaultInjectionFilter filter = new FaultInjectionFilter("canary", 1.0, 0, false, new Random());

        MockHttpServletResponse response = call(filter, new MockHttpServletRequest("GET", "/api/trades"));

        assertThat(response.getStatus()).isEqualTo(500);
        assertThat(response.getContentAsString()).contains("fault injected");
    }

    @Test
    void canaryWithoutConfigurationBehavesNormally() throws Exception {
        FaultInjectionFilter filter = new FaultInjectionFilter("canary", 0, 0, false, new Random());

        assertThat(call(filter, new MockHttpServletRequest("GET", "/api/trades")).getStatus()).isEqualTo(200);
    }

    @Test
    void headerForcesAnErrorOnlyWhenEnabled() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/trades");
        request.addHeader(FaultInjectionFilter.FAULT_HEADER, "error");

        assertThat(call(new FaultInjectionFilter("canary", 0, 0, true, new Random()), request).getStatus()).isEqualTo(500);
        assertThat(call(new FaultInjectionFilter("canary", 0, 0, false, new Random()), request).getStatus()).isEqualTo(200);
    }

    @Test
    void healthChecksAreNeverAffected() throws Exception {
        FaultInjectionFilter filter = new FaultInjectionFilter("canary", 1.0, 0, false, new Random());

        assertThat(call(filter, new MockHttpServletRequest("GET", "/actuator/health")).getStatus()).isEqualTo(200);
    }

    @Test
    void addsLatencyWhenConfigured() throws Exception {
        FaultInjectionFilter filter = new FaultInjectionFilter("canary", 0, 120, false, new Random());
        long start = System.nanoTime();

        call(filter, new MockHttpServletRequest("GET", "/api/trades"));

        assertThat((System.nanoTime() - start) / 1_000_000).isGreaterThanOrEqualTo(100);
    }
}
