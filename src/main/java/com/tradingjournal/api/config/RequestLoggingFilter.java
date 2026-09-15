package com.tradingjournal.api.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

// El identificador de correlación (trace-id) viaja en el header X-Trace-Id.
// Si el orquestador ya lo mandó, lo reutilizamos; si nos llaman directo (sin
// pasar por el orquestador), se genera uno nuevo acá. Queda en el MDC para
// que aparezca en cada línea de log de la petición (ver logback pattern en
// application.yml), y se devuelve en la respuesta para que quien llamó sepa
// cuál se usó. CompanionClient lo reutiliza para propagarlo hacia Mari y Pipe.
@Component
public class RequestLoggingFilter extends OncePerRequestFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String TRACE_ID_MDC_KEY = "traceId";

    private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String traceId = request.getHeader(TRACE_ID_HEADER);
        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID().toString();
        }
        MDC.put(TRACE_ID_MDC_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        try {
            log.info("--> {} {}", request.getMethod(), request.getRequestURI());
            filterChain.doFilter(request, response);
            log.info("<-- {} {} status={}", request.getMethod(), request.getRequestURI(), response.getStatus());
        } finally {
            MDC.remove(TRACE_ID_MDC_KEY);
        }
    }
}
