package com.tradingjournal.api.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

// Métrica de negocio del dominio: operaciones (trades) registradas con éxito.
// Se cuenta aquí, a la salida de la petición, y no en TradeService, para no
// acoplar la lógica de negocio a Micrometer. Los tags version y track los pone
// el registro de métricas (management.metrics.tags). Además deja una línea de
// log con un formato fijo, de la que se puede derivar una métrica basada en logs.
@Component
public class BusinessMetricsFilter extends OncePerRequestFilter {

    public static final String TRADES_CREATED = "trades.created";

    private static final Pattern CREATE_TRADE = Pattern.compile("^/api/(v[0-9]+/)?trades/?$");
    private static final Logger log = LoggerFactory.getLogger(BusinessMetricsFilter.class);

    private final ObjectProvider<MeterRegistry> registry;

    public BusinessMetricsFilter(ObjectProvider<MeterRegistry> registry) {
        this.registry = registry;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        filterChain.doFilter(request, response);
        if ("POST".equals(request.getMethod())
                && response.getStatus() == HttpServletResponse.SC_CREATED
                && CREATE_TRADE.matcher(request.getRequestURI()).matches()) {
            MeterRegistry meters = registry.getIfAvailable();
            if (meters != null) {
                Counter.builder(TRADES_CREATED).description("Trades creados con éxito").register(meters).increment();
            }
            log.info("event=trade_created path={}", request.getRequestURI());
        }
    }
}
