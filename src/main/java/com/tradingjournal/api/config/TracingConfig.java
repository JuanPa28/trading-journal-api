package com.tradingjournal.api.config;

import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// Sin este bean, tener opentelemetry-exporter-logging en el classpath no hace
// nada: Spring Boot solo conecta al SdkTracerProvider los SpanExporter que
// sean beans explícitos. Se apaga solo si se configura un endpoint OTLP
// (management.otlp.tracing.endpoint), para no exportar las trazas dos veces
// una vez el grupo decida la herramienta SAAS transversal.
@Configuration
@ConditionalOnProperty(name = "management.otlp.tracing.endpoint", havingValue = "", matchIfMissing = true)
public class TracingConfig {

    @Bean
    public SpanExporter loggingSpanExporter() {
        return LoggingSpanExporter.create();
    }
}
