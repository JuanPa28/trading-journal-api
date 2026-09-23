package com.tradingjournal.api.config;

import io.opentelemetry.exporter.logging.LoggingSpanExporter;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

// Sin un bean SpanExporter, tener opentelemetry-exporter-logging en el
// classpath no hace nada: Spring Boot solo conecta al SdkTracerProvider los
// SpanExporter que sean beans explícitos.
//
// Por qué esto es imperativo (un if en el método @Bean) y no una anotación
// @Conditional — dos intentos con anotaciones fallaron, ambos confirmados
// con --debug real, antes de llegar a esta versión:
// 1. @ConditionalOnProperty(havingValue = "") — con un endpoint real
//    configurado, Spring lo trata igual que "sin havingValue" (matchea con
//    cualquier valor no-"false", no un string vacío exacto): terminaban
//    activos los dos exportadores (el real de Grafana Y este) a la vez.
// 2. @ConditionalOnMissingBean(OtlpTracingConnectionDetails.class) — las
//    @Configuration del usuario (esta clase) se evalúan ANTES que las
//    auto-configuraciones de Spring Boot, así que el bean real de Spring
//    todavía no existía en el momento de chequear: la condición veía
//    "missing" siempre, sin importar si el endpoint estaba puesto o no.
// Leer el valor real del Environment dentro del método @Bean evita los dos
// problemas — siempre ve el valor final ya resuelto, sin depender del orden
// de auto-configuración ni de cómo Spring interprete havingValue.
@Configuration
public class TracingConfig {

    @Bean
    public SpanExporter loggingSpanExporter(
            @Value("${management.opentelemetry.tracing.export.otlp.endpoint:}") String otlpEndpoint) {
        if (!otlpEndpoint.isBlank()) {
            // Ya hay un endpoint real de Grafana configurado — Spring arma su
            // propio SpanExporter OTLP aparte; este queda como no-op para no
            // exportar las trazas dos veces.
            return SpanExporter.composite(List.of());
        }
        return LoggingSpanExporter.create();
    }
}
