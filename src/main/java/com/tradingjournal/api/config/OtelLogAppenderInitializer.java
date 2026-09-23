package com.tradingjournal.api.config;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

// logback-spring.xml declara el appender OTEL, pero Logback arranca antes de
// que exista el contexto de Spring (y por lo tanto antes de que exista el
// bean OpenTelemetry) — no hay forma de inyectárselo en el propio XML. El
// appender empieza sin exportar nada hasta que se le instala el SDK real acá,
// una vez el contexto ya está completamente arriba.
@Component
public class OtelLogAppenderInitializer {

    private final OpenTelemetry openTelemetry;

    public OtelLogAppenderInitializer(OpenTelemetry openTelemetry) {
        this.openTelemetry = openTelemetry;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void install() {
        OpenTelemetryAppender.install(openTelemetry);
    }
}
