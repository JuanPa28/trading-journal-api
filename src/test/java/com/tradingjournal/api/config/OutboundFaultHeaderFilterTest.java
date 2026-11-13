package com.tradingjournal.api.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class OutboundFaultHeaderFilterTest {

    @AfterEach
    void clean() {
        MDC.clear();
    }

    // Ejecuta el filtro y devuelve lo que vio la cadena (el MDC en pleno procesamiento).
    private String[] run(boolean enabled, String latency, String target) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v2/trades/1");
        if (latency != null) {
            request.addHeader(OutboundFaultHeaderFilter.LATENCY_HEADER, latency);
        }
        if (target != null) {
            request.addHeader(OutboundFaultHeaderFilter.TARGET_HEADER, target);
        }
        AtomicReference<String[]> seen = new AtomicReference<>();
        MockFilterChain chain = new MockFilterChain() {
            @Override
            public void doFilter(jakarta.servlet.ServletRequest req, jakarta.servlet.ServletResponse res) {
                seen.set(new String[]{MDC.get(OutboundFaultHeaderFilter.LATENCY_MDC_KEY),
                        MDC.get(OutboundFaultHeaderFilter.TARGET_MDC_KEY)});
            }
        };
        new OutboundFaultHeaderFilter(enabled).doFilter(request, new MockHttpServletResponse(), chain);
        return seen.get();
    }

    @Test
    void whenDisabledTheHeaderIsIgnored() throws Exception {
        assertThat(run(false, "3000", "mari")).containsExactly(null, null);
    }

    @Test
    void whenEnabledTheLatencyAndTargetReachTheProcessing() throws Exception {
        assertThat(run(true, "3000", "Mari")).containsExactly("3000", "mari");
    }

    @Test
    void targetDefaultsToAll() throws Exception {
        assertThat(run(true, "500", null)).containsExactly("500", "all");
    }

    @Test
    void theLatencyIsCappedAtTenSeconds() throws Exception {
        assertThat(run(true, "999999", null)[0]).isEqualTo("10000");
    }

    @Test
    void invalidOrZeroValuesAreIgnored() throws Exception {
        assertThat(run(true, "abc", null)).containsExactly(null, null);
        assertThat(run(true, "0", null)).containsExactly(null, null);
        assertThat(run(true, "-5", null)).containsExactly(null, null);
    }

    @Test
    void theMdcIsCleanedWhenTheRequestEnds() throws Exception {
        run(true, "3000", "pipe");

        assertThat(MDC.get(OutboundFaultHeaderFilter.LATENCY_MDC_KEY)).isNull();
        assertThat(MDC.get(OutboundFaultHeaderFilter.TARGET_MDC_KEY)).isNull();
    }
}
