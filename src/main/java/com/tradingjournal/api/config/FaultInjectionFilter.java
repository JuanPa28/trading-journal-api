package com.tradingjournal.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Random;

// Inyección controlada de fallos para simular una regresión en producción.
// Solo actúa en el track canary: aunque las variables estén puestas, la versión
// estable NUNCA falla. Se activa por variable de entorno al desplegar una
// revisión (FAULT_ERROR_RATE, FAULT_LATENCY_MS) o, si se habilita
// FAULT_HEADER_ENABLED, por petición con la cabecera X-Inject-Fault: error|latency.
// /actuator queda fuera para que las pruebas de salud no se vean afectadas.
@Component
public class FaultInjectionFilter extends OncePerRequestFilter {

    public static final String FAULT_HEADER = "X-Inject-Fault";
    private static final long HEADER_LATENCY_MS = 1500;

    private final boolean canary;
    private final double errorRate;
    private final long latencyMs;
    private final boolean headerEnabled;
    private final Random random;

    @Autowired
    public FaultInjectionFilter(@Value("${app.track:stable}") String track,
                                @Value("${fault.error-rate:0}") double errorRate,
                                @Value("${fault.latency-ms:0}") long latencyMs,
                                @Value("${fault.header-enabled:false}") boolean headerEnabled) {
        this(track, errorRate, latencyMs, headerEnabled, new Random());
    }

    FaultInjectionFilter(String track, double errorRate, long latencyMs, boolean headerEnabled, Random random) {
        this.canary = "canary".equalsIgnoreCase(track);
        this.errorRate = errorRate;
        this.latencyMs = latencyMs;
        this.headerEnabled = headerEnabled;
        this.random = random;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !canary || request.getRequestURI().startsWith("/actuator");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String requested = headerEnabled ? request.getHeader(FAULT_HEADER) : null;

        long delay = latencyMs;
        if ("latency".equalsIgnoreCase(requested)) {
            delay = Math.max(delay, HEADER_LATENCY_MS);
        }
        if (delay > 0) {
            try {
                Thread.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        boolean fail = "error".equalsIgnoreCase(requested) || (errorRate > 0 && random.nextDouble() < errorRate);
        if (fail) {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("application/json");
            response.getWriter().write("{\"error\":\"fault injected on canary\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }
}
