package com.tradingjournal.api.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BusinessMetricsFilterTest {

    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();

    @SuppressWarnings("unchecked")
    private BusinessMetricsFilter filter() {
        ObjectProvider<io.micrometer.core.instrument.MeterRegistry> provider = mock(ObjectProvider.class);
        when(provider.getIfAvailable()).thenReturn(meters);
        return new BusinessMetricsFilter(provider);
    }

    private double run(String method, String uri, int status) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = (req, res) -> ((MockHttpServletResponse) res).setStatus(status);
        filter().doFilter(new MockHttpServletRequest(method, uri), response, chain);
        var counter = meters.find(BusinessMetricsFilter.TRADES_CREATED).counter();
        return counter == null ? 0 : counter.count();
    }

    @Test
    void countsASuccessfulTradeCreation() throws Exception {
        assertThat(run("POST", "/api/trades", 201)).isEqualTo(1.0);
    }

    @Test
    void countsTheVersionedRoutesToo() throws Exception {
        assertThat(run("POST", "/api/v3/trades", 201)).isEqualTo(1.0);
    }

    @Test
    void ignoresFailuresAndOtherRequests() throws Exception {
        assertThat(run("POST", "/api/trades", 400)).isZero();
        assertThat(run("GET", "/api/trades", 200)).isZero();
        assertThat(run("POST", "/api/traders", 201)).isZero();
    }
}
