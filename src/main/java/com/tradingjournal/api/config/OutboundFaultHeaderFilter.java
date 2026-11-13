package com.tradingjournal.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;

// Latencia saliente por PETICIÓN, activable con una cabecera. Es el interruptor de
// fallo que usa el chaos gate: como solo afecta a la petición que lleva la cabecera,
// el radio de impacto es el del tráfico de la prueba y no hace falta tocar la
// infraestructura (ni tener credenciales de la nube) para activarla o revertirla.
//
//   X-Inject-Outbound-Latency-Ms: 3000      espera añadida antes de cada llamada a un compañero
//   X-Inject-Outbound-Target: mari | pipe   a cuál (por defecto, a todos)
//
// Solo actúa si FAULT_HEADER_ENABLED=true (apagado por defecto) y el valor se limita
// a 10 s. Deja los valores en el MDC para que CompanionClient los aplique.
@Component
public class OutboundFaultHeaderFilter extends OncePerRequestFilter {

    public static final String LATENCY_HEADER = "X-Inject-Outbound-Latency-Ms";
    public static final String TARGET_HEADER = "X-Inject-Outbound-Target";
    public static final String LATENCY_MDC_KEY = "outboundFaultLatencyMs";
    public static final String TARGET_MDC_KEY = "outboundFaultTarget";
    public static final long MAX_LATENCY_MS = 10_000;

    private static final Logger log = LoggerFactory.getLogger(OutboundFaultHeaderFilter.class);

    private final boolean enabled;

    public OutboundFaultHeaderFilter(@Value("${fault.header-enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !enabled || request.getHeader(LATENCY_HEADER) == null;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        long latency = parse(request.getHeader(LATENCY_HEADER));
        if (latency <= 0) {
            filterChain.doFilter(request, response);
            return;
        }
        String target = request.getHeader(TARGET_HEADER);
        MDC.put(LATENCY_MDC_KEY, Long.toString(latency));
        MDC.put(TARGET_MDC_KEY, target == null || target.isBlank() ? "all" : target.trim().toLowerCase());
        log.info("event=outbound_fault_header latencyMs={} target={}", latency, MDC.get(TARGET_MDC_KEY));
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(LATENCY_MDC_KEY);
            MDC.remove(TARGET_MDC_KEY);
        }
    }

    static long parse(String value) {
        if (value == null) {
            return 0;
        }
        try {
            return Math.min(Math.max(Long.parseLong(value.trim()), 0), MAX_LATENCY_MS);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
