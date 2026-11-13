package com.tradingjournal.api.service;

import com.tradingjournal.api.config.RequestLoggingFilter;
import com.tradingjournal.api.dto.companion.CommerceDto;
import com.tradingjournal.api.dto.companion.PartidoDto;
import com.tradingjournal.api.dto.companion.PrediccionDto;
import com.tradingjournal.api.dto.companion.ProductDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;

// Llama en vivo a las APIs de Mari y Pipe para traer sus entidades — nunca
// datos quemados ni copia de su base de datos, tal como pide la rúbrica.
// Trae una entidad ALEATORIA de la lista de cada compañero (no un id fijo
// pedido por quien llama) — así lo aclaró el profesor: "GET RANDOM". Cada
// nube compañera puede estar caída, lenta, o sin datos todavía sin que eso
// tumbe nuestra propia respuesta: cualquier fallo o lista vacía se atrapa y
// se devuelve null para ese campo en particular.
@Service
public class CompanionClient {

    private static final Logger log = LoggerFactory.getLogger(CompanionClient.class);

    private final RestClient mariClient;
    private final RestClient pipeClient;
    private final long faultLatencyMs;
    private final String faultTarget;

    // Los tiempos de espera salen de la configuración, y por defecto valen lo de
    // siempre (5 s para conectar y 10 s para leer). Así la ronda 0 de un
    // experimento de caos mide el comportamiento actual y la mejora se activa
    // cambiando una variable, sin tocar el código.
    //
    // La latencia saliente es un interruptor de fallo: apagado por defecto
    // (OUTBOUND_FAULT_LATENCY_MS=0). Añade esa espera antes de la llamada hacia
    // el compañero indicado (mari, pipe o all) para simular una dependencia lenta
    // en otra nube.
    public CompanionClient(
            @Value("${companions.mari.base-url}") String mariBaseUrl,
            @Value("${companions.pipe.base-url}") String pipeBaseUrl,
            @Value("${companions.connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${companions.read-timeout-ms:10000}") long readTimeoutMs,
            @Value("${companions.fault.latency-ms:0}") long faultLatencyMs,
            @Value("${companions.fault.target:all}") String faultTarget) {
        this.faultLatencyMs = faultLatencyMs;
        this.faultTarget = faultTarget;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofMillis(connectTimeoutMs));
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        this.mariClient = RestClient.builder().baseUrl(mariBaseUrl).requestFactory(requestFactory).build();
        this.pipeClient = RestClient.builder().baseUrl(pipeBaseUrl).requestFactory(requestFactory).build();
    }

    public PartidoDto getPartido() {
        return safeGetRandom(mariClient, "mari", "/api/v1/partidos", PartidoDto[].class);
    }

    public PrediccionDto getPrediccion() {
        return safeGetRandom(mariClient, "mari", "/api/v1/predicciones", PrediccionDto[].class);
    }

    public CommerceDto getCommerce() {
        return safeGetRandom(pipeClient, "pipe", "/api/v1/commerce", CommerceDto[].class);
    }

    public ProductDto getProduct() {
        return safeGetRandom(pipeClient, "pipe", "/api/v1/products", ProductDto[].class);
    }

    private void injectLatency(String target) {
        if (faultLatencyMs <= 0 || !("all".equalsIgnoreCase(faultTarget) || target.equalsIgnoreCase(faultTarget))) {
            return;
        }
        try {
            Thread.sleep(faultLatencyMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private <T> T safeGetRandom(RestClient client, String target, String uri, Class<T[]> arrayType) {
        try {
            injectLatency(target);
            String traceId = MDC.get(RequestLoggingFilter.TRACE_ID_MDC_KEY);
            T[] items = client.get().uri(uri)
                    .header(RequestLoggingFilter.TRACE_ID_HEADER, traceId)
                    .retrieve().body(arrayType);
            if (items == null || items.length == 0) {
                return null;
            }
            return items[ThreadLocalRandom.current().nextInt(items.length)];
        } catch (Exception e) {
            log.warn("No se pudo obtener {} desde la API compañera: {}", uri, e.getMessage());
            return null;
        }
    }
}
