package com.tradingjournal.api.config;

import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Sin este bean, tener opentelemetry-exporter-logging en el classpath no hace
// nada: Spring Boot solo conecta al SdkTracerProvider los SpanExporter que
// sean beans explícitos. Se apaga solo si se configura un endpoint OTLP real
// (management.opentelemetry.tracing.export.otlp.endpoint — la ruta nueva de
// Spring Boot 4.1, la vieja management.otlp.tracing.* quedó deprecada), para
// no exportar las trazas dos veces una vez conectado a Grafana Cloud.
@Configuration
@ConditionalOnProperty(name = "management.opentelemetry.tracing.export.otlp.endpoint", havingValue = "", matchIfMissing = true)
public class TracingConfig {

    @Bean
    public SpanExporter loggingSpanExporter() {
        return LoggingSpanExporter.create();
    }
}
